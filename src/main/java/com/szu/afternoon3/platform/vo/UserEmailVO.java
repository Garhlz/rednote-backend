package com.szu.afternoon3.platform.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserEmailVO {
    // 明确的字段定义，Swagger 可以自动读取到
    private String email;
}