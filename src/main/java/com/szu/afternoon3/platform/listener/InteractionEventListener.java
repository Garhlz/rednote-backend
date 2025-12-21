package com.szu.afternoon3.platform.listener;

import cn.hutool.core.util.StrUtil;
import com.szu.afternoon3.platform.config.RabbitConfig;
import com.szu.afternoon3.platform.entity.User;
import com.szu.afternoon3.platform.entity.mongo.*;
import com.szu.afternoon3.platform.enums.NotificationType;
import com.szu.afternoon3.platform.event.InteractionEvent;
import com.szu.afternoon3.platform.mapper.UserMapper;
import com.szu.afternoon3.platform.repository.*;
import com.szu.afternoon3.platform.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.ScriptType;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
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
    private CommentRepository commentRepository;
    @Autowired
    private CommentLikeRepository commentLikeRepository;
    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ElasticsearchOperations elasticsearchOperations;
    /**
     * 监听 Interaction 队列
     * 自动反序列化 JSON 为 InteractionEvent 对象
     */
    @RabbitListener(queues = RabbitConfig.QUEUE_INTERACTION)
    public void handleInteraction(InteractionEvent event) {
        log.debug("RabbitMQ 收到交互消息: {}", event);
        try {
            switch (event.getType()) {
                case "LIKE":
                    handleLike(event);
                    if ("ADD".equals(event.getAction())) {
                        sendPostNotification(event, NotificationType.LIKE_POST);
                    }
                    break;
                case "COLLECT":
                    handleCollect(event);
                    if ("ADD".equals(event.getAction())) {
                        sendPostNotification(event, NotificationType.COLLECT_POST);
                    }
                    break;
                case "RATE":
                    handleRate(event);
                    sendPostNotification(event, NotificationType.RATE_POST);
                    break;
                case "FOLLOW":
                    if ("ADD".equals(event.getAction())) {
                        sendFollowNotification(event);
                    }
                    break;

                case "COMMENT_LIKE":
                    handleCommentLike(event);
                    if ("ADD".equals(event.getAction())) {
                        sendCommentLikeNotification(event);
                    }
                    break;
            }
        } catch (Exception e) {
            log.error("交互消息处理失败: ", e);
            // 可以在这里抛出异常以触发重试，或者记录死信队列
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

                updateESPostCount(postId, "likeCount", 1);
            }
        } else {
            postLikeRepository.deleteByUserIdAndPostId(userId, postId);
            updatePostCount(postId, "likeCount", -1);
            updateESPostCount(postId, "likeCount", -1);
        }
    }

    /**
     * 使用脚本进行原子更新，避免并发覆盖，且效率最高
     */
    private void updateESPostCount(String postId, String fieldName, int delta) {
        // 1. 准备脚本
        // 你的脚本逻辑很好，考虑了字段为 null 的情况
        String scriptSource = "if (ctx._source." + fieldName + " == null) { " +
                "   ctx._source." + fieldName + " = params.delta; " +
                "} else { " +
                "   ctx._source." + fieldName + " += params.delta; " +
                "}";

        Map<String, Object> params = new HashMap<>();
        params.put("delta", delta);

        // 2. 构建更新查询
        UpdateQuery updateQuery = UpdateQuery.builder(postId)
                .withScript(scriptSource)
                .withScriptType(ScriptType.INLINE) // 【关键修正】必须显式指定脚本类型为内联
                .withParams(params)
                .withLang("painless")
                .withRetryOnConflict(3)
                .build();

        // 3. 执行更新
        try {
            elasticsearchOperations.update(updateQuery, IndexCoordinates.of("post_index"));
            log.debug("✅ ES Count Updated: postId={}, field={}, delta={}", postId, fieldName, delta);
        } catch (Exception e) {
            log.error("❌ Failed to update post count in ES for postId: {}", postId, e);
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

    // --- 评论点赞处理逻辑 ---
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

    // 辅助方法：更新评论表中的计数
    private void updateCommentCount(String commentId, int inc) {
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(commentId)),
                new Update().inc("likeCount", inc),
                CommentDoc.class
        );
    }

    // --- 发送评论点赞通知 ---
    private void sendCommentLikeNotification(InteractionEvent event) {
        String commentId = event.getTargetId();
        Long senderId = event.getUserId();

        // 1. 查评论信息 (为了获取评论作者)
        CommentDoc comment = commentRepository.findById(commentId).orElse(null);
        if (comment == null) return;

        // 2. 如果是自己给自己点赞，不发通知
        if (comment.getUserId().equals(senderId)) return;

        // 3. 查发送者信息
        User sender = userMapper.selectById(senderId);
        if (sender == null) return;

        // 4. 构建通知
        NotificationDoc doc = new NotificationDoc();
        doc.setReceiverId(comment.getUserId()); // 发给评论作者

        doc.setSenderId(senderId);
        doc.setSenderNickname(sender.getNickname());
        doc.setSenderAvatar(sender.getAvatar());

        doc.setType(NotificationType.LIKE_COMMENT); // 类型：赞了评论

        // 关键：targetId 存postId还是commentId？
        // 建议存 commentId，或者存 postId。
        // 为了方便前端跳转到帖子详情并定位评论，通常存 postId 更方便，或者存 commentId 但前端有能力反查。
        // 这里我们沿用 NotificationDoc 的约定：COMMENT/REPLY 类型存 postId。
        // 但对于 LIKE_COMMENT，targetId 存 postId 更实用。
        doc.setTargetId(comment.getPostId());

        // 摘要：显示被点赞的评论内容
        doc.setTargetPreview(StrUtil.subPre(comment.getContent(), 50));

        notificationService.save(doc);
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

    /**
     * 通用：发送帖子相关通知 (点赞/收藏/评分)
     */
    private void sendPostNotification(InteractionEvent event, NotificationType type) {
        String postId = event.getTargetId();
        Long senderId = event.getUserId();

        // 1. 查帖子
        PostDoc post = postRepository.findById(postId).orElse(null);
        if (post == null) return;

        // 2. 如果是自己给自己点赞，不发通知
        if (post.getUserId().equals(senderId)) return;

        // 3. 查发送者信息 (PostgreSQL)
        User sender = userMapper.selectById(senderId);
        if (sender == null) return;

        // 4. 构建通知
        NotificationDoc doc = new NotificationDoc();
        doc.setReceiverId(post.getUserId()); // 发给作者

        doc.setSenderId(sender.getId());
        doc.setSenderNickname(sender.getNickname());
        doc.setSenderAvatar(sender.getAvatar());

        doc.setType(type);
        doc.setTargetId(postId);

        // 摘要：优先用标题，没有标题截取内容
        String preview = StrUtil.isNotBlank(post.getTitle()) ? post.getTitle() : StrUtil.subPre(post.getContent(), 20);
        doc.setTargetPreview(preview);

        notificationService.save(doc);
    }

    // 发送关注通知
    private void sendFollowNotification(InteractionEvent event) {
        Long senderId = event.getUserId();
        String targetUserIdStr = event.getTargetId();

        // 1. 解析被关注者的ID
        long receiverId;
        try {
            receiverId = Long.parseLong(targetUserIdStr);
        } catch (NumberFormatException e) {
            log.warn("关注通知目标ID格式错误: {}", targetUserIdStr);
            return;
        }

        // 2. 查发起者信息 (粉丝的信息)
        User sender = userMapper.selectById(senderId);
        if (sender == null) return;

        // 3. 构建通知
        NotificationDoc doc = new NotificationDoc();
        doc.setReceiverId(receiverId); // 通知的接收者 = 被关注的人

        doc.setSenderId(sender.getId());
        doc.setSenderNickname(sender.getNickname());
        doc.setSenderAvatar(sender.getAvatar());

        doc.setType(NotificationType.FOLLOW);
        doc.setTargetId(String.valueOf(senderId)); // 关联的目标ID就是粉丝的ID，点击可能跳到粉丝主页
        doc.setTargetPreview("关注了你"); // 简单文案

        // 4. 保存
        notificationService.save(doc);
    }
}

