package com.szu.afternoon3.platform.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.szu.afternoon3.platform.common.UserContext;
import com.szu.afternoon3.platform.config.RabbitConfig;
import com.szu.afternoon3.platform.dto.CommentCreateDTO;
import com.szu.afternoon3.platform.entity.User;
import com.szu.afternoon3.platform.entity.mongo.CommentDoc;
import com.szu.afternoon3.platform.entity.mongo.CommentLikeDoc;
import com.szu.afternoon3.platform.entity.mongo.PostDoc;
import com.szu.afternoon3.platform.event.CommentEvent;
import com.szu.afternoon3.platform.exception.AppException;
import com.szu.afternoon3.platform.enums.ResultCode;
import com.szu.afternoon3.platform.mapper.UserMapper;
import com.szu.afternoon3.platform.repository.CommentLikeRepository;
import com.szu.afternoon3.platform.repository.CommentRepository;
import com.szu.afternoon3.platform.repository.PostRepository;
import com.szu.afternoon3.platform.service.CommentService;
import com.szu.afternoon3.platform.vo.CommentVO;
import com.szu.afternoon3.platform.vo.UserInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CommentServiceImpl implements CommentService {

    @Autowired private CommentRepository commentRepository;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private UserMapper userMapper;
    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired
    private CommentLikeRepository commentLikeRepository; // 检查是否点赞
    @Autowired private PostRepository postRepository;
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createComment(CommentCreateDTO dto) {
        Long currentUserId = UserContext.getUserId();
        User user = userMapper.selectById(currentUserId);

        // 0. 【修复】先校验帖子是否存在，并获取帖子作者信息 (用于通知)
        PostDoc post = postRepository.findById(dto.getPostId())
                .orElseThrow(() -> new AppException(ResultCode.RESOURCE_NOT_FOUND, "帖子不存在"));

        CommentDoc doc = new CommentDoc();
        doc.setPostId(dto.getPostId());
        doc.setUserId(currentUserId);
        doc.setUserNickname(user.getNickname());
        doc.setUserAvatar(user.getAvatar());
        doc.setContent(dto.getContent());
        doc.setCreatedAt(LocalDateTime.now());
        doc.setLikeCount(0);
        doc.setReplyCount(0);

        // 处理回复逻辑
        if (StrUtil.isNotBlank(dto.getParentId())) {
            // 1. 查父评论
            CommentDoc parent = commentRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new AppException(ResultCode.RESOURCE_NOT_FOUND, "回复的评论不存在"));

            // 2. 确定 rootId (两层结构)
            String rootId = parent.getParentId() == null ? parent.getId() : parent.getParentId();
            doc.setParentId(rootId);

            // 3. 设置 "回复 @某某"
            doc.setReplyToUserId(parent.getUserId());
            doc.setReplyToUserNickname(parent.getUserNickname());

            // 4. 原子更新一级评论 replyCount
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("id").is(rootId)),
                    new Update().inc("replyCount", 1),
                    CommentDoc.class
            );
        } else {
            // 一级评论
            doc.setParentId(null);
        }

        commentRepository.save(doc);

        // 5. 【RabbitMQ】发送创建事件
        CommentEvent event = new CommentEvent();
        event.setType("CREATE");
        event.setCommentId(doc.getId());
        event.setPostId(doc.getPostId());
        event.setUserId(doc.getUserId());
        event.setUserNickname(doc.getUserNickname()); // 补充昵称，方便消费者用,最后需要存储
        event.setContent(doc.getContent());

        // 【核心修复】设置帖子作者ID，确保通知能发出去
        event.setPostAuthorId(post.getUserId());

        event.setReplyToUserId(doc.getReplyToUserId());
        event.setParentId(doc.getParentId());

        rabbitTemplate.convertAndSend(RabbitConfig.PLATFORM_EXCHANGE, "comment.create", event);
    }
    @Override
    public Map<String, Object> getRootComments(String postId, Integer page, Integer size) {
        Long currentUserId = UserContext.getUserId();

        // 1. 分页查询一级评论 (按热度或时间排序)
        // 这里默认按时间倒序，也可以改为 Sort.by(Sort.Direction.DESC, "likeCount")
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CommentDoc> rootPage = commentRepository.findByPostIdAndParentIdIsNull(postId, pageable);
        List<CommentDoc> roots = rootPage.getContent();

        if (CollUtil.isEmpty(roots)) {
            return Map.of("records", List.of(), "total", 0);
        }

        // --- 准备阶段：收集数据以解决 N+1 问题 ---

        // 2.1 收集一级评论 ID
        List<String> allCommentIdsToCheck = new ArrayList<>();
        roots.forEach(root -> allCommentIdsToCheck.add(root.getId()));

        // 2.2 预查询每个一级评论的子评论 (Top 3 热评)
        // Map<RootId, List<ChildDoc>>
        Map<String, List<CommentDoc>> childrenMap = new HashMap<>();

        for (CommentDoc root : roots) {
            if (root.getReplyCount() > 0) {
                // 【修改点】按点赞数倒序取前3条
                PageRequest previewPage = PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "likeCount"));
                List<CommentDoc> children = commentRepository.findByParentId(root.getId(), previewPage).getContent();

                childrenMap.put(root.getId(), children);

                // 将子评论 ID 也加入待检查列表
                children.forEach(child -> allCommentIdsToCheck.add(child.getId()));
            }
        }

        // 3. 批量查询点赞状态 (仅当用户登录时)
        Set<String> likedCommentIds = new HashSet<>();
        if (currentUserId != null && !allCommentIdsToCheck.isEmpty()) {
            List<CommentLikeDoc> likes = commentLikeRepository.findByUserIdAndCommentIdIn(currentUserId, allCommentIdsToCheck);
            likes.forEach(like -> likedCommentIds.add(like.getCommentId()));
        }

        // 4. 组装 VO
        List<CommentVO> voList = roots.stream().map(root -> {
            // 转换一级评论 (传入 likedSet)
            CommentVO rootVO = convertToVO(root, likedCommentIds);

            // 处理子评论预览
            List<CommentDoc> children = childrenMap.getOrDefault(root.getId(), Collections.emptyList());
            List<CommentVO> childVOs = children.stream()
                    .map(child -> convertToVO(child, likedCommentIds)) // 子评论也使用同一个 Set 判断点赞
                    .collect(Collectors.toList());

            rootVO.setChildComments(childVOs);
            return rootVO;
        }).collect(Collectors.toList());

        return Map.of("records", voList, "total", rootPage.getTotalElements());
    }

    @Override
    public Map<String, Object> getSubComments(String rootCommentId, Integer page, Integer size) {
        Long currentUserId = UserContext.getUserId();

        // 1. 分页查询子评论
        // 点击“展开”通常看全部回复，建议按时间正序（楼层模式），或者按热度倒序
        // 这里演示按时间正序
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.ASC, "createdAt"));
        Page<CommentDoc> childPage = commentRepository.findByParentId(rootCommentId, pageable);
        List<CommentDoc> children = childPage.getContent();

        if (CollUtil.isEmpty(children)) {
            return Map.of("records", List.of(), "total", 0);
        }

        // 2. 批量查询点赞状态
        Set<String> likedCommentIds = new HashSet<>();
        if (currentUserId != null) {
            List<String> ids = children.stream().map(CommentDoc::getId).collect(Collectors.toList());
            List<CommentLikeDoc> likes = commentLikeRepository.findByUserIdAndCommentIdIn(currentUserId, ids);
            likes.forEach(like -> likedCommentIds.add(like.getCommentId()));
        }

        // 3. 组装 VO
        List<CommentVO> voList = children.stream()
                .map(doc -> convertToVO(doc, likedCommentIds))
                .collect(Collectors.toList());

        return Map.of("records", voList, "total", childPage.getTotalElements());
    }

    @Override
    public void deleteComment(String commentId) {
        // 1. 查评论信息
        CommentDoc doc = commentRepository.findById(commentId).orElse(null);
        if (doc == null) return; // 已经被删了，直接返回

        // 2. 【核心修复】权限校验
        Long currentUserId = UserContext.getUserId();
        String role = UserContext.getRole(); // 获取角色

        // 2.1 或者是管理员
        boolean isAdmin = "ADMIN".equals(role);
        // 2.2 或者是评论发布者本人
        boolean isCommentAuthor = doc.getUserId().equals(currentUserId);

        // 2.3 或者是帖子作者 (有权删除自己帖子下的任何恶评)
        boolean isPostAuthor = false;
        // 为了查帖子作者，需要多查一次 Post 表。这在删除操作中是值得的安全性开销。
        PostDoc post = postRepository.findById(doc.getPostId()).orElse(null);
        if (post != null && post.getUserId().equals(currentUserId)) {
            isPostAuthor = true;
        }

        if (!isAdmin && !isCommentAuthor && !isPostAuthor) {
            log.warn("越权删除评论尝试: user={}, comment={}", currentUserId, commentId);
            throw new AppException(ResultCode.PERMISSION_DENIED, "无权删除该评论");
        }

        // 3. 物理删除
        commentRepository.deleteById(commentId);

        // 4. 【RabbitMQ】发送删除事件 (用于异步更新计数)
        CommentEvent event = new CommentEvent();
        event.setType("DELETE");
        event.setPostId(doc.getPostId());
        event.setCommentId(commentId);
        event.setParentId(doc.getParentId());

        rabbitTemplate.convertAndSend(RabbitConfig.PLATFORM_EXCHANGE, "comment.delete", event);
    }

    private CommentVO convertToVO(CommentDoc doc, Set<String> likedCommentIds) {
        CommentVO vo = new CommentVO();
        vo.setId(doc.getId());
        vo.setContent(doc.getContent());
        vo.setCreatedAt(doc.getCreatedAt().toString()); // 建议用 DateUtil 格式化一下
        vo.setLikeCount(doc.getLikeCount());
        vo.setReplyCount(doc.getReplyCount());

        UserInfo author = new UserInfo();
        author.setUserId(String.valueOf(doc.getUserId()));
        author.setNickname(doc.getUserNickname());
        author.setAvatar(doc.getUserAvatar());
        vo.setAuthor(author);

        if (doc.getReplyToUserId() != null) {
            UserInfo replyTo = new UserInfo();
            replyTo.setUserId(String.valueOf(doc.getReplyToUserId()));
            replyTo.setNickname(doc.getReplyToUserNickname());
            vo.setReplyToUser(replyTo);
        }

        // 直接从 Set 中判断，O(1) 复杂度，无需查库
        if (likedCommentIds != null) {
            vo.setIsLiked(likedCommentIds.contains(doc.getId()));
        } else {
            vo.setIsLiked(false);
        }

        return vo;
    }
}