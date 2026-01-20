package com.szu.afternoon3.platform.vo;

import lombok.Data;

@Data
public class LoginVO {
    private Boolean isNewUser;
    private Boolean hasPassword;
    private UserInfo userInfo;

    // 腾讯云 IM 的签名，前端拿到后调用 TIM.login()
    private String userSig;

    // 【保持不变】前端用这个字段访问接口 (对应 Access Token)
    private String token;
    // 【新增】前端在 token 过期后，用这个来换新 token (对应 Refresh Token)
    private String refreshToken;
}
