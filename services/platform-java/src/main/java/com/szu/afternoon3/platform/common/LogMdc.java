package com.szu.afternoon3.platform.common;

import cn.hutool.core.lang.UUID;
import org.slf4j.MDC;

public final class LogMdc {

    public static final String SERVICE = "service";
    public static final String TRACE_ID = "traceId";
    public static final String REQUEST_ID = "requestId";
    public static final String ROUTING_KEY = "routingKey";
    public static final String TRACEPARENT = "traceparent";
    public static final String TRACESTATE = "tracestate";

    private LogMdc() {
    }

    public static String initHttpContext(String requestId, String service) {
        return initHttpContext(requestId, null, null, service);
    }

    public static String initHttpContext(String requestId, String traceId, String traceparent, String service) {
        String effectiveId = requestId;
        if (effectiveId == null || effectiveId.isBlank()) {
            effectiveId = UUID.fastUUID().toString(true);
        }
        MDC.put(SERVICE, service);
        MDC.put(REQUEST_ID, effectiveId);
        if (traceId == null || traceId.isBlank()) {
            MDC.put(TRACE_ID, effectiveId);
        } else {
            MDC.put(TRACE_ID, traceId);
        }
        if (traceparent != null && !traceparent.isBlank()) {
            MDC.put(TRACEPARENT, traceparent);
        }
        return effectiveId;
    }

    public static void bindMqContext(String requestId, String routingKey, String service) {
        bindMqContext(requestId, null, null, routingKey, service);
    }

    public static void bindMqContext(String requestId, String traceId, String traceparent, String routingKey, String service) {
        String effectiveId = requestId;
        if (effectiveId == null || effectiveId.isBlank()) {
            effectiveId = UUID.fastUUID().toString(true);
        }
        MDC.put(SERVICE, service);
        MDC.put(REQUEST_ID, effectiveId);
        if (traceId == null || traceId.isBlank()) {
            MDC.put(TRACE_ID, effectiveId);
        } else {
            MDC.put(TRACE_ID, traceId);
        }
        if (traceparent != null && !traceparent.isBlank()) {
            MDC.put(TRACEPARENT, traceparent);
        }
        if (routingKey != null && !routingKey.isBlank()) {
            MDC.put(ROUTING_KEY, routingKey);
        }
    }

    public static void clear() {
        MDC.remove(SERVICE);
        MDC.remove(REQUEST_ID);
        MDC.remove(TRACE_ID);
        MDC.remove(ROUTING_KEY);
        MDC.remove(TRACEPARENT);
        MDC.remove(TRACESTATE);
    }
}
