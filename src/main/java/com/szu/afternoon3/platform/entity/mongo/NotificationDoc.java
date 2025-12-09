package com.szu.afternoon3.platform.entity.mongo;

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

    // --- 冗余发送者信息 (避免列表页 N+1 查询) ---
    private Long senderId;
    private String senderNickname;
    private String senderAvatar;

    /**
     * 通知类型:
     * LIKE_POST: 赞了你的帖子
     * COLLECT_POST: 收藏了你的帖子
     * RATE_POST: 给你的帖子评了分
     * LIKE_COMMENT: 赞了你的评论
     * FOLLOW: 关注了你
     * SYSTEM: 系统通知
     */
    private String type;

    // --- 目标信息 ---
    private String targetId;      // 关联的ID (帖子ID / 评论ID / 用户ID)
    private String targetPreview; // 内容摘要 (帖子标题/评论内容/对方昵称)

    private Boolean isRead = false; // 默认为未读
    
    private LocalDateTime createdAt = LocalDateTime.now();
}