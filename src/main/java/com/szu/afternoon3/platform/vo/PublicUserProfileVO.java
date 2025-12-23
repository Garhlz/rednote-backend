package com.szu.afternoon3.platform.vo;

import lombok.Data;

@Data
public class PublicUserProfileVO {
    // --- 基础公开信息 ---
    private String userId;
    private String nickname;
    private String avatar;
    private String bio;
    private Integer gender;
    private String region;
    
    // --- 统计数据 ---
    private Long followCount;
    private Long fanCount;
    private Long receivedLikeCount;

    // --- 【新增】交互状态 ---
    // 当前登录用户是否关注了该用户
    private Boolean isFollowed; 
}