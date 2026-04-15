package com.szu.afternoon3.platform.common;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageBuilderSupport;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MqPublisher {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private MessageConverter messageConverter;

    public void publish(String exchange, String routingKey, Object payload) {
        String requestId = MDC.get(LogMdc.REQUEST_ID);
        String traceId = MDC.get(LogMdc.TRACE_ID);
        String traceparent = MDC.get(LogMdc.TRACEPARENT);
        String tracestate = MDC.get(LogMdc.TRACESTATE);
        Message message = messageConverter.toMessage(payload, null);
        MessageBuilderSupport<Message> builder = MessageBuilder.fromMessage(message)
                .setHeader("X-Request-Id", requestId)
                .setHeader("X-Trace-Id", traceId != null ? traceId : requestId)
                .setHeader("X-Service", "platform-java")
                .setHeader("X-Routing-Key", routingKey)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        if (traceparent != null && !traceparent.isBlank()) {
            builder.setHeader("traceparent", traceparent);
        }
        if (tracestate != null && !tracestate.isBlank()) {
            builder.setHeader("tracestate", tracestate);
        }
        Message enriched = builder.build();

        rabbitTemplate.send(exchange, routingKey, enriched);
        log.info("mq publish success service=platform-java requestId={} traceId={} routingKey={}",
                requestId, traceId != null ? traceId : requestId, routingKey);
    }
}
