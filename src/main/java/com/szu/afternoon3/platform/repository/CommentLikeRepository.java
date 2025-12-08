package com.szu.afternoon3.platform.repository;

import com.szu.afternoon3.platform.entity.mongo.CommentLikeDoc;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentLikeRepository extends MongoRepository<CommentLikeDoc, String> {

    // 1. 检查是否点赞
    boolean existsByUserIdAndCommentId(Long userId, String commentId);

    // 2. 取消点赞 (删除)
    void deleteByUserIdAndCommentId(Long userId, String commentId);

    // 3. 级联删除：删除评论时，删除该评论的所有点赞
    void deleteByCommentId(String commentId);
}