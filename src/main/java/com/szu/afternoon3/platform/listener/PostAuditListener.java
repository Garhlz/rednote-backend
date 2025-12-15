package com.szu.afternoon3.platform.listener;
import com.szu.afternoon3.platform.config.RabbitConfig;
import com.szu.afternoon3.platform.entity.mongo.NotificationDoc;
import com.szu.afternoon3.platform.enums.NotificationType;
import com.szu.afternoon3.platform.event.PostAuditEvent;
import com.szu.afternoon3.platform.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PostAuditListener {
    @Autowired
    private NotificationService notificationService;

    /**
     * 监听帖子审核事件
     * 队列名需要在 RabbitConfig 中绑定好，比如 QUEUE_NOTIFY
     */
    @RabbitListener(queues = RabbitConfig.QUEUE_NOTIFY_AUDIT) // 确保你在 Config 里定义了这个队列并绑定了 "post.audit"
    public void handleAuditEvent(PostAuditEvent event) {
        log.info("收到审核通知事件: {}", event);

        NotificationDoc note = new NotificationDoc();

        // 1. 设置接收者 (作者)
        note.setReceiverId(event.getAuthorId());

        // 2. 设置发送者 (系统管理员)
        // 我们可以约定 senderId = 0L 代表系统
        note.setSenderId(0L);
        note.setSenderNickname("映记系统助手");
        note.setSenderAvatar("https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/default_avatar.jpg"); // 设置一个官方头像URL

        // 3. 设置类型和内容
        if (event.getStatus() == 1) {
            note.setType(NotificationType.SYSTEM_AUDIT_PASS);
            // 摘要显示：恭喜！你的笔记《xxx》已通过审核
            note.setTargetPreview("恭喜！你的笔记《" + event.getPostTitle() + "》已通过审核，快去看看吧！");
        } else {
            note.setType(NotificationType.SYSTEM_AUDIT_REJECT);
            // 摘要显示：很遗憾... 原因：涉嫌违规
            String reason = event.getReason() != null ? event.getReason() : "内容不符合规范";
            note.setTargetPreview("很遗憾，你的笔记《" + event.getPostTitle() + "》未通过审核。原因：" + reason);
        }

        // 4. 设置跳转目标
        note.setTargetId(event.getPostId());

        // 5. 保存
        notificationService.save(note);
    }
}
