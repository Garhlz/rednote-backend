package com.szu.afternoon3.platform.vo;

import lombok.Data;

@Data
public class AdminPostStatVO {
    private String id;
    private String title;
    private String cover;
    
    // 统计数据
    private Integer viewCount;    // 浏览量
    private Integer likeCount;    // 点赞量
    private Integer commentCount; // 评论量
    private Integer collectCount;
    private Double ratingAverage;
    
    // 作者信息
    private String userNickname;
    private String createdAt;
}