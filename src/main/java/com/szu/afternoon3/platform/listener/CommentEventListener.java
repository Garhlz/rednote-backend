package com.szu.afternoon3.platform.listener;

import cn.hutool.core.util.StrUtil;
import com.szu.afternoon3.platform.config.RabbitConfig;
import com.szu.afternoon3.platform.entity.User;
import com.szu.afternoon3.platform.entity.mongo.CommentDoc;
import com.szu.afternoon3.platform.entity.mongo.NotificationDoc;
import com.szu.afternoon3.platform.entity.mongo.PostDoc;
import com.szu.afternoon3.platform.enums.NotificationType;
import com.szu.afternoon3.platform.event.CommentEvent;
import com.szu.afternoon3.platform.mapper.UserMapper;
import com.szu.afternoon3.platform.repository.CommentLikeRepository;
import com.szu.afternoon3.platform.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RabbitListener(queues = RabbitConfig.QUEUE_COMMENT)
public class CommentEventListener {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CommentLikeRepository commentLikeRepository;
    /**
     * 处理评论创建事件
     */
    @RabbitHandler
    public void handleCommentCreate(CommentEvent event) {
        if (!"CREATE".equals(event.getType())) return;

        log.info("RabbitMQ 处理评论创建: commentId={}", event.getCommentId());

        // 更新帖子总评论数 +1 (Atomic)
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(event.getPostId())),
                new Update().inc("commentCount", 1),
                PostDoc.class
        );

        // 二级评论对应的一级评论的评论数+1放在service中做了

        // 发送通知
        sendCommentNotification(event);
    }

    /**
     * 处理评论删除事件
     */
    @RabbitHandler
    public void handleCommentDelete(CommentEvent event) {
        if (!"DELETE".equals(event.getType())) return;

        String commentId = event.getCommentId();
        log.info("RabbitMQ 处理评论删除: commentId={}", commentId);

        // 1. 【新增】清理该评论的点赞记录
        // 对应 Repository 里的 void deleteByCommentId(String commentId);
        commentLikeRepository.deleteByCommentId(commentId);

        // 2. 更新帖子总评论数 -1
        // (注意：如果是级联删除帖子引发的，这一步其实是多余的，但在单删评论时是必须的)
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(event.getPostId()).and("commentCount").gt(0)),
                new Update().inc("commentCount", -1),
                PostDoc.class
        );

        // 3. 更新父评论回复数
        if (StrUtil.isNotBlank(event.getParentId())) {
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("id").is(event.getParentId()).and("replyCount").gt(0)),
                    new Update().inc("replyCount", -1),
                    CommentDoc.class
            );
        }
    }
    // --- 辅助方法：生成通知 ---
    private void sendCommentNotification(CommentEvent event) {
        Long senderId = event.getUserId(); // 评论者

        // 场景 A: 回复了某人 (二级评论) -> 类型 REPLY
        // event.getReplyToUserId() 在 DTO 转 Event 时被设置
        if (event.getReplyToUserId() != null) {
            // 如果是自己回复自己，不发通知
            if (senderId.equals(event.getReplyToUserId())) return;

            // 接收者是 被回复的人
            createNotify(senderId, event.getReplyToUserId(), NotificationType.REPLY, event);
        }
        // 场景 B: 直接评论帖子 (一级评论) -> 类型 COMMENT
        // 接收者是 帖子作者
        else if (event.getPostAuthorId() != null) {
            // 如果是帖子作者自己评论自己，不发通知
            if (senderId.equals(event.getPostAuthorId())) return;

            createNotify(senderId, event.getPostAuthorId(), NotificationType.COMMENT, event);
        }
    }

    private void createNotify(Long senderId, Long receiverId, NotificationType type, CommentEvent event) {
        // 1. 查发送者信息 (用于填充 NotificationDoc 的冗余字段)
        User sender = userMapper.selectById(senderId);
        if (sender == null) return;

        // 2. 构建通知对象
        NotificationDoc doc = new NotificationDoc();
        doc.setReceiverId(receiverId); // 接收者

        doc.setSenderId(senderId);
        doc.setSenderNickname(sender.getNickname());
        doc.setSenderAvatar(sender.getAvatar());

        doc.setType(type); // "COMMENT" or "REPLY

        // 关联ID：存 PostId。这样前端点击通知，可以直接跳到帖子详情页
        doc.setTargetId(event.getPostId());

        // 摘要：显示评论内容 (截取前 50 字)
        doc.setTargetPreview(StrUtil.subPre(event.getContent(), 50));

        // 3. 保存
        notificationService.save(doc);
    }
}