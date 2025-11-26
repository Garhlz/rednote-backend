package com.szu.afternoon3.platform.DTO;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WechatLoginDTO {
    @NotBlank(message = "Code不能为空") // 需要 validation 依赖
    private String code;
}