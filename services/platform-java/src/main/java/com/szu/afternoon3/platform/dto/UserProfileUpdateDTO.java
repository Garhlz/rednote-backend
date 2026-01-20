package com.szu.afternoon3.platform.dto;

import lombok.Data;

@Data
public class UserProfileUpdateDTO {
    private String nickname;
    private String avatar;
    private String bio;
    private Integer gender;   // 0, 1, 2
    private String birthday;  // yyyy-MM-dd
    private String region;
}
