package com.szu.afternoon3.platform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
public class UserPasswordResetDTO {
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email; // 唯一标识符

    @NotBlank(message = "验证码不能为空")
    private String code;  // 证明邮箱是你的

    @NotBlank(message = "新密码不能为空")
    @Length(min = 6, message = "密码长度不能少于6位")
    private String newPassword;
}