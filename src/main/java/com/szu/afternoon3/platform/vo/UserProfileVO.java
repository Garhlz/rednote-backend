package com.szu.afternoon3.platform.vo;

import lombok.Data;

@Data
public class UserProfileVO {
    private String userId;
    private String nickname;
    private String avatar;
    private String bio;
    private Integer gender; // 0:保密, 1:男, 2:女
    private String birthday; // yyyy-MM-dd
    private String region;
    private String email;
    private Boolean hasPassword;
}
