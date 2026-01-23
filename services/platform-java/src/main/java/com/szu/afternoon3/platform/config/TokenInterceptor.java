package com.szu.afternoon3.platform.config;

import com.szu.afternoon3.platform.common.UserContext;
import com.szu.afternoon3.platform.exception.AppException;
import com.szu.afternoon3.platform.enums.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class TokenInterceptor implements HandlerInterceptor {
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String USER_NICKNAME_HEADER = "X-User-Nickname";
    private static final String[] PUBLIC_PATHS = {
            "/api/post/list",
            "/api/post/search",
            "/api/post/search/suggest",
            "/api/tag/hot",
            "/api/comment/list",
            "/api/comment/sub-list"
    };

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        String userIdHeader = request.getHeader(USER_ID_HEADER);
        if (userIdHeader == null || userIdHeader.isBlank()) {
            if (isPublicPath(request.getRequestURI())) {
                return true;
            }
            throw new AppException(ResultCode.UNAUTHORIZED);
        }

        try {
            Long userId = Long.valueOf(userIdHeader);
            String role = request.getHeader(USER_ROLE_HEADER);
            if (role == null || role.isBlank()) {
                role = "USER";
            }
            String nickname = request.getHeader(USER_NICKNAME_HEADER);
            if (nickname == null || nickname.isBlank()) {
                nickname = "用户";
            }

            UserContext.setUserId(userId);
            UserContext.setRole(role);
            UserContext.setNickname(nickname);
            return true;
        } catch (NumberFormatException e) {
            log.warn("Invalid user id header: {}", userIdHeader);
            throw new AppException(ResultCode.UNAUTHORIZED);
        }
    }

    private boolean isPublicPath(String uri) {
        if (uri == null) {
            return false;
        }
        for (String path : PUBLIC_PATHS) {
            if (uri.equals(path)) {
                return true;
            }
        }
        return false;
    }
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 请求结束，必须清理 ThreadLocal，防止内存泄漏和数据污染
        UserContext.clear();
    }
}
