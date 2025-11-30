package com.szu.afternoon3.platform.config;

import cn.hutool.core.util.StrUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import com.szu.afternoon3.platform.common.UserContext;
import com.szu.afternoon3.platform.exception.AppException;
import com.szu.afternoon3.platform.exception.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class TokenInterceptor implements HandlerInterceptor {

    @Value("${szu.jwt.secret}")
    private String secretKey;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 如果不是映射到方法直接通过（比如处理静态资源，或者 OPTIONS 请求）
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        // 2. 获取 Header 中的 Authorization
        String authHeader = request.getHeader("Authorization");

        // 3. 基础校验：Header 是否存在，格式是否为 "Bearer <token>"
        if (StrUtil.isBlank(authHeader) || !authHeader.startsWith("Bearer ")) {
            // 这里抛出异常，会被 GlobalExceptionHandler 捕获，返回 401 给前端
            throw new AppException(ResultCode.UNAUTHORIZED);
        }

        // 4. 截取 Token
        String token = authHeader.substring(7);

        try {
            // 5. 校验 Token 签名 (Hutool)
            boolean verify = JWTUtil.verify(token, secretKey.getBytes(StandardCharsets.UTF_8));
            if (!verify) {
                throw new AppException(ResultCode.TOKEN_EXPIRED);
            }

            // 6. 校验 Token 是否过期 (Hutool 默认会校验 exp 字段，但手动 validate 更保险)
            try {
                JWTUtil.parseToken(token).validate(0); // 0 表示不做容忍时间的偏差
            } catch (Exception e) {
                throw new AppException(ResultCode.TOKEN_EXPIRED);
            }

            // 7. 解析 userId
            JWT jwt = JWTUtil.parseToken(token);
            // 注意：payload 里的数字可能被解析为 Integer，建议转 String 再转 Long 安全
            Object userIdObj = jwt.getPayload("userId");
            if (userIdObj == null) {
                throw new AppException(ResultCode.UNAUTHORIZED);
            }
            Long userId = Long.valueOf(userIdObj.toString());

            // 8. 放入 ThreadLocal
            UserContext.setUserId(userId);

            // 放行
            return true;

        } catch (AppException e) {
            throw e; // 已经是我们定义的异常，直接抛出
        } catch (Exception e) {
            log.error("Token解析异常", e);
            throw new AppException(ResultCode.TOKEN_EXPIRED); // 其他解析错误统一视为无效
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 请求结束，必须清理 ThreadLocal，防止内存泄漏和数据污染
        UserContext.clear();
    }
}