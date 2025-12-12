package com.szu.afternoon3.platform.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    // ==========================================
    // 1. 常量定义
    // ==========================================

    // 交换机
    public static final String PLATFORM_EXCHANGE = "platform.topic.exchange";
    public static final String DLX_EXCHANGE = "platform.dlx.exchange"; // 死信交换机

    // 业务队列
    public static final String QUEUE_INTERACTION = "platform.interaction.queue";
    public static final String QUEUE_USER = "platform.user.queue";
    public static final String QUEUE_SEARCH = "platform.search.queue";
    public static final String QUEUE_POST = "platform.post.queue";
    public static final String QUEUE_COMMENT = "platform.comment.queue";
    public static final String QUEUE_LOG = "platform.log.queue";
    // 死信队列
    public static final String QUEUE_DEAD_LETTER = "platform.dead.letter.queue";


    // ==========================================
    // 2. 定义交换机
    // ==========================================

    // 业务交换机 (Topic)
    @Bean
    public TopicExchange platformExchange() {
        return new TopicExchange(PLATFORM_EXCHANGE);
    }

    // 死信交换机 (Topic) - 建议用 Topic 以保留原消息的 RoutingKey
    @Bean
    public TopicExchange dlxExchange() {
        return new TopicExchange(DLX_EXCHANGE);
    }

    // ==========================================
    // 3. 定义队列 (统一绑定 DLX)
    // ==========================================

    /**
     * 辅助方法：创建持久化并绑定死信交换机的队列
     * 这样所有业务队列都会自动具备死信能力
     */
    private Queue createQueueWithDlq(String queueName) {
        return QueueBuilder.durable(queueName)
                // 核心设置：指定该队列消息死掉后，发送到哪个交换机
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                // 可选：指定死信的 RoutingKey。如果不填，RabbitMQ 会默认使用原消息的 RoutingKey
                // 我们不填，这样在死信队列里也能通过 RoutingKey 知道消息原本是发给谁的
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

    @Bean
    public Queue commentQueue() { return createQueueWithDlq(QUEUE_COMMENT); } // 【修改】统一使用辅助方法

    @Bean
    public Queue logQueue() {return createQueueWithDlq(QUEUE_LOG);}

    // 死信队列本身 (普通持久化队列，不能套娃再绑死信)
    @Bean
    public Queue deadLetterQueue() {
        return new Queue(QUEUE_DEAD_LETTER, true);
    }

    // ==========================================
    // 4. 绑定关系 (Binding)
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

    @Bean
    public Binding bindingComment() {
        return BindingBuilder.bind(commentQueue()).to(platformExchange()).with("comment.#");
    }

    // 死信队列绑定：接收所有从 DLX 过来的消息 (# 通配符)
    @Bean
    public Binding bindingDlq() {
        return BindingBuilder.bind(deadLetterQueue()).to(dlxExchange()).with("#");
    }

    @Bean
    public Binding bindingLog() {
        return BindingBuilder.bind(logQueue()).to(platformExchange()).with("log.#");
    }

    // ==========================================
    // 5. 序列化配置
    // ==========================================

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}