package com.szu.afternoon3.platform.config;

import cn.hutool.core.lang.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RequestIdInterceptor implements HandlerInterceptor {
    
    private static final String TRACE_ID = "traceId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 生成一个唯一ID，放入 MDC 容器
        String uuid = UUID.fastUUID().toString(true);
        MDC.put(TRACE_ID, uuid);
        
        // 顺便把 ID 塞回 Response Header，方便前端拿着 ID 找你报修
        response.setHeader("X-Trace-Id", uuid);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 请求结束，清理 MDC，防止内存泄漏
        MDC.remove(TRACE_ID);
    }
}