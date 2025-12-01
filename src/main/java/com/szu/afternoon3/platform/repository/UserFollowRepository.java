package com.szu.afternoon3.platform.repository;

import com.szu.afternoon3.platform.entity.mongo.UserFollowDoc;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserFollowRepository extends MongoRepository<UserFollowDoc, String> {
    // 1. 检查关注状态 (用于前端显示“关注/已关注”按钮)
    boolean existsByUserIdAndTargetUserId(Long userId, Long targetUserId);

    // 2. 取关 (删除记录)
    void deleteByUserIdAndTargetUserId(Long userId, Long targetUserId);

    // 3. 获取我关注的人的ID列表 (核心：用于首页“关注”流的筛选)
    // 只需要返回 targetUserId 字段即可，节省流量
    @Query(value = "{ 'userId': ?0 }", fields = "{ 'targetUserId': 1 }")
    List<UserFollowDoc> findFollowingIds(Long userId);

    // 4. 统计 (可选，虽然一般建议在 User 表冗余计数，但初期可以直接 count)
    // 但是users表位于pg部分中
    long countByUserId(Long userId);       // 我的关注数
    long countByTargetUserId(Long targetUserId); // 我的粉丝数
}