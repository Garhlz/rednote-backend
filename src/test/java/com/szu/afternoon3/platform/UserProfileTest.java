package com.szu.afternoon3.platform;

import com.szu.afternoon3.platform.common.UserContext;
import com.szu.afternoon3.platform.entity.User;
import com.szu.afternoon3.platform.mapper.UserMapper;
import com.szu.afternoon3.platform.service.UserService;
import com.szu.afternoon3.platform.vo.UserProfileVO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;

@SpringBootTest
public class UserProfileTest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserService userService;

    @Test
    public void testGetUserProfile() {
        System.out.println("========== 开始 UserProfileService 测试 ==========");

        String randomSuffix = String.valueOf(System.currentTimeMillis());

        // 1. 准备数据
        User user = new User();
        user.setNickname("ProfileTest_" + randomSuffix.substring(8));
        user.setEmail("profile_" + randomSuffix + "@test.com");
        user.setOpenid("openid_profile_" + randomSuffix);
        user.setPassword("encrypted_pwd");
        user.setGender(1);
        user.setBirthday(LocalDate.of(2000, 1, 1));
        user.setRegion("Shenzhen");
        user.setBio("Hello World");
        user.setRole("USER");
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setIsDeleted(0);

        userMapper.insert(user);
        Long userId = user.getId();
        System.out.println("插入测试用户 ID: " + userId);

        // 2. 模拟登录上下文
        UserContext.setUserId(userId);

        try {
            // 3. 调用 Service
            UserProfileVO vo = userService.getUserProfile();

            // 4. 验证结果
            System.out.println("获取到的 Profile: " + vo);

            Assertions.assertNotNull(vo);
            Assertions.assertEquals(userId.toString(), vo.getUserId());
            Assertions.assertEquals(user.getNickname(), vo.getNickname());
            Assertions.assertEquals("2000-01-01", vo.getBirthday());
            Assertions.assertEquals(true, vo.getHasPassword());

            System.out.println("测试通过！");

        } finally {
            // 5. 清理上下文
            UserContext.clear();
        }

        System.out.println("========== 测试结束 ==========");
    }
}
