package com.szu.afternoon3.platform.util;

import cn.hutool.jwt.JWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secretKey", "unit-test-secret-with-enough-entropy");
    }

    @Test
    void accessTokenShouldCarryUserContextAndType() {
        String token = jwtUtil.createAccessToken(42L, "USER", "Elaine");

        assertThat(jwtUtil.validateToken(token)).isTrue();
        JWT parsed = jwtUtil.parseToken(token);
        assertThat(parsed.getPayload("userId")).hasToString("42");
        assertThat(parsed.getPayload("role")).isEqualTo("USER");
        assertThat(parsed.getPayload("nickname")).isEqualTo("Elaine");
        assertThat(parsed.getPayload("type")).isEqualTo("access");
    }

    @Test
    void refreshTokenShouldNotContainAccessOnlyClaims() {
        String token = jwtUtil.createRefreshToken(42L);

        assertThat(jwtUtil.validateToken(token)).isTrue();
        JWT parsed = jwtUtil.parseToken(token);
        assertThat(parsed.getPayload("userId")).hasToString("42");
        assertThat(parsed.getPayload("type")).isEqualTo("refresh");
        assertThat(parsed.getPayload("role")).isNull();
        assertThat(parsed.getPayload("nickname")).isNull();
    }

    @Test
    void malformedOrTamperedTokenShouldBeRejected() {
        String token = jwtUtil.createAccessToken(42L, "USER", "Elaine");
        String tampered = token.substring(0, token.length() - 1)
                + (token.endsWith("a") ? "b" : "a");

        assertThat(jwtUtil.validateToken("not-a-jwt")).isFalse();
        assertThat(jwtUtil.validateToken(tampered)).isFalse();
    }
}
