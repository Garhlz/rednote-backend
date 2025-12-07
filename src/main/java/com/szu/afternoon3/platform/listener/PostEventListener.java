package com.szu.afternoon3.platform.listener;

import com.szu.afternoon3.platform.event.PostDeleteEvent;
import com.szu.afternoon3.platform.repository.CommentRepository;
import com.szu.afternoon3.platform.repository.PostCollectRepository;
import com.szu.afternoon3.platform.repository.PostLikeRepository;
import com.szu.afternoon3.platform.repository.PostRatingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
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
     * 处理帖子删除事件
     * 1. 清理 Redis 缓存
     * 2. 级联删除/清理 MongoDB 中的关联数据 (评论、点赞、收藏)
     */
    @Async // 关键：异步执行，不阻塞主线程，保证接口快速响应
    @EventListener
    public void handlePostDelete(PostDeleteEvent event) {
        String postId = event.getPostId();
        log.info("监听到帖子删除事件，开始异步清理关联数据... postId={}", postId);

        long start = System.currentTimeMillis();

        // 1. 清理 Redis 缓存 (Cache Aside Pattern)
        // 假设你缓存了帖子详情，Key 格式如 "post:detail:{id}"
        String cacheKey = "post:detail:" + postId;
        redisTemplate.delete(cacheKey);
        // 如果有热门榜单缓存，可能也需要处理，或者等待其自动过期
        // redisTemplate.delete("rednote:tags:hot"); // 可选

        // 2. 清理关联的 MongoDB 数据
        // 注意：这里使用的是 Repository 中定义的 deleteByPostId
        // 如果你的业务要求“帖子恢复后点赞还在”，则这里也应该改为 Update isDeleted=1
        // 但根据现有代码结构，这里执行物理删除以保持数据整洁。

        try {
            // 2.1 删除该帖子的所有点赞记录
            postLikeRepository.deleteByPostId(postId);

            // 2.2 删除该帖子的所有收藏记录
            postCollectRepository.deleteByPostId(postId);

            // 2.3 删除该帖子的所有评论
            commentRepository.deleteByPostId(postId);

            // 2.4 删除评分记录 (如果有)
            // postRatingRepository.deleteByPostId(postId); // 需在 Repository 补充此方法

        } catch (Exception e) {
            log.error("清理帖子关联数据失败: postId={}", postId, e);
            // 在生产环境中，这里可能需要写入一张 "RetryTable" 或发送到 MQ 死信队列进行重试
        }

        log.info("帖子关联数据清理完成，耗时: {}ms", System.currentTimeMillis() - start);
    }
}