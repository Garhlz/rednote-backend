package com.szu.afternoon3.platform.vo;

import lombok.Data;

@Data
public class UserInfo {
    private String userId; // 转String防止前端精度丢失
    private String nickname;
    private String avatar;
    private String email;
}
