package com.szu.afternoon3.platform.repository;

import com.szu.afternoon3.platform.entity.mongo.PostRatingDoc;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

@Repository
public interface PostRatingRepository extends MongoRepository<PostRatingDoc, String> {

    /**
     * 查询某用户对某帖子的评分记录
     * 用于判断是“新增评分”还是“修改评分”
     */
    Optional<PostRatingDoc> findByUserIdAndPostId(Long userId, String postId);

    // 分页查询某人的评分记录
    Page<PostRatingDoc> findByUserId(Long userId, Pageable pageable);
}