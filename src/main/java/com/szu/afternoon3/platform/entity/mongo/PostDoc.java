package com.szu.afternoon3.platform.entity.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
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

    // 冗余作者信息（避免列表页每次都查 Postgres）
    private String userNickname;
    private String userAvatar;

    private String title;
    private String content;
    private Integer type; // 0:图文, 1:视频

    // 资源列表：因为一般只有9张图，适合内嵌，不需要分表
    private List<Resource> resources;

    // 标签列表：一般几个标签，适合内嵌
    @Indexed // 方便按标签搜索
    private List<String> tags;

    // 统计数据（这是扁平化设计的关键：把计数和具体名单分开）
    // 具体的点赞人名单在 PostLikeDoc 里，这里只存总数
    private Integer viewCount = 0;
    private Integer likeCount = 0;
    private Integer collectCount = 0;
    private Integer commentCount = 0;

    private Integer status; // 0:审核中, 1:发布

    // [建议] 显式初始化，防止插入时为 null
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    private Integer isDeleted = 0;
    @Data
    public static class Resource {
        private String url;
        private String type; // IMAGE, VIDEO
    }
}