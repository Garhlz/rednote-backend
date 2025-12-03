package com.szu.afternoon3.platform.vo;

import lombok.Data;

import java.util.List;

@Data
public class PostVO {
    private String id;           // 帖子ID
    // 作者信息 (聚合展示用)
    private UserInfo author;   // { userId, nickname, avatar }

    private String title;
    private String content;      // 列表页只返回前50字摘要，详情页返回全部
    private Integer type;        // 0:图文, 1:视频
    private List<String> images; // 列表页可能只返回封面(第1张)，详情页返回所有
    private List<String> videos; // 视频列表
    // TODO 如何确定图片/视频的顺序
    // 交互计数
    private Integer likeCount;
    private Integer collectCount;
    private Integer commentCount;

    // 💡 状态字段 (前端用于渲染红心/高亮)
    private Boolean isLiked;     // 我是否点赞
    private Boolean isCollected; // 我是否收藏
    private Boolean isFollowed;  // 我是否关注了作者

    private String createdAt;    // 格式化后的时间

}