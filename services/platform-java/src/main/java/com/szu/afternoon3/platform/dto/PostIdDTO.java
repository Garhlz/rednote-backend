package com.szu.afternoon3.platform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// 专门用于只传帖子ID的场景
@Data
public class PostIdDTO {
    @NotBlank(message = "帖子ID不能为空")
    private String postId;
}