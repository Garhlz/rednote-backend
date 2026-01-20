package com.szu.afternoon3.platform.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminPostAuditDTO {
    @NotNull(message = "审核状态不能为空")
    private Integer status; // 1:通过, 2:拒绝

    private String reason; // 拒绝理由（选填）
}