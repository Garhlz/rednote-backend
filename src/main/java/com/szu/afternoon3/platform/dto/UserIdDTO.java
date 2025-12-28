package com.szu.afternoon3.platform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserIdDTO {
    @NotBlank(message = "目标用户ID不能为空")
    private String targetUserId; // 对应前端传来的 targetUserId
}