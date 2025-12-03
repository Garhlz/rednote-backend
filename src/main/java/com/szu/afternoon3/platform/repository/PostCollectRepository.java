package com.szu.afternoon3.platform.repository;

import com.szu.afternoon3.platform.entity.mongo.PostCollectDoc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PostCollectRepository extends MongoRepository<PostCollectDoc, String> {

    // 1. 检查是否存在 (用户是否收藏过该贴)
    // 这是一个非常高频的查询
    boolean existsByUserIdAndPostId(Long userId, String postId);

    // 2. 查询具体的收藏记录 (通常用于取消收藏时获取 ID，或者 exists 够用了直接 delete)
    Optional<PostCollectDoc> findByUserIdAndPostId(Long userId, String postId);

    // 3. 删除记录 (取消收藏)
    // Spring Data Mongo 的 deleteBy... 默认返回 void 或者删除的条数(Long/Integer)
    void deleteByUserIdAndPostId(Long userId, String postId);

    // 4. 查询某人收藏过的所有记录 (用于“我收藏的”列表)
    Page<PostCollectDoc> findByUserId(Long userId, Pageable pageable);

    // 5. 级联删除：如果帖子被删了，清理掉所有相关的收藏记录
    void deleteByPostId(String postId);

    // 【新增】批量查询
    List<PostCollectDoc> findByUserIdAndPostIdIn(Long userId, Collection<String> postIds);
}