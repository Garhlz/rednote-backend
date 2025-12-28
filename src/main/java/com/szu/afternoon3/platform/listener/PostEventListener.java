package com.szu.afternoon3.platform.listener;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.szu.afternoon3.platform.config.RabbitConfig;
import com.szu.afternoon3.platform.entity.User;
import com.szu.afternoon3.platform.entity.mongo.CommentDoc;
import com.szu.afternoon3.platform.entity.mongo.NotificationDoc;
import com.szu.afternoon3.platform.entity.mongo.PostAuditLogDoc;
import com.szu.afternoon3.platform.entity.mongo.PostDoc;
import com.szu.afternoon3.platform.enums.NotificationType;
import com.szu.afternoon3.platform.event.PostCreateEvent;
import com.szu.afternoon3.platform.event.PostDeleteEvent;
import com.szu.afternoon3.platform.event.PostUpdateEvent;
import com.szu.afternoon3.platform.mapper.UserMapper;
import com.szu.afternoon3.platform.repository.*;
import com.szu.afternoon3.platform.service.NotificationService;
import com.szu.afternoon3.platform.service.impl.AiServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 帖子相关事件监听器
 * 统一监听 platform.post.queue
 */
@Component
@Slf4j
// 确保 RabbitConfig 中 QUEUE_POST = "platform.post.queue"
@RabbitListener(queues = RabbitConfig.QUEUE_POST)
public class PostEventListener {

    @Autowired private PostLikeRepository postLikeRepository;
    @Autowired private PostCollectRepository postCollectRepository;
    @Autowired private CommentRepository commentRepository;
    @Autowired private PostRatingRepository postRatingRepository;

    @Autowired private AiServiceImpl aiService;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private UserMapper userMapper;
    @Autowired private NotificationService notificationService; // 注入通知服务

    @Value("${ai.bot.user-id}")
    private Long botUserId;

    /**
     * 处理发帖：AI 自动评论
     */
    @RabbitHandler
    public void handlePostCreate(PostCreateEvent event) {
        log.info("RabbitMQ 收到发帖事件: {}", event.getId());
        try {
            handleAutoComment(event);
        } catch (Exception e) {
            log.error("AI 自动评论失败", e);
        }
    }

    /**
     * 处理修帖：(暂留空)
     */
    @RabbitHandler
    public void handlePostUpdate(PostUpdateEvent event) {
        log.debug("RabbitMQ 收到修帖事件: {}", event.getPostId());
    }

    /**
     * 处理删帖：数据清理 + 管理员操作通知
     */
    @RabbitHandler
    public void handlePostDelete(PostDeleteEvent event) {
        String postId = event.getPostId();
        log.info("RabbitMQ 收到删帖事件: postId={}, operatorId={}", postId, event.getOperatorId());

        // ----------------------------------------------------
        // 1. 业务数据清理 (原有逻辑)
        // ----------------------------------------------------
        try {
            postLikeRepository.deleteByPostId(postId);
            postCollectRepository.deleteByPostId(postId);
            postRatingRepository.deleteByPostId(postId);
            commentRepository.deleteByPostId(postId); // 直接删除关联评论
            log.info("删帖关联数据清理完成");
        } catch (Exception e) {
            log.error("删帖数据清理失败", e);
        }

        // ----------------------------------------------------
        // 2. 管理员删除的特殊处理 (新增逻辑)
        // ----------------------------------------------------
        if (event.isAdminOp()) {
            handleAdminDeleteLogAndNotify(event);
        }
    }

    // --- Private Methods ---

    private void handleAutoComment(PostCreateEvent event) {
        String summary = aiService.generatePostSummary(event.getUserId(), event.getTitle(), event.getContent(), event.getImages(), event.getVideo());
        if (StrUtil.isBlank(summary)) return;

        User botUser = userMapper.selectById(botUserId);
        if (botUser == null) return;

        CommentDoc comment = new CommentDoc();
        comment.setPostId(event.getId());
        comment.setUserId(botUserId);
        comment.setUserNickname(botUser.getNickname());
        comment.setUserAvatar(botUser.getAvatar());
        comment.setContent(summary);
        comment.setCreatedAt(LocalDateTime.now());
        comment.setLikeCount(0);
        comment.setReplyCount(0);

        commentRepository.save(comment);

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(event.getId())),
                new Update().inc("commentCount", 1),
                PostDoc.class
        );
    }

    private void handleAdminDeleteLogAndNotify(PostDeleteEvent event) {
        // A. 发送违规通知
        NotificationDoc note = new NotificationDoc();
        note.setReceiverId(event.getAuthorId());
        note.setSenderId(0L);
        note.setSenderNickname("映记安全中心");
        note.setSenderAvatar("https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/default_avatar.jpg");
        note.setType(NotificationType.SYSTEM_POST_DELETE);

        String reason = event.getReason() != null ? event.getReason() : "违反社区规范";
        note.setTargetPreview("您的笔记《" + event.getPostTitle() + "》已被移除。原因：" + reason);
        note.setTargetId(event.getPostId());

        notificationService.save(note);

        // B. 记录操作日志
        try {
            PostAuditLogDoc auditLog = new PostAuditLogDoc();
            auditLog.setPostId(event.getPostId());
            auditLog.setPostTitle(event.getPostTitle());
            auditLog.setOperatorId(event.getOperatorId());
             auditLog.setOperatorName(event.getOperatorName());
            auditLog.setAuditStatus(3); // 3=强制删除
            auditLog.setRejectReason(event.getReason());
            auditLog.setCreatedAt(LocalDateTime.now());

            mongoTemplate.save(auditLog);
            log.info("管理员删帖日志记录成功");
        } catch (Exception e) {
            log.error("保存管理员删帖日志失败", e);
        }
    }
}