package com.szu.afternoon3.platform.entity.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

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

//    @TextIndexed(weight = 2) // 权重2：标题匹配
//    @Indexed
    private String title;

//    @TextIndexed(weight = 1) // 权重1：内容匹配
    private String content;

    private Integer type; // 0:图文, 1:视频

    private List<Resource> resources;

//    @TextIndexed(weight = 3) // 新增全文索引，用于模糊搜索 (searchPosts)
    @Indexed           // 保留普通索引，用于精确筛选 (getPostList)
    private List<String> tags;

    // 统计数据
    private Integer viewCount = 0;
    private Integer likeCount = 0;
    private Integer collectCount = 0;
    private Integer commentCount = 0;

    private Integer status; // 0:审核中, 1:发布, 2: 审核失败

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    private Integer isDeleted = 0;

    @Data
    public static class Resource {
        private String url;
        private String type; // IMAGE, VIDEO
    }
}