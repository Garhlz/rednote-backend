package com.szu.afternoon3.platform;

import cn.hutool.crypto.digest.BCrypt;
import com.szu.afternoon3.platform.common.UserContext;
import com.szu.afternoon3.platform.dto.*;
import com.szu.afternoon3.platform.entity.User;
import com.szu.afternoon3.platform.mapper.UserMapper;
import com.szu.afternoon3.platform.service.UserService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;

@SpringBootTest
public class UserFlowTest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserService userService;

    @Test
    public void testUserFlow() {
        System.out.println("========== 开始 UserFlow 端到端测试 ==========");

        // 1. 创建初始用户 (模拟微信注册)
        String suffix = String.valueOf(System.currentTimeMillis());
        User user = new User();
        user.setNickname("TestUser_" + suffix);
        user.setOpenid("openid_" + suffix);
        user.setRole("USER");
        user.setStatus(1);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        user.setIsDeleted(0);
        userMapper.insert(user);
        
        Long userId = user.getId();
        System.out.println("1. 创建测试用户 ID: " + userId);

        // 模拟登录状态
        UserContext.setUserId(userId);

        try {
            // 2. 测试绑定邮箱
            System.out.println("2. 测试绑定邮箱...");
            String email = "test_" + suffix + "@szu.edu.cn";
            UserBindEmailDTO bindEmailDTO = new UserBindEmailDTO();
            bindEmailDTO.setEmail(email);
            bindEmailDTO.setCode("123456"); // 使用 Mock 的验证码
            
            userService.bindEmail(bindEmailDTO);
            
            // 验证数据库
            User updatedUser = userMapper.selectById(userId);
            Assertions.assertEquals(email, updatedUser.getEmail());
            System.out.println("   邮箱绑定成功: " + updatedUser.getEmail());

            // 3. 测试首次设置密码 (验证邮箱模式)
            System.out.println("3. 测试首次设置密码...");
            UserPasswordSetDTO pwdSetDTO = new UserPasswordSetDTO();
            pwdSetDTO.setCode("123456");
            pwdSetDTO.setPassword("NewPassword123");
            
            userService.setPasswordWithCode(pwdSetDTO);
            
            updatedUser = userMapper.selectById(userId);
            Assertions.assertTrue(BCrypt.checkpw("NewPassword123", updatedUser.getPassword()));
            System.out.println("   密码设置成功");

            // 4. 测试修改密码 (验证旧密码)
            System.out.println("4. 测试修改密码...");
            UserPasswordChangeDTO pwdChangeDTO = new UserPasswordChangeDTO();
            pwdChangeDTO.setOldPassword("NewPassword123");
            pwdChangeDTO.setNewPassword("ChangedPassword456");
            
            userService.changePassword(pwdChangeDTO);
            
            updatedUser = userMapper.selectById(userId);
            Assertions.assertTrue(BCrypt.checkpw("ChangedPassword456", updatedUser.getPassword()));
            System.out.println("   密码修改成功");

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
            Assertions.assertEquals("This is a bio", updatedUser.getBio());
            Assertions.assertEquals(2, updatedUser.getGender());
            Assertions.assertEquals("2003-05-20", updatedUser.getBirthday().toString());
            System.out.println("   资料修改成功");
            
        } finally {
            UserContext.clear();
        }
        
        System.out.println("========== 测试全部通过！ ==========");
    }
}
