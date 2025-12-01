package com.szu.afternoon3.platform.entity.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Document(collection = "user_follows")
// 核心索引1：联合唯一索引，防止重复关注同一个人
@CompoundIndex(name = "idx_follow_unique", def = "{'userId': 1, 'targetUserId': 1}", unique = true)
public class UserFollowDoc {
    @Id
    private String id;

    // 谁发起的关注 (粉丝 ID)
    @Indexed // 场景：查询“我关注了谁” -> 用于首页关注流
    private Long userId;

    // 被关注的人 (博主 ID)
    @Indexed // 场景：查询“谁关注了我” -> 用于粉丝列表
    private Long targetUserId;

    // --- 冗余字段 (NoSQL 特性) ---
    // 存入时把 User 表里的数据拷贝一份过来
    // 这样查粉丝列表时，直接从 Mongo 就能拿到头像，不用回查 SQL
    private String userNickname;
    private String userAvatar;

    private String targetUserNickname;
    private String targetUserAvatar;

    private LocalDateTime createdAt;
}