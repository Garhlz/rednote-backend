package com.szu.afternoon3.platform.repository;

import com.szu.afternoon3.platform.entity.mongo.CommentLikeDoc;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface CommentLikeRepository extends MongoRepository<CommentLikeDoc, String> {

    // 1. 检查是否点赞
    boolean existsByUserIdAndCommentId(Long userId, String commentId);

    // 2. 取消点赞 (删除)
    void deleteByUserIdAndCommentId(Long userId, String commentId);

    // 3. 级联删除：删除评论时，删除该评论的所有点赞
    void deleteByCommentId(String commentId);

    void deleteByCommentIdIn(List<String> commentIds);

    // 【新增】批量查询某用户对一组评论的点赞记录
    List<CommentLikeDoc> findByUserIdAndCommentIdIn(Long userId, Collection<String> commentIds);
}