package com.szu.afternoon3.platform.entity.mongo;

import com.szu.afternoon3.platform.enums.NotificationType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Document(collection = "notifications")
// 核心索引：加速轮询“我的未读消息数”
@CompoundIndex(name = "idx_receiver_read", def = "{'receiverId': 1, 'isRead': 1}")
public class NotificationDoc {
    @Id
    private String id;

    @Indexed
    private Long receiverId; // 接收者ID (谁收到通知)

    // --- 冗余发送者信息 ---
    private Long senderId;
    private String senderNickname;
    private String senderAvatar;

    /**
     * 修改为枚举类型
     * Spring Data MongoDB 默认会将其序列化为字符串 (e.g. "LIKE_POST")
     */
    private NotificationType type;

    // --- 目标信息 ---
    // 对于 COMMENT/REPLY，targetId 统一存 postId，方便前端点击通知直接跳转到帖子详情页
    private String targetId;

    // 内容摘要:
    // COMMENT/REPLY: 显示评论的具体内容
    // LIKE/COLLECT: 显示帖子标题
    private String targetPreview;

    private Boolean isRead = false; // 默认为未读

    private LocalDateTime createdAt = LocalDateTime.now();
}