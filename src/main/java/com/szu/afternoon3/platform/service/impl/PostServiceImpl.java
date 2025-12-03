package com.szu.afternoon3.platform.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.szu.afternoon3.platform.common.UserContext;
import com.szu.afternoon3.platform.entity.mongo.PostCollectDoc;
import com.szu.afternoon3.platform.entity.mongo.PostDoc;
import com.szu.afternoon3.platform.entity.mongo.PostLikeDoc;
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
        // 1. 处理分页参数 (Spring Data 是从 0 开始)
        int pageNum = (page == null || page < 1) ? 0 : page - 1;
        int pageSize = (size == null || size < 1) ? 20 : size;

        // 默认按时间倒序
        Pageable pageable = PageRequest.of(pageNum, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<PostDoc> postDocPage;

        // 场景 A: 标签精确筛选 (点击了某个标签)
        if (StrUtil.isNotBlank(tag)) {
            postDocPage = postRepository.findByTagsContainingAndIsDeleted(tag, 0, pageable);
        }
        // 场景 B: 关注流
        else if ("follow".equalsIgnoreCase(tab)) {
            Long currentUserId = UserContext.getUserId();
            if (currentUserId == null) {
                throw new AppException(ResultCode.UNAUTHORIZED);
            }
            List<UserFollowDoc> follows = userFollowRepository.findFollowingIds(currentUserId);
            if (CollUtil.isEmpty(follows)) {
                postDocPage = Page.empty(pageable);
            } else {
                List<Long> targetIds = follows.stream()
                        .map(UserFollowDoc::getTargetUserId)
                        .collect(Collectors.toList());
                postDocPage = postRepository.findByUserIdInAndStatusAndIsDeleted(targetIds, 1, 0, pageable);
            }
        }
        // 场景 C: 推荐流 (默认)
        else {
            postDocPage = postRepository.findByStatusAndIsDeleted(1, 0, pageable);
        }

        return buildResultMap(postDocPage);
    }

    // 因为text index对于中文支持不友好，还是换回了正则
    @Override
    public Map<String, Object> searchPosts(String keyword, Integer page, Integer size) {
        int pageNum = (page == null || page < 1) ? 0 : page - 1;
        int pageSize = (size == null || size < 1) ? 20 : size;

        // 1. 如果关键词为空，返回空列表
        if (StrUtil.isBlank(keyword)) {
            Pageable pageable = PageRequest.of(pageNum, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
            return buildResultMap(Page.empty(pageable));
        }

        // 2. 关键词转义，防止正则报错
        String safeKeyword = java.util.regex.Pattern.quote(keyword);
        String regex = ".*" + safeKeyword + ".*";

        // 3. 构建查询 Query
        Query query = new Query();

        // 基础条件：未删除、已发布
        query.addCriteria(Criteria.where("isDeleted").is(0));
        query.addCriteria(Criteria.where("status").is(1));

        // 核心匹配逻辑：(标题 包含 OR 内容 包含 OR 标签 包含)
        // 注意：这里无法像 Text Index 那样自动计算 score 权重，
        // 但对于大作业来说，能搜出来比搜得准更重要。
        query.addCriteria(new Criteria().orOperator(
                Criteria.where("title").regex(regex, "i"),
                Criteria.where("content").regex(regex, "i"),
                Criteria.where("tags").regex(regex, "i")
        ));

        // 4. 排序：按时间倒序（搜索结果通常也希望看新的）
        // 如果想实现“匹配度排序”，在不使用搜索引擎(ES)的情况下比较复杂，
        // 简单的做法是先按时间排，解决 90% 的需求。
        Pageable pageable = PageRequest.of(pageNum, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        query.with(pageable);

        // 5. 执行查询
        long total = mongoTemplate.count(query, PostDoc.class);
        List<PostDoc> list = mongoTemplate.find(query, PostDoc.class);

        // 6. 封装结果
        Page<PostDoc> postDocPage = new PageImpl<>(list, pageable, total);
        return buildResultMap(postDocPage);
    }

    @Override
    public PostVO getPostDetail(String postId) {
        PostDoc doc = postRepository.findById(postId).orElse(null);

        // 1. 基础判空
        if (doc == null || (doc.getIsDeleted() != null && doc.getIsDeleted() == 1)) {
            throw new AppException(ResultCode.RESOURCE_NOT_FOUND);
        }

        // 2. 权限校验：如果不是发布状态
        if (doc.getStatus() != null && doc.getStatus() != 1) {
            // 获取当前登录用户
            Long currentUserId = UserContext.getUserId();
            // 如果当前没人登录，或者登录的人不是作者，则抛出异常
            // 意味着：作者本人可以看自己 审核中(0) 或 审核失败(2) 的帖子
            if (currentUserId == null || !currentUserId.equals(doc.getUserId())) {
                throw new AppException(ResultCode.RESOURCE_NOT_FOUND);
            }
        }

        return convertToVO(doc, true);
    }

    @Override
    public List<String> getSearchSuggestions(String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return new ArrayList<>();
        }

        // --- 核心中文匹配逻辑 ---

        // 1. 转义关键词中的特殊字符
        String safeKeyword = java.util.regex.Pattern.quote(keyword);

        // 2. 定义正则规则 (包含匹配)
        String regex = ".*" + safeKeyword + ".*";

        // 3. 构建查询
        Query query = new Query();
        query.addCriteria(Criteria.where("isDeleted").is(0));
        query.addCriteria(Criteria.where("status").is(1));

        // 4. OR 查询
        query.addCriteria(new Criteria().orOperator(
                Criteria.where("tags").regex(regex, "i"),
                Criteria.where("title").regex(regex, "i")
        ));

        // 5. 优化查询字段
        query.limit(20);
        query.fields().include("title").include("tags");

        // 6. 执行查询
        List<PostDoc> docs = mongoTemplate.find(query, PostDoc.class);

        // 7. 数据清洗与排序
        Set<String> suggestions = new LinkedHashSet<>();

        // 策略：优先展示匹配到的【标签】
        for (PostDoc doc : docs) {
            if (CollUtil.isNotEmpty(doc.getTags())) {
                for (String tag : doc.getTags()) {
                    // 【修改点 1】: 包含关键词 且 不完全等于关键词
                    if (StrUtil.contains(tag, keyword) && !StrUtil.equals(tag, keyword)) {
                        suggestions.add(tag);
                    }
                }
            }
        }

        // 策略：其次展示匹配到的【标题】
        for (PostDoc doc : docs) {
            String title = doc.getTitle();
            // 【修改点 2】: 包含关键词 且 不完全等于关键词
            if (StrUtil.contains(title, keyword) && !StrUtil.equals(title, keyword)) {
                // 这里暂不做截断
                suggestions.add(title);
            }

            if (suggestions.size() >= 15) {
                break;
            }
        }

        return new ArrayList<>(suggestions);
    }
    // --- Private Methods ---

    // 1. 修改 buildResultMap 方法
    private Map<String, Object> buildResultMap(Page<PostDoc> pageData) {
        List<PostDoc> postDocs = pageData.getContent();

        // --- 批量查询优化开始 ---

        // 准备三个 Set 用于 O(1) 快速查找
        Set<String> likedPostIds = new HashSet<>();
        Set<String> collectedPostIds = new HashSet<>();
        Set<Long> followedUserIds = new HashSet<>();

        Long currentUserId = UserContext.getUserId();

        // 只有登录用户才需要查这些状态
        if (currentUserId != null && CollUtil.isNotEmpty(postDocs)) {
            // A. 收集本页所有的 postId
            List<String> postIds = postDocs.stream().map(PostDoc::getId).collect(Collectors.toList());
            // B. 收集本页所有的 authorId (去重)
            List<Long> authorIds = postDocs.stream().map(PostDoc::getUserId).distinct().collect(Collectors.toList());

            // C. 批量查询点赞状态
            // 查出这批帖子中，我点赞过哪些
            List<PostLikeDoc> likes = postLikeRepository.findByUserIdAndPostIdIn(currentUserId, postIds);
            likedPostIds = likes.stream().map(PostLikeDoc::getPostId).collect(Collectors.toSet());

            // D. 批量查询收藏状态
            List<PostCollectDoc> collects = postCollectRepository.findByUserIdAndPostIdIn(currentUserId, postIds);
            collectedPostIds = collects.stream().map(PostCollectDoc::getPostId).collect(Collectors.toSet());

            // E. 批量查询关注状态
            List<UserFollowDoc> follows = userFollowRepository.findByUserIdAndTargetUserIdIn(currentUserId, authorIds);
            followedUserIds = follows.stream().map(UserFollowDoc::getTargetUserId).collect(Collectors.toSet());
        }

        // --- 批量查询优化结束 ---

        // 2. 转换 VO，传入刚才查好的 Set
        // 注意：这里需要配合修改下面的 convertToVO 方法，让它支持传入状态
        Set<String> finalLiked = likedPostIds;
        Set<String> finalCollected = collectedPostIds;
        Set<Long> finalFollowed = followedUserIds;

        List<PostVO> records = postDocs.stream()
                .map(doc -> convertToVO(doc, false, finalLiked, finalCollected, finalFollowed))
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", pageData.getTotalElements());
        result.put("current", pageData.getNumber() + 1);
        result.put("size", pageData.getSize());
        return result;
    }
    // 3. 重载 convertToVO 方法 (用于列表页，接受批量查询的结果)
    private PostVO convertToVO(PostDoc doc, boolean isDetail,
                               Set<String> likedSet,
                               Set<String> collectedSet,
                               Set<Long> followedSet) {
        // 先调用基础转换逻辑 (复用代码)
        PostVO vo = baseConvertToVO(doc, isDetail);

        // 直接从内存 Set 中判断，不再查库
        vo.setIsLiked(likedSet.contains(doc.getId()));
        vo.setIsCollected(collectedSet.contains(doc.getId()));
        vo.setIsFollowed(followedSet.contains(doc.getUserId()));

        return vo;
    }
    // 4. 原有的 convertToVO 方法 (用于详情页，单条查询)
    // 详情页本身就只查一次，不需要批量优化，保留原有逻辑即可，或者调用 base
    private PostVO convertToVO(PostDoc doc, boolean isDetail) {
        PostVO vo = baseConvertToVO(doc, isDetail);

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

    // 5. 抽取公共的基础转换逻辑 (避免代码重复)
    private PostVO baseConvertToVO(PostDoc doc, boolean isDetail) {
        PostVO vo = new PostVO();
        vo.setId(doc.getId());

        UserInfo author = new UserInfo();
        author.setUserId(String.valueOf(doc.getUserId()));
        author.setNickname(doc.getUserNickname());
        author.setAvatar(doc.getUserAvatar());
        vo.setAuthor(author);

        vo.setTitle(doc.getTitle());

        if (isDetail) {
            vo.setContent(doc.getContent());
        } else {
            vo.setContent(StrUtil.subPre(doc.getContent(), 50));
        }
        vo.setTags(doc.getTags());

        vo.setType(doc.getType());

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
        vo.setImages(images);
        vo.setVideos(videos);

        vo.setLikeCount(doc.getLikeCount());
        vo.setCollectCount(doc.getCollectCount());
        vo.setCommentCount(doc.getCommentCount());

        if (doc.getCreatedAt() != null) {
            vo.setCreatedAt(DateUtil.format(doc.getCreatedAt(), "yyyy-MM-dd HH:mm:ss"));
        }

        return vo;
    }
}