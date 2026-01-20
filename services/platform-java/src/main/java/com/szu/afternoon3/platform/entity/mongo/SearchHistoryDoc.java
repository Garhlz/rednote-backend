package com.szu.afternoon3.platform.entity.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.IndexDirection;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Document(collection = "search_histories")
// 索引：userId + keyword 唯一，保证同一个词只存一次（再次搜索更新时间）
@CompoundIndex(name = "idx_user_keyword", def = "{'userId': 1, 'keyword': 1}", unique = true)
public class SearchHistoryDoc {
    @Id
    private String id;

    @Indexed
    private Long userId;

    private String keyword;

    @Indexed(direction = IndexDirection.DESCENDING) // 按时间倒序取最近的
    private LocalDateTime updatedAt;
}
