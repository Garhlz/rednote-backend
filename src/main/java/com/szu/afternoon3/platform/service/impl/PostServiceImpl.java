package com.szu.afternoon3.platform.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.szu.afternoon3.platform.common.UserContext;
import com.szu.afternoon3.platform.entity.mongo.PostDoc;
import com.szu.afternoon3.platform.entity.mongo.UserFollowDoc;
import com.szu.afternoon3.platform.exception.AppException;
import com.szu.afternoon3.platform.exception.ResultCode;
import com.szu.afternoon3.platform.repository.PostCollectRepository;
import com.szu.afternoon3.platform.repository.PostLikeRepository;
import com.szu.afternoon3.platform.repository.PostRepository;
import com.szu.afternoon3.platform.repository.UserFollowRepository;
import com.szu.afternoon3.platform.service.PostService;
import com.szu.afternoon3.platform.vo.PostVO;
import com.szu.afternoon3.platform.vo.UserInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PostServiceImpl implements PostService {

    @Autowired
    private PostRepository postRepository;
    @Autowired
    private UserFollowRepository userFollowRepository;
    @Autowired
    private PostLikeRepository postLikeRepository;
    @Autowired
    private PostCollectRepository postCollectRepository;

    @Autowired
    private MongoTemplate mongoTemplate;
    @Override
    public Map<String, Object> getPostList(Integer page, Integer size, String tab, String tag) {
        // 1. 处理分页参数 (Spring Data 是从 0 开始，前端是从 1 开始)
        int pageNum = (page == null || page < 1) ? 0 : page - 1;
        int pageSize = (size == null || size < 1) ? 20 : size;

        // 按创建时间倒序
        Pageable pageable = PageRequest.of(pageNum, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        // 2. 执行查询
        Page<PostDoc> postDocPage;

        // 场景 A: 标签筛选 (优先级最高)
        if (StrUtil.isNotBlank(tag)) {
            postDocPage = postRepository.findByTagsContainingAndIsDeleted(tag, 0, pageable);
        }
        // 场景 B: 关注流
        else if ("follow".equalsIgnoreCase(tab)) {
            Long currentUserId = UserContext.getUserId();

            // 如果未登录却请求关注流，抛出 401 异常
            if (currentUserId == null) {
                throw new AppException(ResultCode.UNAUTHORIZED);
            }

            // 1. 先查我关注的人的ID列表
            List<UserFollowDoc> follows = userFollowRepository.findFollowingIds(currentUserId);
            if (CollUtil.isEmpty(follows)) {
                postDocPage = Page.empty(pageable);
            } else {
                List<Long> targetIds = follows.stream()
                        .map(UserFollowDoc::getTargetUserId)
                        .collect(Collectors.toList());
                // 2. 查这些人发的、状态正常的帖子
                postDocPage = postRepository.findByUserIdInAndStatusAndIsDeleted(targetIds, 1, 0, pageable);
            }
        }
        // 场景 C: 推荐流 (默认)
        else {
            // 查询所有已发布(status=1)且未删除(isDeleted=0)的帖子
            postDocPage = postRepository.findByStatusAndIsDeleted(1, 0, pageable);
        }

        // 3. 调用统一的构建方法返回
        return buildResultMap(postDocPage);
    }

    @Override
    public Map<String, Object> searchPosts(String keyword, Integer page, Integer size) {
        // 1. 分页
        int pageNum = (page == null || page < 1) ? 0 : page - 1;
        int pageSize = (size == null || size < 1) ? 20 : size;
        Pageable pageable = PageRequest.of(pageNum, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        // 2. 构建查询
        // 文本搜索条件
        TextCriteria textCriteria = TextCriteria.forDefaultLanguage().matching(keyword);

        Query query = TextQuery.queryText(textCriteria)
                .sortByScore() // 按匹配度排序，或者用 pageable 的时间排序
                .addCriteria(Criteria.where("isDeleted").is(0)) // 过滤已删除
                .addCriteria(Criteria.where("status").is(1))    // 过滤未发布
                .with(pageable);

        // 3. 执行查询
        long total = mongoTemplate.count(query, PostDoc.class);
        List<PostDoc> list = mongoTemplate.find(query, PostDoc.class);

        // 4. 手动封装 Page 对象以便复用 buildResultMap
        Page<PostDoc> postDocPage = new PageImpl<>(list, pageable, total);

        return buildResultMap(postDocPage);
    }

    /**
     * [新增] 获取帖子详情实现
     */
    @Override
    public PostVO getPostDetail(String postId) {
        // 1. 查询数据库
        PostDoc doc = postRepository.findById(postId).orElse(null);

        // 2. 校验是否存在或已删除
        // TODO 如果你是作者本人(currentUserId == doc.userId)，可以看审核中的贴，这里暂简化为只能看已发布
        if (doc == null || doc.getIsDeleted() == 1 || doc.getStatus() != 1) {
            throw new AppException(ResultCode.RESOURCE_NOT_FOUND);
        }

        // TODO: 可以在此处增加浏览量(viewCount)逻辑

        // 3. 转换为 VO (isDetail = true，表示保留完整内容)
        return convertToVO(doc, true);
    }

    /**
     * [重构] 统一封装返回 Map，避免代码重复
     * 将 Page<PostDoc> 转换为 API 要求的 Map 结构
     */
    private Map<String, Object> buildResultMap(Page<PostDoc> pageData) {
        // 转换 Entity -> VO
        List<PostVO> records = pageData.getContent().stream()
                // [修改] 列表页调用时，isDetail = false
                .map(doc -> convertToVO(doc, false))
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", pageData.getTotalElements());
        result.put("current", pageData.getNumber() + 1); // 还原为 1-based
        result.put("size", pageData.getSize());

        return result;
    }

    /**
     * 将 PostDoc 转为 PostVO，并填充当前用户的交互状态
     * [修改] 增加 isDetail 参数，控制是否截断正文
     */
    private PostVO convertToVO(PostDoc doc, boolean isDetail) {
        PostVO vo = new PostVO();
        vo.setId(doc.getId());

        // 作者信息
        UserInfo author = new UserInfo();
        author.setUserId(String.valueOf(doc.getUserId()));
        author.setNickname(doc.getUserNickname());
        author.setAvatar(doc.getUserAvatar());
        vo.setAuthor(author);

        vo.setTitle(doc.getTitle());

        // [修改] 根据场景处理内容长度
        if (isDetail) {
            vo.setContent(doc.getContent()); // 详情页：完整内容
        } else {
            // 列表页只截取前 50 字
            vo.setContent(StrUtil.subPre(doc.getContent(), 50));
        }

        vo.setType(doc.getType());

        // 资源分离 (Image / Video)
        List<String> images = new ArrayList<>();
        List<String> videos = new ArrayList<>();
        if (doc.getResources() != null) {
            for (PostDoc.Resource res : doc.getResources()) {
                if ("VIDEO".equalsIgnoreCase(res.getType())) {
                    videos.add(res.getUrl());
                } else {
                    images.add(res.getUrl());
                }
            }
        }
        // 列表页通常只需要第一张图作为封面(前端逻辑)，这里后端还是返回全部，或者也可以在这里根据 isDetail 做截取
        vo.setImages(images);
        vo.setVideos(videos);

        // 计数
        vo.setLikeCount(doc.getLikeCount());
        vo.setCollectCount(doc.getCollectCount());
        vo.setCommentCount(doc.getCommentCount());

        // 时间格式化
        if (doc.getCreatedAt() != null) {
            vo.setCreatedAt(DateUtil.format(doc.getCreatedAt(), "yyyy-MM-dd HH:mm:ss"));
        }

        // --- 交互状态 (是否点赞/收藏/关注) ---
        Long currentUserId = UserContext.getUserId();
        if (currentUserId != null) {
            vo.setIsLiked(postLikeRepository.existsByUserIdAndPostId(currentUserId, doc.getId()));
            vo.setIsCollected(postCollectRepository.existsByUserIdAndPostId(currentUserId, doc.getId()));
            vo.setIsFollowed(userFollowRepository.existsByUserIdAndTargetUserId(currentUserId, doc.getUserId()));
        } else {
            vo.setIsLiked(false);
            vo.setIsCollected(false);
            vo.setIsFollowed(false);
        }

        return vo;
    }
}