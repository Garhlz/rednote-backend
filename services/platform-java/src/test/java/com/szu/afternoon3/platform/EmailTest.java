package com.szu.afternoon3.platform;

import com.szu.afternoon3.platform.service.AuthService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
public class EmailTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    public void testSendEmailCode() {
        String targetEmail = "garhlz257@gmail.com";
        System.out.println("========== 开始邮件发送测试 ==========");
        System.out.println("目标邮箱: " + targetEmail);

        // 1. 【关键】先清除 Redis 里的限流 Key，防止 "操作太频繁" 报错
        // 假设你在代码里定义的限流 Key 是 "verify:limit:{email}"
        String limitKey = "verify:limit:" + targetEmail;
        redisTemplate.delete(limitKey);
        System.out.println("已清除旧的限流 Key，确保本次发送能通过...");

        try {
            // 2. 调用发送接口 (假设你已经把 type 参数删掉了)
            authService.sendEmailCode(targetEmail);
            System.out.println("✅ Service 方法调用成功！邮件应该已发出。");
        } catch (Exception e) {
            System.err.println("❌ 发送失败，报错信息如下：");
            e.printStackTrace();
            Assertions.fail("邮件发送过程抛出异常");
        }

        // 3. 验证 Redis 是否存入了验证码
        // 既然你删除了 type，我假设你在 Service 内部把 key 硬编码成了 "verify:code:BIND:" + email
        // 或者 "verify:code:" + email。我们尝试获取一下。


        String possibleKey2 = "verify:code:" + targetEmail;


        String code = redisTemplate.opsForValue().get(possibleKey2);

        String validCode = null;
        if (code != null) {
            validCode = code;
            System.out.println("在 Redis 中找到验证码 (Key=" + possibleKey2 + "): " + validCode);
        }

        Assertions.assertNotNull(validCode, "Redis 中未找到验证码！请检查 Service 中存入 Redis 的 Key 格式。");
        System.out.println("========== 测试通过！请去你的收件箱确认 ==========");
    }
}