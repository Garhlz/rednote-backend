package com.szu.afternoon3.platform.vo;

import lombok.Data;

// 和UserInfo的唯一区别是没有email
@Data
public class SimpleUserVO {
    private String userId;
    private String nickname;
    private String avatar;
}