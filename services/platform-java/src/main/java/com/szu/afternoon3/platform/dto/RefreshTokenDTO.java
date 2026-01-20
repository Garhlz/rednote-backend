package com.szu.afternoon3.platform.dto;

import lombok.Data;

@Data
public class RefreshTokenDTO {
    // 前端传来之前保存的 refreshToken
    private String refreshToken; 
}