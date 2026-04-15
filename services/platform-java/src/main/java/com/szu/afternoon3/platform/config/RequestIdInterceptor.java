package com.szu.afternoon3.platform.config;

import com.szu.afternoon3.platform.common.LogMdc;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RequestIdInterceptor implements HandlerInterceptor {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String TRACEPARENT_HEADER = "traceparent";
    private static final String TRACESTATE_HEADER = "tracestate";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = request.getHeader(TRACE_ID_HEADER);
        }
        String traceId = request.getHeader(TRACE_ID_HEADER);
        String traceparent = request.getHeader(TRACEPARENT_HEADER);
        String tracestate = request.getHeader(TRACESTATE_HEADER);
        String effectiveId = LogMdc.initHttpContext(requestId, traceId, traceparent, "platform-java");
        if (tracestate != null && !tracestate.isBlank()) {
            org.slf4j.MDC.put(LogMdc.TRACESTATE, tracestate);
        }
        response.setHeader(REQUEST_ID_HEADER, effectiveId);
        response.setHeader(TRACE_ID_HEADER, effectiveId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        LogMdc.clear();
    }
}
