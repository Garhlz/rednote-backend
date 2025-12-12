package com.szu.afternoon3.platform.listener;

import cn.hutool.core.collection.CollUtil;
import com.szu.afternoon3.platform.config.RabbitConfig;
import com.szu.afternoon3.platform.entity.mongo.CommentDoc;
import com.szu.afternoon3.platform.event.PostCreateEvent;
import com.szu.afternoon3.platform.event.PostDeleteEvent;
import com.szu.afternoon3.platform.event.PostUpdateEvent;
import com.szu.afternoon3.platform.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

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
    @Autowired
    private CommentLikeRepository commentLikeRepository;
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
            postRatingRepository.deleteByPostId(postId);

            // 方案 A: 完美主义 (数据绝对干净，但性能稍慢)
            // 第一步：查出该贴下所有评论的 ID (只查 ID，很快)
//             List<CommentDoc> comments = commentRepository.findByPostIdAndParentIdIsNull(postId, Pageable.unpaged()).getContent();
//             List<String> commentIds = comments.stream().map(CommentDoc::getId).collect(Collectors.toList());

            // 第二步：批量删除这些评论的点赞
//             if (CollUtil.isNotEmpty(commentIds)) {
//                 // 需要在 Repository 加一个 deleteByCommentIdIn(List<String> ids)
//                 commentLikeRepository.deleteByCommentIdIn(commentIds);
//             }

            // 方案 B: 实用主义 (推荐)
            // 直接删除评论，忽略"评论点赞表"里的孤儿数据。
            // 因为 commentId 已经删了，依附于它的点赞数据永远查不出来，只是占一点点磁盘空间而已。
            commentRepository.deleteByPostId(postId);

            log.info("删帖清理完成");
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