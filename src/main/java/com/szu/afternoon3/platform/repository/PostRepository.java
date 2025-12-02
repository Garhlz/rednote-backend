package com.szu.afternoon3.platform.repository;

import com.szu.afternoon3.platform.entity.mongo.PostDoc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PostRepository extends MongoRepository<PostDoc, String> {

    // 1. 查询某人的帖子列表 (支持分页)
    // 自动对应 MongoDB: db.posts.find({"userId": ?, "isDeleted": 0})
    Page<PostDoc> findByUserIdAndIsDeleted(Long userId, Integer isDeleted, Pageable pageable);

    // 2. 首页推荐流：查询所有状态正常的帖子 (支持分页)
    // status=1(已发布), isDeleted=0(未删除)
    Page<PostDoc> findByStatusAndIsDeleted(Integer status, Integer isDeleted, Pageable pageable);

    // 3. 标签搜索：查询包含特定标签的帖子
    // tags 是一个 List<String>，Spring Data 会自动处理 "包含" 逻辑
    Page<PostDoc> findByTagsContainingAndIsDeleted(String tag, Integer isDeleted, Pageable pageable);

    // 4. 模糊搜索标题
    // Like 对应正则查询，性能稍差，但在实验项目中完全可用
    Page<PostDoc> findByTitleLikeAndIsDeleted(String title, Integer isDeleted, Pageable pageable);

    // 5. 根据 ID 列表批量查询 (用于收藏列表回显)
    List<PostDoc> findAllById(Iterable<String> ids);

    // 6. 关注流：查询指定用户列表(关注的人)发布的帖子
    // SQL类似: SELECT * FROM posts WHERE user_id IN (1, 2, 3) AND status=1 AND is_deleted=0
    Page<PostDoc> findByUserIdInAndStatusAndIsDeleted(Collection<Long> userIds, Integer status, Integer isDeleted, Pageable pageable);
}