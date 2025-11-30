package com.szu.afternoon3.platform;

import com.szu.afternoon3.platform.entity.User;
import com.szu.afternoon3.platform.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;
import java.util.UUID;

@SpringBootTest
public class UserTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    public void testInsertUser() {
        System.out.println("========== 开始 PostgreSQL (User) 测试 ==========");

        // 为了防止 "Duplicate Key" 错误，我们生成一个随机后缀
        String randomSuffix = String.valueOf(System.currentTimeMillis());

        // 1. 创建一个新用户对象
        User user = new User();
        user.setNickname("测试组员_" + randomSuffix.substring(8)); // 取时间戳后几位
        // 保证 email 和 openid 每次运行都不一样
        user.setEmail("user_" + randomSuffix + "@szu.edu.cn");
        user.setOpenid("openid_" + randomSuffix);

        user.setPassword("123456");
        user.setRole("USER");
        user.setStatus(1);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        user.setIsDeleted(0);

        // 2. 插入数据库
        int result = userMapper.insert(user);

        // 3. 验证结果
        if (result > 0) {
            System.out.println("写入成功！User ID: " + user.getId());
            System.out.println("生成的 Email: " + user.getEmail());
        } else {
            System.err.println("写入失败！");
        }

        System.out.println("========== 测试结束 ==========");
    }
}