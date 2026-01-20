package com.szu.afternoon3.platform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// 专门用于只传评论ID的场景
@Data
public class CommentIdDTO {
    @NotBlank(message = "评论ID不能为空")
    private String commentId;
}