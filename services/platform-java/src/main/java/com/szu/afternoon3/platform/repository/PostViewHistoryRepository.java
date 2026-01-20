package com.szu.afternoon3.platform.repository;

import com.szu.afternoon3.platform.entity.mongo.PostViewHistoryDoc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PostViewHistoryRepository extends MongoRepository<PostViewHistoryDoc, String> {

    // 查找记录以便更新时间 (Upsert逻辑)
    Optional<PostViewHistoryDoc> findByUserIdAndPostId(Long userId, String postId);

    // 分页查询我的浏览历史
    Page<PostViewHistoryDoc> findByUserId(Long userId, Pageable pageable);
}