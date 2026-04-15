package com.szu.afternoon3.platform.listener;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.szu.afternoon3.platform.common.LogMdc;
import com.szu.afternoon3.platform.config.RabbitConfig;
import com.szu.afternoon3.platform.entity.User;
import com.szu.afternoon3.platform.entity.mongo.CommentDoc;
import com.szu.afternoon3.platform.entity.mongo.NotificationDoc;
import com.szu.afternoon3.platform.entity.mongo.PostAuditLogDoc;
import com.szu.afternoon3.platform.entity.mongo.PostDoc;
import com.szu.afternoon3.platform.enums.NotificationType;
import com.szu.afternoon3.platform.event.*;
import com.szu.afternoon3.platform.mapper.UserMapper;
import com.szu.afternoon3.platform.repository.*;
import com.szu.afternoon3.platform.grpc.NotificationRpcClient;
import com.szu.afternoon3.platform.service.impl.AiServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
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
    @Autowired private NotificationRpcClient notificationRpcClient; // 直接写入 notification-rpc

    @Value("${ai.bot.user-id}")
    private Long botUserId;

    /**
     * 处理发帖：AI 自动评论
     */
    @RabbitHandler
    public void handlePostCreate(PostCreateEvent event,
                                 @Header(name = "X-Request-Id", required = false) String requestId,
                                 @Header(name = AmqpHeaders.RECEIVED_ROUTING_KEY, required = false) String routingKey) {
        LogMdc.bindMqContext(requestId, routingKey, "platform-java");
        log.info("mq consume start routingKey={} postId={}", routingKey, event.getId());
        try {
            handleAutoComment(event);
        } catch (Exception e) {
            log.error("AI 自动评论失败", e);
        } finally {
            LogMdc.clear();
        }
    }

    /**
     * 处理修帖：(暂留空)
     */
    @RabbitHandler
    public void handlePostUpdate(PostUpdateEvent event,
                                 @Header(name = "X-Request-Id", required = false) String requestId,
                                 @Header(name = AmqpHeaders.RECEIVED_ROUTING_KEY, required = false) String routingKey) {
        LogMdc.bindMqContext(requestId, routingKey, "platform-java");
        log.debug("mq consume routingKey={} postId={}", routingKey, event.getPostId());
        LogMdc.clear();
    }

    /**
     * 处理删帖：数据清理 + 管理员操作通知
     */
    @RabbitHandler
    public void handlePostDelete(PostDeleteEvent event,
                                 @Header(name = "X-Request-Id", required = false) String requestId,
                                 @Header(name = AmqpHeaders.RECEIVED_ROUTING_KEY, required = false) String routingKey) {
        LogMdc.bindMqContext(requestId, routingKey, "platform-java");
        String postId = event.getPostId();
        log.info("mq consume start routingKey={} postId={} operatorId={}", routingKey, postId, event.getOperatorId());

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
        LogMdc.clear();
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

        notificationRpcClient.save(note);

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

    /**
     * 【新增】处理审核操作事件
     * 由于 PostEventListener 监听了 "post.#"，它会误收到 "post.audit" 消息。
     * 添加此方法是为了避免抛出 "No listener method found" 异常。
     */
    @RabbitHandler
    public void handlePostAudit(PostAuditEvent event,
                                @Header(name = "X-Request-Id", required = false) String requestId,
                                @Header(name = AmqpHeaders.RECEIVED_ROUTING_KEY, required = false) String routingKey) {
        LogMdc.bindMqContext(requestId, routingKey, "platform-java");
        // 这里留空即可，表示“我知道收到了，但我不需要处理，请确认消费成功”
        log.debug("mq consume ignore routingKey={} postId={}", routingKey, event.getPostId());
        LogMdc.clear();
    }

    /**
     * 【新增】处理审核通过事件
     * 同样是因为 "post.#" 路由键导致的误收。
     */
    @RabbitHandler
    public void handlePostAuditPass(PostAuditPassEvent event,
                                    @Header(name = "X-Request-Id", required = false) String requestId,
                                    @Header(name = AmqpHeaders.RECEIVED_ROUTING_KEY, required = false) String routingKey) {
        LogMdc.bindMqContext(requestId, routingKey, "platform-java");
        // 这里留空即可
        log.debug("mq consume ignore routingKey={} postId={}", routingKey, event.getId());

        // 扩展建议：
        // 如果未来想在审核通过后做一些业务（比如给作者加积分、触发某种奖励），
        // 就可以写在这里，而不需要去改 AdminService。
        LogMdc.clear();
    }
}
