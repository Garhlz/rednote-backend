package com.szu.afternoon3.platform.entity.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.TextScore;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "posts")
public class PostDoc {
    @Id
    private String id;

    @Indexed // 加索引，方便查询某人的所有帖子
    private Long userId;

    // 冗余作者信息
    private String userNickname;
    private String userAvatar;

    // 标题和内容不需要 @TextIndexed 了，因为我们统一搜索 searchTerms
    private String title;
    private String content;

    @Indexed           // 保留普通索引，用于精确筛选 (getPostList)
    private List<String> tags;

    // 搜索专用字段
    // weight=5 表示匹配到这里的词，相关度得分很高
    @TextIndexed(weight = 5)
    private List<String> searchTerms;

    // 用于接收查询时的匹配分数 (不会存入数据库)
    @TextScore
    private Float score;

    // 统计数据
    // TODO 这里有浏览数量
    private Integer viewCount = 0;
    private Integer likeCount = 0;
    private Integer collectCount = 0;
    private Integer commentCount = 0;

    private Integer status; // 0:审核中, 1:发布, 2: 审核失败

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    private Integer isDeleted = 0;

    /**
     * 0: 图文 (多张图)
     * 1: 视频 (单视频)
     * 2: 纯文字 (本质上是带背景图的图文，但标记出来方便后续做特殊样式)
     */
    private Integer type;

    /**
     * 资源列表
     * type=0: 存多张图片 URL
     * type=1: 存【一个】视频 URL (约定 index 0 是视频)
     * type=2: 存【一个】由前端生成的图片 URL
     */
    private List<String> resources; // 简化：直接存 String List，不再用 Resource 内部类

    /**
     * 封面图 (冗余字段，方便列表查询)
     * type=0: resources[0]
     * type=1: 视频URL + 阿里云截帧参数
     * type=2: resources[0]
     */
    private String cover;

    // 平均分 (默认 0.0)
    // 前端展示时通常保留一位小数，如 4.5
    private Double ratingAverage = 0.0;

    // 评分总人数
    // 用于显示 "100人已评" 以及计算加权平均
    private Integer ratingCount = 0;

    // 封面图原始宽度 (px)
    private Integer coverWidth;
    // 封面图原始高度 (px)
    private Integer coverHeight;
}