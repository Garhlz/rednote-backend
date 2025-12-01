package com.szu.afternoon3.platform.vo;

import lombok.Data;

@Data
public class LoginVO {
    private String token;
    private Boolean isNewUser;
    private Boolean hasPassword;
    private UserInfo userInfo;
}
