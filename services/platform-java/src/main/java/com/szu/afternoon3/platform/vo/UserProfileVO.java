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

    // --- 【新增】统计数据 ---
    private Long followCount;        // 我关注的人数
    private Long fanCount;           // 关注我的人数 (粉丝数)
    private Long receivedLikeCount;  // 我获得的总点赞数
}
