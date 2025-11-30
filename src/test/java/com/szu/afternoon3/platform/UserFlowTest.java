package com.szu.afternoon3.platform;

import cn.hutool.crypto.digest.BCrypt;
import com.szu.afternoon3.platform.common.UserContext;
import com.szu.afternoon3.platform.dto.*;
import com.szu.afternoon3.platform.entity.User;
import com.szu.afternoon3.platform.mapper.UserMapper;
import com.szu.afternoon3.platform.service.AuthService;
import com.szu.afternoon3.platform.service.UserService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean; // 1. 导入 MockBean
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender; // 2. 导入 JavaMailSender

import java.time.LocalDateTime;

@SpringBootTest
public class UserFlowTest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private AuthService authService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    // 3. 【核心修改】这里使用 @MockBean 而不是 @Autowired
    // 这意味着在测试中，Spring 会注入一个“假的”发送器，调用 send 方法时什么都不会发生，也不会报错
    @MockBean
    private JavaMailSender mailSender;

    @Test
    public void testUserFlow() {
        System.out.println("========== 开始 UserFlow 端到端测试 (Mock邮件发送) ==========");

        // 1. 创建初始用户
        String suffix = String.valueOf(System.currentTimeMillis());
        User user = new User();
        user.setNickname("TestUser_" + suffix);
        user.setOpenid("openid_" + suffix);
        user.setRole("USER");
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setIsDeleted(0);
        userMapper.insert(user);

        Long userId = user.getId();
        System.out.println("1. 创建测试用户 ID: " + userId);

        UserContext.setUserId(userId);

        try {
            // 2. 测试绑定邮箱
            System.out.println("2. 测试绑定邮箱...");
            // 这里依然用随机生成的邮箱，防止数据库唯一性冲突
            String email = "szu_test_" + suffix + "@163.com";

            // 清除限流
            String limitKey = "verify:limit:" + email;
            redisTemplate.delete(limitKey);

            // 发送验证码
            // 【注意】因为 mailSender 被 Mock 了，这里不会真的发邮件，也不会报错
            // 但是！AuthService 里的逻辑依然会执行：生成验证码 -> 存入 Redis
            authService.sendEmailCode(email);
            System.out.println("   (Mock) 已请求发送验证码...");

            // 从 Redis 中获取验证码
            String codeKey = "verify:code:" + email;
            String realCode = redisTemplate.opsForValue().get(codeKey);

            Assertions.assertNotNull(realCode, "Redis 中未找到验证码");
            System.out.println("   从 Redis 获取到验证码: " + realCode);

            // 执行绑定
            UserBindEmailDTO bindEmailDTO = new UserBindEmailDTO();
            bindEmailDTO.setEmail(email);
            bindEmailDTO.setCode(realCode);

            userService.bindEmail(bindEmailDTO);

            User updatedUser = userMapper.selectById(userId);
            Assertions.assertEquals(email, updatedUser.getEmail());
            System.out.println("   ✅ 邮箱绑定成功: " + updatedUser.getEmail());

            // ... 后续代码不变 ...

            // 3. 测试首次设置密码
            System.out.println("3. 测试首次设置密码...");
            String pwdCode = "888888";
            redisTemplate.opsForValue().set(codeKey, pwdCode);

            UserPasswordSetDTO pwdSetDTO = new UserPasswordSetDTO();
            pwdSetDTO.setCode(pwdCode);
            pwdSetDTO.setPassword("NewPassword123");

            userService.setPasswordWithCode(pwdSetDTO);

            updatedUser = userMapper.selectById(userId);
            Assertions.assertTrue(BCrypt.checkpw("NewPassword123", updatedUser.getPassword()));
            System.out.println("   ✅ 密码设置成功");

            // 4. 测试修改密码
            System.out.println("4. 测试修改密码...");
            UserPasswordChangeDTO pwdChangeDTO = new UserPasswordChangeDTO();
            pwdChangeDTO.setOldPassword("NewPassword123");
            pwdChangeDTO.setNewPassword("ChangedPassword456");

            userService.changePassword(pwdChangeDTO);

            updatedUser = userMapper.selectById(userId);
            Assertions.assertTrue(BCrypt.checkpw("ChangedPassword456", updatedUser.getPassword()));
            System.out.println("   ✅ 密码修改成功");

            // 5. 测试修改个人资料
            System.out.println("5. 测试修改个人资料...");
            UserProfileUpdateDTO profileDTO = new UserProfileUpdateDTO();
            profileDTO.setNickname("UpdatedName");
            profileDTO.setBio("This is a bio");
            profileDTO.setGender(2);
            profileDTO.setBirthday("2003-05-20");

            userService.updateProfile(profileDTO);

            updatedUser = userMapper.selectById(userId);
            Assertions.assertEquals("UpdatedName", updatedUser.getNickname());
            System.out.println("   ✅ 资料修改成功");

        } finally {
            UserContext.clear();
        }

        System.out.println("========== 测试全部通过！ ==========");
    }
}