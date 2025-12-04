package com.szu.afternoon3.platform.entity.mongo;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
@Data
@Document(collection = "comment_likes")
// 索引：用户对同一条评论只能赞一次
@CompoundIndex(name = "idx_user_comment_unique", def = "{'userId': 1, 'commentId': 1}", unique = true)
public class CommentLikeDoc {
    @Id
    private String id;

    @Indexed
    private Long userId;

    @Indexed
    private String commentId; // 关联 CommentDoc

    private LocalDateTime createdAt;
}