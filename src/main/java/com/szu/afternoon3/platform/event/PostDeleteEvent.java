package com.szu.afternoon3.platform.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PostDeleteEvent {
    /**
     * 被删除的帖子ID
     */
    private String postId;

    /**
     * 操作者的ID (可能是作者，也可能是管理员)
     */
    private Long operatorId;
}