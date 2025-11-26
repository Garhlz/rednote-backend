package com.szu.afternoon3.platform.util;

import cn.hutool.jwt.JWT;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtUtil {
    // 真实项目中这个密钥应该放在配置文件里
    // todo修改JWT密钥
    private static final byte[] KEY = "ElaineIsWritingSpringCode2025".getBytes();

    public String createToken(Long userId) {
        return JWT.create()
                .setPayload("userId", userId)
                .setExpiresAt(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24 * 7)) // 7天过期
                .setKey(KEY)
                .sign();
    }

    // 解析 Token 获取 userId 的方法后续再写
}
