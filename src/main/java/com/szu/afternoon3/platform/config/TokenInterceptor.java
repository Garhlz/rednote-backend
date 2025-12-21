package com.szu.afternoon3.platform.config;

import cn.hutool.core.util.StrUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import com.szu.afternoon3.platform.common.UserContext;
import com.szu.afternoon3.platform.exception.AppException;
import com.szu.afternoon3.platform.enums.ResultCode;
import com.szu.afternoon3.platform.mapper.UserMapper;
import com.szu.afternoon3.platform.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class TokenInterceptor implements HandlerInterceptor {
    @Autowired
    private StringRedisTemplate redisTemplate; // [新增] 注入 Redis
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private JwtUtil jwtUtil; // 注入工具类，不再直接用 Hutool 静态方法
    @Value("${szu.jwt.secret}")
    private String secretKey;

    private static final String TOKEN_BLOCK_PREFIX = "auth:token:block:";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (StrUtil.isBlank(authHeader) || !authHeader.startsWith("Bearer ")) {
            throw new AppException(ResultCode.UNAUTHORIZED);
        }

        String token = authHeader.substring(7);

        // 1. 检查 Redis 黑名单 (逻辑保持不变)
        if (redisTemplate.hasKey(TOKEN_BLOCK_PREFIX + token)) {
            throw new AppException(ResultCode.USER_LOGGED_OUT);
        }

        // 2. 校验签名和过期
        if (!jwtUtil.validateToken(token)) {
            throw new AppException(ResultCode.TOKEN_EXPIRED);
        }

        try {
            JWT jwt = jwtUtil.parseToken(token);

            // 3. 【新增】类型检查：必须是 Access Token
            String type = (String) jwt.getPayload("type");
            // 兼容旧 Token (没有 type 字段的) 或明确校验 "access"
            if (StrUtil.isNotBlank(type) && !"access".equals(type)) {
                throw new AppException(ResultCode.UNAUTHORIZED, "凭证类型错误");
            }

            // 4. 解析数据
            Long userId = Long.valueOf(jwt.getPayload("userId").toString());
            String role = (String) jwt.getPayload("role");
            if (role == null) role = "USER";

            // 5. 【新增】提取 Nickname (免查库优化)
            String nickname = (String) jwt.getPayload("nickname");
            if (StrUtil.isBlank(nickname)) nickname = "用户"; // 兜底

            // 6. 放入上下文
            UserContext.setUserId(userId);
            UserContext.setRole(role);
            UserContext.setNickname(nickname); // 设置昵称

            return true;

        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Token解析详细异常", e);
            throw new AppException(ResultCode.TOKEN_EXPIRED);
        }
    }
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 请求结束，必须清理 ThreadLocal，防止内存泄漏和数据污染
        UserContext.clear();
    }
}