package com.szu.afternoon3.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CommentCreateDTO {
    @NotBlank(message = "帖子ID不能为空")
    private String postId;

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 1000, message = "评论内容不能超过1000字")
    private String content;

    /**
     * 父评论ID (可选)
     * 1. 如果是回复帖子(一级评论)，此字段不传或为null
     * 2. 如果是回复某条评论(二级评论)，此字段必传
     */
    private String parentId;
}