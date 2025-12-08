package com.szu.afternoon3.platform.service.impl;

import com.szu.afternoon3.platform.common.RedisKey;
import com.szu.afternoon3.platform.common.UserContext;
import com.szu.afternoon3.platform.dto.PostRateDTO;
import com.szu.afternoon3.platform.event.InteractionEvent;
import com.szu.afternoon3.platform.exception.AppException;
import com.szu.afternoon3.platform.exception.ResultCode;
import com.szu.afternoon3.platform.service.InteractionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class InteractionServiceImpl implements InteractionService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Override
    public void likePost(String postId) {
        Long userId = UserContext.getUserId();
        String key = RedisKey.POST_LIKE_SET + postId;

        // Redis 原子操作: ADD
        // 返回 1 表示新添加(之前没赞过)，0 表示已存在
        Long result = redisTemplate.opsForSet().add(key, userId.toString());

        if (result != null && result > 0) {
            // 只有状态改变了，才发事件去写库
            eventPublisher.publishEvent(new InteractionEvent(userId, postId, "LIKE", "ADD", null));
        }
    }

    @Override
    public void unlikePost(String postId) {
        Long userId = UserContext.getUserId();
        String key = RedisKey.POST_LIKE_SET + postId;

        Long result = redisTemplate.opsForSet().remove(key, userId.toString());

        if (result != null && result > 0) {
            eventPublisher.publishEvent(new InteractionEvent(userId, postId, "LIKE", "REMOVE", null));
        }
    }

    @Override
    public void collectPost(String postId) {
        Long userId = UserContext.getUserId();
        String key = RedisKey.POST_COLLECT_SET + postId;

        Long result = redisTemplate.opsForSet().add(key, userId.toString());

        if (result != null && result > 0) {
            eventPublisher.publishEvent(new InteractionEvent(userId, postId, "COLLECT", "ADD", null));
        }
    }

    @Override
    public void uncollectPost(String postId) {
        Long userId = UserContext.getUserId();
        String key = RedisKey.POST_COLLECT_SET + postId;

        Long result = redisTemplate.opsForSet().remove(key, userId.toString());

        if (result != null && result > 0) {
            eventPublisher.publishEvent(new InteractionEvent(userId, postId, "COLLECT", "REMOVE", null));
        }
    }

    @Override
    public void ratePost(PostRateDTO dto) {
        Long userId = UserContext.getUserId();
        String postId = dto.getPostId();
        Double score = dto.getScore();

        String key = RedisKey.POST_RATE_HASH + postId;

        // Redis Hash: HSET postId userId score
        // 这里我们不判断 result，因为用户可能是在“修改评分”，无论如何都要发事件更新 Mongo
        redisTemplate.opsForHash().put(key, userId.toString(), score.toString());

        // 发送评分事件
        eventPublisher.publishEvent(new InteractionEvent(userId, postId, "RATE", "ADD", score));
    }

    @Override
    public void likeComment(String commentId) {
        Long userId = UserContext.getUserId();
        String key = RedisKey.COMMENT_LIKE_SET + commentId;

        // 1. Redis 去重 (Write-Behind)c
        Long result = redisTemplate.opsForSet().add(key, userId.toString());

        if (result != null && result > 0) {
            // 2. 发送异步事件
            // type 使用 "COMMENT_LIKE" 以便 Listener 区分
            eventPublisher.publishEvent(new InteractionEvent(userId, commentId, "COMMENT_LIKE", "ADD", null));
        }
    }

    @Override
    public void unlikeComment(String commentId) {
        Long userId = UserContext.getUserId();
        String key = RedisKey.COMMENT_LIKE_SET + commentId;

        Long result = redisTemplate.opsForSet().remove(key, userId.toString());

        if (result != null && result > 0) {
            eventPublisher.publishEvent(new InteractionEvent(userId, commentId, "COMMENT_LIKE", "REMOVE", null));
        }
    }
}