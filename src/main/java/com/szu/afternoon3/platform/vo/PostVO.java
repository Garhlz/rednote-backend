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

    /**
     * 0: 图文 (多张图)
     * 1: 视频 (单视频)
     * 2: 纯文字 (本质上是带背景图的图文，但标记出来方便后续做特殊样式)
     */
    private Integer type;

    // 列表页：只用 cover 展示
    private String cover;

    // 详情页：
    // type=0/2: 前端读这个列表渲染轮播图/单图
    private List<String> images;

    // type=1: 前端读这个字段渲染播放器
    private String video;
    // 交互计数
    private Integer likeCount;
    private Integer collectCount;
    private Integer commentCount;

    // 状态字段 (前端用于渲染红心/高亮)
    private Boolean isLiked;     // 我是否点赞
    private Boolean isCollected; // 我是否收藏
    private Boolean isFollowed;  // 我是否关注了作者


    // 【新增】评分统计
    private Double ratingAverage; // 平均分 (e.g. 4.5)
    private Integer ratingCount;  // 评分人数 (e.g. 102)

    // 【新增】我的评分状态
    private Double myScore;      // 我给出的评分 (若未评分则为 null 或 0.0)

    private String createdAt;    // 格式化后的时间
    // 标签列表
    private List<String> tags;

    private Integer coverWidth;

    private Integer coverHeight;
}