package com.szu.afternoon3.platform;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

@SpringBootTest
public class RedisTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    public void testRedisConnectionAndOps() {
        System.out.println("========== 开始 Redis 连接测试 ==========");

        // 1. 定义一个 Key 和 Value
        String key = "test:connection:ping";
        String value = "pong_" + System.currentTimeMillis();

        // 2. 写入数据 (设置过期时间 60秒)
        System.out.println("正在写入数据: Key=" + key + ", Value=" + value);
        redisTemplate.opsForValue().set(key, value, 60, TimeUnit.SECONDS);

        // 3. 读取数据
        String retrievedValue = redisTemplate.opsForValue().get(key);
        System.out.println("读取到的数据: " + retrievedValue);

        // 4. 验证是否一致
        Assertions.assertEquals(value, retrievedValue, "Redis 读取的数据与写入的不一致！");
        System.out.println("✅ 读写测试通过！");

        // 5. 测试删除
        Boolean deleteResult = redisTemplate.delete(key);
        System.out.println("删除结果: " + deleteResult);
        Assertions.assertEquals(Boolean.TRUE, deleteResult, "删除 Key 失败！");

        // 6. 再次读取确认已为空
        String deletedValue = redisTemplate.opsForValue().get(key);
        Assertions.assertNull(deletedValue, "Key 应该已被删除，但仍能读取到！");

        System.out.println("========== Redis 测试全部通过 ==========");
    }
}