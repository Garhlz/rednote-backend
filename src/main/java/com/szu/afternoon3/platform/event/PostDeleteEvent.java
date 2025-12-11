package com.szu.afternoon3.platform.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
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