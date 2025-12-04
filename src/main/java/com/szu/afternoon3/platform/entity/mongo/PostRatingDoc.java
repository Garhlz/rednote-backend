package com.szu.afternoon3.platform.entity.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Document(collection = "post_ratings")
// 关键：联合唯一索引，确保一个用户对一个帖子只能有一个评分
@CompoundIndex(name = "idx_user_post_rating_unique", def = "{'userId': 1, 'postId': 1}", unique = true)
public class PostRatingDoc {
    @Id
    private String id;

    @Indexed // 加速“查询某用户评过的分”
    private Long userId;

    @Indexed // 加速“计算某帖子的平均分”
    private String postId;

    // 评分值：0.5, 1.0, 1.5 ... 5.0
    // 使用 Double 存储，方便计算
    private Double score;

    private LocalDateTime createdAt;

    // 允许用户修改评分，所以记录更新时间
    private LocalDateTime updatedAt;
}