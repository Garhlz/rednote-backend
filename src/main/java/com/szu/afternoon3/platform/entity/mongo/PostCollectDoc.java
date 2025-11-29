package com.szu.afternoon3.platform.entity.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Document(collection = "post_collects")
// 关键：联合唯一索引，防止重复收藏
@CompoundIndex(name = "idx_user_post_unique", def = "{'userId': 1, 'postId': 1}", unique = true)
public class PostCollectDoc {
    @Id
    private String id;

    private Long userId;
    private String postId;

    private LocalDateTime createdAt;
}