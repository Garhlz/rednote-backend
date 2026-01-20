package com.szu.afternoon3.platform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminUserDeleteDTO {
    // 必填，用于记录日志或通知用户
    @NotBlank(message = "删除原因不能为空")
    private String reason;
}