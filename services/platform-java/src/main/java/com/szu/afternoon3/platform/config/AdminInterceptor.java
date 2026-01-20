package com.szu.afternoon3.platform.config;

import com.szu.afternoon3.platform.common.UserContext;
import com.szu.afternoon3.platform.exception.AppException;
import com.szu.afternoon3.platform.enums.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Slf4j
public class AdminInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取当前角色 (由 TokenInterceptor 解析并放入上下文)
        String role = UserContext.getRole();

        // 2. 鉴权：必须是 ADMIN
        if (!"ADMIN".equals(role)) {
            log.warn("越权访问拦截: UserID={}, Role={}, URL={}", 
                     UserContext.getUserId(), role, request.getRequestURI());
            throw new AppException(ResultCode.PERMISSION_DENIED, "非管理员无权操作");
        }

        // 3. 放行
        return true;
    }
}