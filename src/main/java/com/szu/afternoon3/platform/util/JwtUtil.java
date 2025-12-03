package com.szu.afternoon3.platform.util;

import cn.hutool.jwt.JWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    // 从配置文件读取
    @Value("${szu.jwt.secret}")
    private String secretKey;

    public String createToken(Long userId) {
        return JWT.create()
                .setPayload("userId", userId)
//                .setExpiresAt(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24 * 7)) // 7天?
                .setExpiresAt(null) // TODO 开发阶段设置为不过期好了
                .setKey(secretKey.getBytes(StandardCharsets.UTF_8))
                .sign();
    }
}