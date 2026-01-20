package com.szu.afternoon3.platform.entity.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Document(collection = "comments")
@CompoundIndex(name = "idx_post_time", def = "{'postId': 1, 'createdAt': -1}")
public class CommentDoc {
    @Id
    private String id;

    @Indexed
    private String postId;

    private Long userId;
    private String userNickname;
    private String userAvatar;

    private String content;

    // 父评论ID：
    // 1. 如果是回复帖子，此字段为 null (一级评论)
    // 2. 如果是回复别人，此字段固定为所属的【一级评论 ID】(为了方便聚合查询)
    @Indexed
    private String parentId;

    // 冗余字段：真正被回复的人 (方便前端显示 "回复 @某某")
    private Long replyToUserId;
    private String replyToUserNickname;

    // 【新增】回复总数 (仅一级评论维护此字段，用于前端显示 "展开X条回复")
    private Integer replyCount = 0;

    // 点赞数
    private Integer likeCount = 0;

    private LocalDateTime createdAt;
}