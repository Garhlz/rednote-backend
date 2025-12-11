package com.szu.afternoon3.platform.listener;

import com.szu.afternoon3.platform.config.RabbitConfig;
import com.szu.afternoon3.platform.event.PostCreateEvent;
import com.szu.afternoon3.platform.event.PostDeleteEvent;
import com.szu.afternoon3.platform.event.PostUpdateEvent;
import com.szu.afternoon3.platform.repository.CommentRepository;
import com.szu.afternoon3.platform.repository.PostCollectRepository;
import com.szu.afternoon3.platform.repository.PostLikeRepository;
import com.szu.afternoon3.platform.repository.PostRatingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 帖子相关事件监听器
 * 统一监听 platform.post.queue，利用 @RabbitHandler 分发不同类型的事件
 */
@Component
@Slf4j
// 假设你在 RabbitConfig 中增加了这个队列常量，值为 "platform.post.queue"
@RabbitListener(queues = "platform.post.queue")
public class PostEventListener {

    @Autowired
    private PostLikeRepository postLikeRepository;
    @Autowired
    private PostCollectRepository postCollectRepository;
    @Autowired
    private CommentRepository commentRepository;
    @Autowired
    private PostRatingRepository postRatingRepository;
    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 处理帖子删除
     */
    @RabbitHandler
    public void handlePostDelete(PostDeleteEvent event) {
        String postId = event.getPostId();
        log.info("RabbitMQ 收到删帖事件，清理关联数据... postId={}", postId);

        // 1. 清理 Redis 缓存 (如果有)
        // redisTemplate.delete("post:detail:" + postId);

        // 2. 清理 MongoDB 关联数据
        try {
            postLikeRepository.deleteByPostId(postId);
            postCollectRepository.deleteByPostId(postId);
            commentRepository.deleteByPostId(postId);
            // postRatingRepository.deleteByPostId(postId);
        } catch (Exception e) {
            log.error("删帖清理失败: postId={}", postId, e);
        }
    }

    /**
     * 处理帖子创建 (审核逻辑)
     * 来自原 PostAuditListener
     */
    @RabbitHandler
    public void handlePostCreate(PostCreateEvent event) {
        log.info("RabbitMQ 收到发帖事件，进入审核流程: {}", event.getPostId());
        // TODO: 调用阿里云/百度 内容安全 API
        // 审核通过 -> update post set status = 1
        // 审核不通过 -> update post set status = 2
    }

    /**
     * 处理帖子更新 (审核逻辑)
     */
    @RabbitHandler
    public void handlePostUpdate(PostUpdateEvent event) {
        log.info("RabbitMQ 收到修帖事件，重新审核: {}", event.getPostId());
        // TODO: 重新审核逻辑
    }
}