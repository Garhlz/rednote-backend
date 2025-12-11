package com.szu.afternoon3.platform.service.impl;

import com.szu.afternoon3.platform.common.RedisKey;
import com.szu.afternoon3.platform.common.UserContext;
import com.szu.afternoon3.platform.config.RabbitConfig; // 引入配置
import com.szu.afternoon3.platform.dto.PostRateDTO;
import com.szu.afternoon3.platform.event.InteractionEvent;
import com.szu.afternoon3.platform.service.InteractionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate; // 引入 RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class InteractionServiceImpl implements InteractionService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate; // 替换 ApplicationEventPublisher

    @Override
    public void likePost(String postId) {
        Long userId = UserContext.getUserId();
        String key = RedisKey.POST_LIKE_SET + postId;

        Long result = redisTemplate.opsForSet().add(key, userId.toString());

        if (result != null && result > 0) {
            // 发送消息到 RabbitMQ，路由键使用 interaction.create
            InteractionEvent event = new InteractionEvent(userId, postId, "LIKE", "ADD", null);
            rabbitTemplate.convertAndSend(RabbitConfig.PLATFORM_EXCHANGE, "interaction.create", event);
        }
    }

    @Override
    public void unlikePost(String postId) {
        Long userId = UserContext.getUserId();
        String key = RedisKey.POST_LIKE_SET + postId;

        Long result = redisTemplate.opsForSet().remove(key, userId.toString());

        if (result != null && result > 0) {
            // 路由键使用 interaction.delete
            InteractionEvent event = new InteractionEvent(userId, postId, "LIKE", "REMOVE", null);
            rabbitTemplate.convertAndSend(RabbitConfig.PLATFORM_EXCHANGE, "interaction.delete", event);
        }
    }

    @Override
    public void collectPost(String postId) {
        Long userId = UserContext.getUserId();
        String key = RedisKey.POST_COLLECT_SET + postId;

        Long result = redisTemplate.opsForSet().add(key, userId.toString());

        if (result != null && result > 0) {
            InteractionEvent event = new InteractionEvent(userId, postId, "COLLECT", "ADD", null);
            rabbitTemplate.convertAndSend(RabbitConfig.PLATFORM_EXCHANGE, "interaction.create", event);
        }
    }

    @Override
    public void uncollectPost(String postId) {
        Long userId = UserContext.getUserId();
        String key = RedisKey.POST_COLLECT_SET + postId;

        Long result = redisTemplate.opsForSet().remove(key, userId.toString());

        if (result != null && result > 0) {
            InteractionEvent event = new InteractionEvent(userId, postId, "COLLECT", "REMOVE", null);
            rabbitTemplate.convertAndSend(RabbitConfig.PLATFORM_EXCHANGE, "interaction.delete", event);
        }
    }

    @Override
    public void ratePost(PostRateDTO dto) {
        Long userId = UserContext.getUserId();
        String postId = dto.getPostId();
        Double score = dto.getScore();

        String key = RedisKey.POST_RATE_HASH + postId;

        redisTemplate.opsForHash().put(key, userId.toString(), score.toString());

        // 评分无论是新增还是修改，都可以视为 create 或 update，这里统一用 create
        InteractionEvent event = new InteractionEvent(userId, postId, "RATE", "ADD", score);
        rabbitTemplate.convertAndSend(RabbitConfig.PLATFORM_EXCHANGE, "interaction.create", event);
    }

    @Override
    public void likeComment(String commentId) {
        Long userId = UserContext.getUserId();
        String key = RedisKey.COMMENT_LIKE_SET + commentId;

        Long result = redisTemplate.opsForSet().add(key, userId.toString());

        if (result != null && result > 0) {
            InteractionEvent event = new InteractionEvent(userId, commentId, "COMMENT_LIKE", "ADD", null);
            rabbitTemplate.convertAndSend(RabbitConfig.PLATFORM_EXCHANGE, "interaction.create", event);
        }
    }

    @Override
    public void unlikeComment(String commentId) {
        Long userId = UserContext.getUserId();
        String key = RedisKey.COMMENT_LIKE_SET + commentId;

        Long result = redisTemplate.opsForSet().remove(key, userId.toString());

        if (result != null && result > 0) {
            InteractionEvent event = new InteractionEvent(userId, commentId, "COMMENT_LIKE", "REMOVE", null);
            rabbitTemplate.convertAndSend(RabbitConfig.PLATFORM_EXCHANGE, "interaction.delete", event);
        }
    }
}