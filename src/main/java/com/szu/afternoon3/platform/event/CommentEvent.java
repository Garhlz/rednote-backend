package com.szu.afternoon3.platform.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentEvent {
    /**
     * 事件类型: CREATE, DELETE
     */
    private String type;

    private String commentId;
    private String postId;
    private Long userId; // 评论者ID
    private String userNickname;
    private String content; // 评论内容摘要（用于通知）

    // --- 用于通知的信息 ---
    private Long postAuthorId; // 帖子作者ID
    private Long replyToUserId; // 被回复的人ID (如果是回复评论)
    private String parentId; // 父评论ID
}