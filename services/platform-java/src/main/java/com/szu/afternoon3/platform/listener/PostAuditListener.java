package com.szu.afternoon3.platform.listener;
import com.szu.afternoon3.platform.config.RabbitConfig;
import com.szu.afternoon3.platform.entity.mongo.NotificationDoc;
import com.szu.afternoon3.platform.entity.mongo.PostAuditLogDoc;
import com.szu.afternoon3.platform.enums.NotificationType;
import com.szu.afternoon3.platform.event.PostAuditEvent;
import com.szu.afternoon3.platform.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
public class PostAuditListener {
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private MongoTemplate mongoTemplate;
    /**
     * 监听帖子审核事件
     * 队列名需要在 RabbitConfig 中绑定好，比如 QUEUE_NOTIFY
     */
    @RabbitListener(queues = RabbitConfig.QUEUE_NOTIFY_AUDIT)
    public void handleAuditEvent(PostAuditEvent event) {
        log.info("收到审核事件: {}", event);

        // ==========================================
        // 1. 【新增】保存审核记录流水 (Audit Log)
        // ==========================================
        try {
            PostAuditLogDoc logDoc = new PostAuditLogDoc();
            logDoc.setPostId(event.getPostId());
            logDoc.setPostTitle(event.getPostTitle());
            logDoc.setOperatorId(event.getOperatorId());
            logDoc.setOperatorName(event.getOperatorName());
            logDoc.setAuditStatus(event.getStatus());
            logDoc.setRejectReason(event.getReason());
            logDoc.setCreatedAt(LocalDateTime.now());

            mongoTemplate.save(logDoc);
            log.info("审核记录已落库: postId={}", event.getPostId());
        } catch (Exception e) {
            log.error("保存审核记录失败", e);
            // 记录失败不应该影响发送通知，所以 catch 住
        }

        // ==========================================
        // 2. 发送站内通知 (Notification) - 原有逻辑
        // ==========================================
        NotificationDoc note = new NotificationDoc();
        note.setReceiverId(event.getAuthorId());
        note.setSenderId(0L); // 0 代表系统
        note.setSenderNickname("映记系统助手");
        note.setSenderAvatar("https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/default_avatar.jpg");

        if (event.getStatus() == 1) {
            note.setType(NotificationType.SYSTEM_AUDIT_PASS);
            note.setTargetPreview("恭喜！你的笔记《" + event.getPostTitle() + "》已通过审核，快去看看吧！");
        } else {
            note.setType(NotificationType.SYSTEM_AUDIT_REJECT);
            String reason = event.getReason() != null ? event.getReason() : "内容不符合规范";
            note.setTargetPreview("很遗憾，你的笔记《" + event.getPostTitle() + "》未通过审核。原因：" + reason);
        }
        note.setTargetId(event.getPostId());

        notificationService.save(note);
    }
}
