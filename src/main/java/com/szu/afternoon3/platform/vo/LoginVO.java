package com.szu.afternoon3.platform.vo;

import lombok.Data;

@Data
public class LoginVO {
    private String token;
    private Boolean isNewUser;
    private Boolean hasPassword;
    private UserInfo userInfo;

    // 【新增】腾讯云 IM 的签名，前端拿到后调用 TIM.login()
    private String userSig;
}
