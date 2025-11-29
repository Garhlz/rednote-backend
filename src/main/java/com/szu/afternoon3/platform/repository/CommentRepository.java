package com.szu.afternoon3.platform.repository;

import com.szu.afternoon3.platform.entity.mongo.CommentDoc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends MongoRepository<CommentDoc, String> {

    // 1. 查询某个帖子下的所有“一级评论” (parentId 为 null)
    // 通常评论区是按时间倒序或者热度排序，通过 Pageable 控制
    Page<CommentDoc> findByPostIdAndParentIdIsNull(String postId, Pageable pageable);

    // 2. 查询某条父评论下的所有“子回复”
    // 子回复通常比较少，一般不需要分页，直接返回 List 即可；如果特别多也建议分页
    List<CommentDoc> findByParentId(String parentId);

    // 3. 统计帖子下的评论总数 (可选，如果 PostDoc 里维护了 count 就不需要这个)
    long countByPostId(String postId);

    // 4. 删除帖子时，级联删除该帖子的所有评论
    void deleteByPostId(String postId);
}