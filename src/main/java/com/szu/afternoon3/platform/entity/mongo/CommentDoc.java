package com.szu.afternoon3.platform.entity.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Document(collection = "comments")
// 复合索引：通常查询某个帖子下的评论，并按时间排序
@CompoundIndex(name = "idx_post_time", def = "{'postId': 1, 'createdAt': -1}")
public class CommentDoc {
    @Id
    private String id;

    @Indexed
    private String postId; // 关联 PostDoc 的 id

    private Long userId;   // 评论者 ID
    private String userNickname; // 冗余
    private String userAvatar;   // 冗余

    private String content;

    @Indexed
    private String parentId; // 如果是回复某条评论，存父评论ID，否则为 null

    // 如果是回复，指向被回复的用户ID，方便前端显示 "回复 @某某"
    private Long replyToUserId;
    private String replyToUserNickname; // 冗余
    private String replyToUserAvatar;   // 冗余

    private LocalDateTime createdAt;

    // [新增] 点赞数
    private Integer likeCount = 0;
}