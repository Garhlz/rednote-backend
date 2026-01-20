package com.szu.afternoon3.platform.enums;

public enum NotificationType {
    LIKE_POST,      // 赞了帖子
    COLLECT_POST,   // 收藏了帖子
    RATE_POST,      // 评分
    LIKE_COMMENT,   // 赞了评论
    FOLLOW,         // 关注
    COMMENT,        // 评论了帖子
    REPLY,          // 回复了评论
    SYSTEM,          // 系统通知
    SYSTEM_AUDIT_PASS,   // 审核通过
    SYSTEM_AUDIT_REJECT,  // 审核拒绝
    SYSTEM_POST_DELETE  // 帖子删除通知
}