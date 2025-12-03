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

    @Override
    public Map<String, Object> searchPosts(String keyword, Integer page, Integer size) {
        int pageNum = (page == null || page < 1) ? 0 : page - 1;
        int pageSize = (size == null || size < 1) ? 20 : size;
        Pageable pageable = PageRequest.of(pageNum, pageSize); // 这里不用指定Sort，默认按相关度排序

        if (StrUtil.isBlank(keyword)) {
            return buildResultMap(Page.empty(pageable));
        }

        // 1. 构建全文搜索条件
        // 由于我们在 Entity 里给 tags 加了 @TextIndexed，这里会自动包含对 tags 的搜索
        TextCriteria textCriteria = TextCriteria.forDefaultLanguage().matching(keyword);

        // 2. 构建查询
        // 使用 TextQuery 以利用相关度评分 (score)
        Query query = TextQuery.queryText(textCriteria)
                .sortByScore() // 优先显示匹配度高的 (比如完全匹配标签的)
                .addCriteria(Criteria.where("isDeleted").is(0))
                .addCriteria(Criteria.where("status").is(1))
                .with(pageable);

        // 3. 执行查询
        long total = mongoTemplate.count(query, PostDoc.class);
        List<PostDoc> list = mongoTemplate.find(query, PostDoc.class);

        // 4. 封装结果
        Page<PostDoc> postDocPage = new PageImpl<>(list, pageable, total);
        return buildResultMap(postDocPage);
    }

    @Override
    public PostVO getPostDetail(String postId) {
        PostDoc doc = postRepository.findById(postId).orElse(null);

        // 校验是否存在、是否删除、是否发布
        if (doc == null || (doc.getIsDeleted() != null && doc.getIsDeleted() == 1) || (doc.getStatus() != null && doc.getStatus() != 1)) {
            // 这里有个小优化：如果是作者本人，允许看未发布的
            // if (!Objects.equals(doc.getUserId(), UserContext.getUserId())) { ... }
            throw new AppException(ResultCode.RESOURCE_NOT_FOUND);
        }

        return convertToVO(doc, true);
    }

    // --- Private Methods ---

    private Map<String, Object> buildResultMap(Page<PostDoc> pageData) {
        List<PostVO> records = pageData.getContent().stream()
                .map(doc -> convertToVO(doc, false)) // 列表模式
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", pageData.getTotalElements());
        result.put("current", pageData.getNumber() + 1);
        result.put("size", pageData.getSize());
        return result;
    }

    private PostVO convertToVO(PostDoc doc, boolean isDetail) {
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