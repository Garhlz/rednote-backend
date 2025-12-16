package com.szu.afternoon3.platform.vo;

import lombok.Data;

@Data
public class TagStatVO {
    private String tagName; // 标签名
    private Long totalViews; // 该标签下的总浏览量
    private Integer postCount; // 该标签下的帖子数
}