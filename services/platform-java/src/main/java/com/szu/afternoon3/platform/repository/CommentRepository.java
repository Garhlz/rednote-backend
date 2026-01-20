package com.szu.afternoon3.platform.repository;

import com.szu.afternoon3.platform.entity.mongo.CommentDoc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends MongoRepository<CommentDoc, String> {

    // 1. 查询一级评论列表 (parentId 为 null)
    Page<CommentDoc> findByPostIdAndParentIdIsNull(String postId, Pageable pageable);

    // 2. 查询某条一级评论下的子回复 (支持分页)
    // 对应接口: /api/comment/list?parentId=xxx
    Page<CommentDoc> findByParentId(String parentId, Pageable pageable);

    void deleteByPostId(String postId);

    Page<CommentDoc> findByUserId(Long userId, Pageable pageable);
}