package com.szu.afternoon3.platform.vo;

import lombok.Data;

@Data
public class MyCommentVO {
    private String id;           // 评论ID (用于可能的删除操作)
    private String content;      // 评论缩略内容
    private String createdAt;    // 评论时间
    private Integer likeCount;   // 获赞数

    // --- 关联帖子信息 ---
    private String postId;       // 跳转用
    private String postCover;    // 帖子封面图
    private String postTitle;    // (可选) 帖子标题，提升体验
}