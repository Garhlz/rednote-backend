package com.szu.afternoon3.platform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AccountLoginDTO {
    @NotBlank(message = "账号不能为空")
    private String account;
    @NotBlank(message = "密码不能为空")
    private String password;
}