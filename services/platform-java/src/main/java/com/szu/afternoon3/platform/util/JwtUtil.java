package com.szu.afternoon3.platform.util;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    // 从配置文件读取
    @Value("${szu.jwt.secret}")
    private String secretKey;

    // Access Token 有效期 7d
    private static final long ACCESS_EXPIRE = 1000L * 60 * 60 * 24 * 7;

    // Refresh Token 有效期 30d
    private static final long REFRESH_EXPIRE = 1000L * 60 * 60 * 24 * 30;

    public String createAccessToken(Long userId, String role, String nickname) {
        return JWT.create()
                .setPayload("userId", userId)
                .setPayload("role", role)
                .setPayload("nickname", nickname) // 【关键】放入昵称
                .setPayload("type", "access")     // 标记类型，防止混用
                .setExpiresAt(new Date(System.currentTimeMillis() + ACCESS_EXPIRE))
                .setKey(secretKey.getBytes(StandardCharsets.UTF_8))
                .sign();
    }

    /**
     * 创建 Refresh Token (长效，仅用于保活)
     */
    public String createRefreshToken(Long userId) {
        return JWT.create()
                .setPayload("userId", userId)
                .setPayload("type", "refresh")    // 标记类型
                .setExpiresAt(new Date(System.currentTimeMillis() + REFRESH_EXPIRE))
                .setKey(secretKey.getBytes(StandardCharsets.UTF_8))
                .sign();
    }

    /**
     * 校验 Token 签名和过期
     */
    public boolean validateToken(String token) {
        try {
            // 1. 校验签名 (校验 secretKey 是否正确)
            boolean isSigned = JWTUtil.verify(token, secretKey.getBytes(StandardCharsets.UTF_8));
            if (!isSigned) {
//                System.err.println("❌ Token 签名校验失败！可能原因：SecretKey 不一致或 Token 被篡改");
                return false;
            }

            // 2. 校验过期时间 (validate(0) 如果过期会抛出 ValidateException)
            JWTUtil.parseToken(token).validate(0);

            return true;
        } catch (Exception e) {
//            System.err.println("❌ Token 校验发生异常: " + e.getMessage());
//            e.printStackTrace();
            return false;
        }
    }

    public JWT parseToken(String token) {
        return JWTUtil.parseToken(token);
    }
}