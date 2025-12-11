package com.szu.afternoon3.platform.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitConfig {

    // --- 业务常量 ---
    public static final String PLATFORM_EXCHANGE = "platform.topic.exchange";
    public static final String QUEUE_INTERACTION = "platform.interaction.queue";
    public static final String QUEUE_USER = "platform.user.queue";
    public static final String QUEUE_SEARCH = "platform.search.queue";
    public static final String QUEUE_POST = "platform.post.queue";

    // --- 【新增】死信常量 ---
    // 死信交换机
    public static final String DLX_EXCHANGE = "platform.dlx.exchange";
    // 死信队列 (所有死掉的消息都进这一个，方便统一监控)
    public static final String QUEUE_DEAD_LETTER = "platform.dead.letter.queue";

    // ==========================================
    // 1. 定义交换机
    // ==========================================

    // 业务交换机
    @Bean
    public TopicExchange platformExchange() {
        return new TopicExchange(PLATFORM_EXCHANGE);
    }

    // 【新增】死信交换机 (Topic类型，方便保留原消息的 RoutingKey)
    @Bean
    public TopicExchange dlxExchange() {
        return new TopicExchange(DLX_EXCHANGE);
    }

    // ==========================================
    // 2. 定义队列 (关键修改：绑定 DLX)
    // ==========================================

    /**
     * 辅助方法：创建带有死信配置的队列
     */
    private Queue createQueueWithDlq(String queueName) {
        return QueueBuilder.durable(queueName)
                // 核心设置：指定该队列消息死掉后，发送到哪个交换机
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                // 可选：指定死信的 RoutingKey。如果不填，默认沿用原消息的 RoutingKey
                // 我们不填，这样在死信队列里也能看到它原本是发给谁的
                .build();
    }

    @Bean
    public Queue interactionQueue() { return createQueueWithDlq(QUEUE_INTERACTION); }

    @Bean
    public Queue userQueue() { return createQueueWithDlq(QUEUE_USER); }

    @Bean
    public Queue searchQueue() { return createQueueWithDlq(QUEUE_SEARCH); }

    @Bean
    public Queue postQueue() { return createQueueWithDlq(QUEUE_POST); }

    // 【新增】死信队列本身 (它自己不需要再配死信了，否则会套娃)
    @Bean
    public Queue deadLetterQueue() {
        return new Queue(QUEUE_DEAD_LETTER, true);
    }

    // ==========================================
    // 3. 绑定关系
    // ==========================================

    @Bean
    public Binding bindingInteraction() {
        return BindingBuilder.bind(interactionQueue()).to(platformExchange()).with("interaction.#");
    }

    @Bean
    public Binding bindingUser() {
        return BindingBuilder.bind(userQueue()).to(platformExchange()).with("user.#");
    }

    @Bean
    public Binding bindingSearch() {
        return BindingBuilder.bind(searchQueue()).to(platformExchange()).with("search.#");
    }

    @Bean
    public Binding bindingPost() {
        return BindingBuilder.bind(postQueue()).to(platformExchange()).with("post.#");
    }

    // 【新增】死信队列绑定
    // 使用 # 通配符，接收所有从业务队列“死”过来的消息
    @Bean
    public Binding bindingDlq() {
        return BindingBuilder.bind(deadLetterQueue()).to(dlxExchange()).with("#");
    }

    // ==========================================
    // 4. 序列化配置
    // ==========================================
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}