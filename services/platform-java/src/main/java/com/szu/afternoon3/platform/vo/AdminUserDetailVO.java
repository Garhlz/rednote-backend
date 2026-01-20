package com.szu.afternoon3.platform.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AdminUserDetailVO {
    // ==========================================
    // 1. 基础信息 (PostgreSQL)
    // ==========================================
    private Long id;
    private String email;
    private String nickname;
    private String avatar;
    private String bio;
    private Integer gender; // 0:保密, 1:男, 2:女
    private String region;
    private LocalDateTime registerTime;
    private Integer status; // 账号状态 1:正常 0:封禁

    // ==========================================
    // 2. 社区活跃度 (主动数据 - 他做了什么)
    // ==========================================
    private Long postCount;          // 发布帖子数
    private Long followCount;        // 关注人数

    private Long givenLikeCount;     // 发出的点赞数 (他赞了别人多少次)
    private Long givenCollectCount;  // 发出的收藏数
    private Long givenCommentCount;  // 发出的评论数 (他评论了别人多少次) [新增]
    private Long givenRateCount;     // 发出的评分数 (他给多少帖子打过分) [新增]

    // ==========================================
    // 3. 社区影响力 (被动数据 - 别人对他做了什么)
    // ==========================================
    private Long fanCount;             // 粉丝数

    private Long receivedLikeCount;    // 获得的赞总数 (所有帖子获赞之和)
    private Long receivedCollectCount; // 获得的收藏总数
    private Long receivedCommentCount; // 获得的评论总数 (所有帖子评论之和) [新增]

    private Double avgPostScore;       // 帖子平均得分 (他所有帖子的评分均值)
}