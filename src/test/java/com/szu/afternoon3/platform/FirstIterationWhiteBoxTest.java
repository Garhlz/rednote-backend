package com.szu.afternoon3.platform;

import cn.hutool.crypto.digest.BCrypt;
import com.szu.afternoon3.platform.common.UserContext;
import com.szu.afternoon3.platform.dto.*;
import com.szu.afternoon3.platform.entity.User;
import com.szu.afternoon3.platform.mapper.UserMapper;
import com.szu.afternoon3.platform.service.AuthService;
import com.szu.afternoon3.platform.service.UserService;
import com.szu.afternoon3.platform.vo.UserProfileVO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;

/**
 * 第一周迭代内容白盒测试
 * 包含：用户注册、绑定邮箱、设置密码、修改资料、查看资料
 */
@SpringBootTest(properties = "management.health.mail.enabled=false")
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class FirstIterationWhiteBoxTest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private AuthService authService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockBean
    private JavaMailSender mailSender;

    @Test
    public void testFirstWeekIteration() {
        System.out.println("==================================================");
        System.out.println("      第一周迭代功能白盒测试 (White-Box Test)      ");
        System.out.println("==================================================");

        // 1. 创建初始用户 (模拟微信登录后的状态)
        System.out.println("\n[Step 1] 创建初始用户 (模拟微信登录)");
        String suffix = String.valueOf(System.currentTimeMillis());
        User user = new User();
        user.setNickname("WhiteBoxUser_" + suffix);
        user.setOpenid("wb_openid_" + suffix);
        user.setRole("USER");
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setIsDeleted(0);
        userMapper.insert(user);
        Long userId = user.getId();
        System.out.println("   -> 用户创建成功, ID: " + userId);

        // 模拟登录上下文
        UserContext.setUserId(userId);

        try {
            // 2. 绑定邮箱
            System.out.println("\n[Step 2] 绑定邮箱流程");
            String email = "wb_test_" + suffix + "@163.com";

            // 清除可能存在的限流
            redisTemplate.delete("verify:limit:" + email);

            // 发送验证码 (Mock)
            authService.sendEmailCode(email);
            System.out.println("   -> 请求发送验证码 (邮件服务已Mock)");

            // 获取 Redis 中的验证码
            String codeKey = "verify:code:" + email;
            String realCode = redisTemplate.opsForValue().get(codeKey);
            Assertions.assertNotNull(realCode, "Redis 中未找到验证码");
            System.out.println("   -> 验证码已存入 Redis: " + realCode);

            // 提交绑定
            UserBindEmailDTO bindEmailDTO = new UserBindEmailDTO();
            bindEmailDTO.setEmail(email);
            bindEmailDTO.setCode(realCode);
            userService.bindEmail(bindEmailDTO);

            User updatedUser = userMapper.selectById(userId);
            Assertions.assertEquals(email, updatedUser.getEmail());
            System.out.println("   -> 邮箱绑定成功: " + updatedUser.getEmail());

            // 3. 设置初始密码
            System.out.println("\n[Step 3] 设置初始密码");
            // 为了设置密码，需要验证码验证身份（这里复用邮箱验证码流程，简化测试，直接往Redis塞一个验证码用于修改密码）
            // 注意：实际业务中设置密码可能需要验证码，或者如果是绑定邮箱后立即设置可能不需要。
            // 假设需要验证码:
            String pwdCode = "666666";
            redisTemplate.opsForValue().set(codeKey, pwdCode); // 复用 Key

            UserPasswordSetDTO pwdSetDTO = new UserPasswordSetDTO();
            pwdSetDTO.setCode(pwdCode);
            pwdSetDTO.setPassword("WhiteBoxPass123");
            userService.setPasswordWithCode(pwdSetDTO);

            updatedUser = userMapper.selectById(userId);
            Assertions.assertTrue(BCrypt.checkpw("WhiteBoxPass123", updatedUser.getPassword()));
            System.out.println("   -> 密码设置成功 (BCrypt加密验证通过)");

            // 4. 修改个人资料
            System.out.println("\n[Step 4] 修改个人资料");
            UserProfileUpdateDTO profileDTO = new UserProfileUpdateDTO();
            profileDTO.setNickname("WB_Updated_Name");
            profileDTO.setBio("White Box Testing is fun");
            profileDTO.setGender(1);
            profileDTO.setBirthday("2024-01-01");
            userService.updateProfile(profileDTO);

            updatedUser = userMapper.selectById(userId);
            Assertions.assertEquals("WB_Updated_Name", updatedUser.getNickname());
            System.out.println("   -> 昵称已更新: " + updatedUser.getNickname());

            // 5. 获取个人资料 (VO)
            System.out.println("\n[Step 5] 获取个人资料视图");
            UserProfileVO profileVO = userService.getUserProfile();
            Assertions.assertEquals(email, profileVO.getEmail());
            Assertions.assertEquals("WB_Updated_Name", profileVO.getNickname());
            System.out.println("   -> 获取资料成功: " + profileVO);

        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
            throw e; // 抛出异常让 JUnit 标记失败
        } finally {
            UserContext.clear();
        }

        System.out.println("\n==================================================");
        System.out.println("            ✅ 白盒测试全部通过                   ");
        System.out.println("==================================================");
    }
}
