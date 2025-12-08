package com.szu.afternoon3.platform.listener;

import com.szu.afternoon3.platform.entity.mongo.*;
import com.szu.afternoon3.platform.event.InteractionEvent;
import com.szu.afternoon3.platform.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
@Slf4j
public class InteractionEventListener {

    @Autowired
    private PostLikeRepository postLikeRepository;
    @Autowired
    private PostCollectRepository postCollectRepository;
    @Autowired
    private PostRatingRepository postRatingRepository;
    @Autowired
    private PostRepository postRepository; // 用于读取旧均分
    @Autowired
    private CommentLikeRepository commentLikeRepository;
    @Autowired
    private MongoTemplate mongoTemplate;

    @Async // 【关键】异步执行
    @EventListener
    public void handleInteraction(InteractionEvent event) {
        log.info("异步处理交互事件: {}", event);
        try {
            switch (event.getType()) {
                case "LIKE":
                    handleLike(event);
                    break;
                case "COLLECT":
                    handleCollect(event);
                    break;
                case "RATE":
                    handleRate(event);
                    break;
                case "COMMENT_LIKE":   // 【新增 Case】评论点赞
                    handleCommentLike(event);
                    break;
                default:
                    log.warn("未知的交互类型: {}", event.getType());
            }
        } catch (Exception e) {
            log.error("交互落库失败: ", e);
            // 生产环境建议：将失败事件写入死信队列或重试表
        }
    }

    // --- 点赞处理 ---
    private void handleLike(InteractionEvent event) {
        String postId = event.getTargetId();
        Long userId = event.getUserId();

        if ("ADD".equals(event.getAction())) {
            // 1. 幂等性检查：如果库里已经有了，就不插了（虽然 Redis 挡了一层，但为了保险）
            if (!postLikeRepository.existsByUserIdAndPostId(userId, postId)) {
                PostLikeDoc doc = new PostLikeDoc();
                doc.setUserId(userId);
                doc.setPostId(postId);
                doc.setCreatedAt(LocalDateTime.now());
                postLikeRepository.save(doc);

                // 2. 原子更新计数
                updatePostCount(postId, "likeCount", 1);
            }
        } else {
            postLikeRepository.deleteByUserIdAndPostId(userId, postId);
            updatePostCount(postId, "likeCount", -1);
        }
    }

    // --- 收藏处理 ---
    private void handleCollect(InteractionEvent event) {
        String postId = event.getTargetId();
        Long userId = event.getUserId();

        if ("ADD".equals(event.getAction())) {
            if (!postCollectRepository.existsByUserIdAndPostId(userId, postId)) {
                PostCollectDoc doc = new PostCollectDoc();
                doc.setUserId(userId);
                doc.setPostId(postId);
                doc.setCreatedAt(LocalDateTime.now());
                postCollectRepository.save(doc);
                updatePostCount(postId, "collectCount", 1);
            }
        } else {
            postCollectRepository.deleteByUserIdAndPostId(userId, postId);
            updatePostCount(postId, "collectCount", -1);
        }
    }

    // --- 评分处理 (难点) ---
    private void handleRate(InteractionEvent event) {
        String postId = event.getTargetId();
        Long userId = event.getUserId();
        Double newScore = event.getValue();

        // 1. 查旧评分
        Optional<PostRatingDoc> oldRatingOpt = postRatingRepository.findByUserIdAndPostId(userId, postId);

        // 2. 查 Post 当前状态 (需要 totalCount 和 currentAvg 来计算)
        // 注意：这里读取可能会有微小的并发偏差，但在大作业场景可接受
        PostDoc post = postRepository.findById(postId).orElse(null);
        if (post == null) return;

        double currentAvg = post.getRatingAverage() == null ? 0.0 : post.getRatingAverage();
        int currentCount = post.getRatingCount() == null ? 0 : post.getRatingCount();

        // 计算当前总分
        double totalScore = currentAvg * currentCount;

        if (oldRatingOpt.isPresent()) {
            // === 情况 A: 修改评分 ===
            PostRatingDoc oldRating = oldRatingOpt.get();
            Double oldScore = oldRating.getScore();

            // 如果分值没变，啥也不干
            if (oldScore.equals(newScore)) return;

            // 更新记录
            oldRating.setScore(newScore);
            oldRating.setUpdatedAt(LocalDateTime.now());
            postRatingRepository.save(oldRating);

            // 重新计算均分: (总分 - 旧分 + 新分) / 总人数
            double newTotalScore = totalScore - oldScore + newScore;
            double newAvg = newTotalScore / currentCount; // 人数不变

            // 更新 Post
            updatePostRating(postId, newAvg, 0); // count 增量为 0

        } else {
            // === 情况 B: 新增评分 ===
            PostRatingDoc doc = new PostRatingDoc();
            doc.setUserId(userId);
            doc.setPostId(postId);
            doc.setScore(newScore);
            doc.setCreatedAt(LocalDateTime.now());
            postRatingRepository.save(doc);

            // 重新计算均分: (总分 + 新分) / (总人数 + 1)
            double newTotalScore = totalScore + newScore;
            double newAvg = newTotalScore / (currentCount + 1);

            // 更新 Post
            updatePostRating(postId, newAvg, 1); // count 增量为 1
        }
    }

    // 处理评论点赞
    private void handleCommentLike(InteractionEvent event) {
        String commentId = event.getTargetId();
        Long userId = event.getUserId();

        if ("ADD".equals(event.getAction())) {
            // 1. 幂等性检查
            if (!commentLikeRepository.existsByUserIdAndCommentId(userId, commentId)) {
                // 2. 插入流水记录
                CommentLikeDoc doc = new CommentLikeDoc();
                doc.setUserId(userId);
                doc.setCommentId(commentId);
                doc.setCreatedAt(LocalDateTime.now());
                commentLikeRepository.save(doc);

                // 3. 原子更新评论的点赞计数 (likeCount + 1)
                updateCommentCount(commentId, 1);
            }
        } else {
            // 1. 删除流水
            commentLikeRepository.deleteByUserIdAndCommentId(userId, commentId);
            // 2. 计数 - 1
            updateCommentCount(commentId, -1);
        }
    }

    // 通用：更新评论计数
    private void updateCommentCount(String commentId, int inc) {
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(commentId)),
                new Update().inc("likeCount", inc),
                CommentDoc.class
        );
    }

    // 通用：原子更新简单计数器
    private void updatePostCount(String postId, String field, int inc) {
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(postId)),
                new Update().inc(field, inc),
                PostDoc.class
        );
    }

    // 专用：更新评分数据
    private void updatePostRating(String postId, Double newAvg, int countInc) {
        Update update = new Update()
                .set("ratingAverage", newAvg); // 直接覆盖均分

        if (countInc != 0) {
            update.inc("ratingCount", countInc); // 原子增减人数
        }

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(postId)),
                update,
                PostDoc.class
        );
    }
}