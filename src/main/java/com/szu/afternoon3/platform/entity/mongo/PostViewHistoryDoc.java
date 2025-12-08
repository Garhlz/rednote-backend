package com.szu.afternoon3.platform.entity.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Document(collection = "post_view_histories")
// 联合唯一索引：同一个用户对同一个帖子，只保留一条最新的浏览记录
@CompoundIndex(name = "idx_user_post_unique", def = "{'userId': 1, 'postId': 1}", unique = true)
public class PostViewHistoryDoc {
    @Id
    private String id;

    @Indexed
    private Long userId;

    private String postId;

    // 浏览时间 (用于排序)
    private LocalDateTime viewTime;
}