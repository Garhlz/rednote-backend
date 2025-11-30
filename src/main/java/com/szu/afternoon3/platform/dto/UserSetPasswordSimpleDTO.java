package com.szu.afternoon3.platform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserSetPasswordSimpleDTO {
    @NotBlank(message = "密码不能为空")
    private String password;
}
