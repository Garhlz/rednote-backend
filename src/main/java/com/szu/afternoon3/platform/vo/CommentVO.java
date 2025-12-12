package com.szu.afternoon3.platform.vo;

import lombok.Data;
import java.util.List;

@Data
public class CommentVO {
    private String id;
    private String content;
    private String createdAt;
    
    // 评论者信息
    private UserInfo author; // {userId, nickname, avatar}

    // 交互数据
    private Integer likeCount;
    private Boolean isLiked; // 我是否点赞

    // --- 子评论专用 ---
    // 如果是子评论，这里显示 "回复 @某某"
    private UserInfo replyToUser; 

    // --- 一级评论专用 ---
    // 该评论下的子回复总数
    private Integer replyCount;
    // 子回复预览列表 (默认只返前 3 条，点击展开再调列表接口)
    private List<CommentVO> childComments; 
}