package com.szu.afternoon3.platform.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
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
    public static final String QUEUE_NOTIFY_AUDIT = "platform.notify.audit.queue";
    public static final String QUEUE_ES_SYNC = "platform.es.sync.queue";
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

    @Bean
    public Queue notifyAuditQueue() {return createQueueWithDlq(QUEUE_NOTIFY_AUDIT);}

    @Bean
    public Queue esSyncQueue() {return createQueueWithDlq(QUEUE_ES_SYNC);}

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

    @Bean
    public Binding bindingNotifyAudit() {
        return BindingBuilder.bind(notifyAuditQueue()).to(platformExchange()).with("post.audit");
    }

    @Bean
    public Binding bindingEsSync() {
        return BindingBuilder.bind(esSyncQueue())
                .to(platformExchange())
                .with("post.#"); // 简单粗暴，监听所有帖子变动
    }


    @Bean
    public Binding bindingUserUpdateToEs() {
        // 让 esSyncQueue 监听 user.update 路由键
        return BindingBuilder.bind(esSyncQueue()).to(platformExchange()).with("user.update");
    }

    @Bean
    public Binding bindingAuditPassToEs() {
        return BindingBuilder.bind(esSyncQueue()).to(platformExchange()).with("post.audit.pass");
    }

    // ==========================================
    // 5. 序列化配置
    // ==========================================

    @Bean
    public MessageConverter jsonMessageConverter() {
        // 1. 创建 ObjectMapper 并配置时间模块 (这是关键修正！)
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        // 注册 Java 8 时间模块
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        // 禁止将日期写为时间戳/数组，强制转为 ISO 字符串 (如 "2023-12-19T10:20:30")
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 2. 将 ObjectMapper 注入 Converter
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);

        // 3. 配置类型映射 (保留你原有的安全配置)
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        // 解决 "无法解析符号 TypePrecedence"
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.TYPE_ID);
        // 配置白名单
        typeMapper.setTrustedPackages(
                "com.szu.afternoon3.platform.event",
                "com.szu.afternoon3.platform.entity",
                "com.szu.afternoon3.platform.entity.mongo",
                "com.szu.afternoon3.platform.entity.es"
        );

        // 4. 将配置好的 TypeMapper 设置给 Converter
        converter.setJavaTypeMapper(typeMapper);

        return converter;
    }
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);

        // 【关键】强制使用我们配置好的 JSON 转换器
        factory.setMessageConverter(jsonMessageConverter());

        // 可选：设置手动 ACK (如果你代码里写了 channel.basicAck)
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);

        return factory;
    }
}