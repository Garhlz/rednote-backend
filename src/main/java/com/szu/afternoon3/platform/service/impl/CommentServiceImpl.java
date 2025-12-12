package com.szu.afternoon3.platform.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.szu.afternoon3.platform.common.UserContext;
import com.szu.afternoon3.platform.config.RabbitConfig;
import com.szu.afternoon3.platform.dto.CommentCreateDTO;
import com.szu.afternoon3.platform.entity.User;
import com.szu.afternoon3.platform.entity.mongo.CommentDoc;
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
    @Transactional(rollbackFor = Exception.class) // 只是为了 User 表读取一致性，Mongo 不受控
    public void createComment(CommentCreateDTO dto) {
        Long currentUserId = UserContext.getUserId();
        User user = userMapper.selectById(currentUserId);

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
                .orElseThrow(() -> new AppException(ResultCode.RESOURCE_NOT_FOUND));
            
            // 2. 确定 rootId (保持两层结构)
            // 如果 parent 是一级评论(parentId=null)，那它就是 root
            // 如果 parent 是二级评论，那它的 parentId 才是 root
            String rootId = parent.getParentId() == null ? parent.getId() : parent.getParentId();
            doc.setParentId(rootId);

            // 3. 设置 "回复 @某某"
            doc.setReplyToUserId(parent.getUserId());
            doc.setReplyToUserNickname(parent.getUserNickname());

            // 4. 【关键】原子更新一级评论的 replyCount + 1
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
        event.setContent(doc.getContent());

        // 填充用于通知的字段 (需要你在 createComment 里先查好 post 信息)
        // event.setPostAuthorId(post.getUserId());
        event.setReplyToUserId(doc.getReplyToUserId());
        event.setParentId(doc.getParentId());

        // 路由键: comment.create
        rabbitTemplate.convertAndSend(RabbitConfig.PLATFORM_EXCHANGE, "comment.create", event);
    }

    @Override
    public Map<String, Object> getRootComments(String postId, Integer page, Integer size) {
        Long currentUserId = UserContext.getUserId();
        
        // 1. 分页查询一级评论
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt")); // 或者按 likeCount 热度排
        Page<CommentDoc> rootPage = commentRepository.findByPostIdAndParentIdIsNull(postId, pageable);
        List<CommentDoc> roots = rootPage.getContent();

        if (CollUtil.isEmpty(roots)) {
            return Map.of("records", List.of(), "total", 0);
        }

        // 2. 批量查询子评论预览 (解决 N+1 问题)
        // 目标：为每个 root 查出最新的 3 条 child
        // 简单做法：循环查 (N次查询)。如果 QPS 不高可以接受。
        // 高级做法：使用 Mongo Aggregation 的 $lookup 或 $graphLookup (较复杂)。
        // 这种场景下，为了开发效率，先用循环查前 3 条即可，Mongo 查单表 ID 索引很快。
        
        List<String> rootIds = roots.stream().map(CommentDoc::getId).toList();
        
        // 3. 批量查询点赞状态 (我是否给这些评论点过赞)
        Set<String> likedCommentIds = new HashSet<>();
        if (currentUserId != null) {
            // 这里假设你有 CommentLikeRepository.findByUserIdAndCommentIdIn...
            // 否则就循环查 exists (性能稍差)
        }

        // 4. 组装 VO
        List<CommentVO> voList = roots.stream().map(root -> {
            CommentVO vo = convertToVO(root, currentUserId);

            // TODO改成赞最多的
            // 查询 3 条子评论做预览
            if (root.getReplyCount() > 0) {
                PageRequest previewPage = PageRequest.of(0, 3, Sort.by(Sort.Direction.ASC, "createdAt"));
                List<CommentDoc> children = commentRepository.findByParentId(root.getId(), previewPage).getContent();
                
                List<CommentVO> childVOs = children.stream()
                    .map(child -> convertToVO(child, currentUserId))
                    .collect(Collectors.toList());
                vo.setChildComments(childVOs);
            } else {
                vo.setChildComments(new ArrayList<>());
            }
            return vo;
        }).collect(Collectors.toList());

        return Map.of("records", voList, "total", rootPage.getTotalElements());
    }

    @Override
    public Map<String, Object> getSubComments(String rootCommentId, Integer page, Integer size) {
        // 点击 "展开更多" 时调用
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.ASC, "createdAt"));
        Page<CommentDoc> childPage = commentRepository.findByParentId(rootCommentId, pageable);
        
        Long currentUserId = UserContext.getUserId();
        List<CommentVO> voList = childPage.getContent().stream()
            .map(doc -> convertToVO(doc, currentUserId))
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

    // 辅助转换方法
    private CommentVO convertToVO(CommentDoc doc, Long currentUserId) {
        CommentVO vo = new CommentVO();
        vo.setId(doc.getId());
        vo.setContent(doc.getContent());
        vo.setCreatedAt(doc.getCreatedAt().toString()); // 格式化自己处理
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

        // 检查点赞状态
        if (currentUserId != null) {
             boolean isLiked = commentLikeRepository.existsByUserIdAndCommentId(currentUserId, doc.getId());
             vo.setIsLiked(isLiked);
        } else {
            vo.setIsLiked(false);
        }
        
        return vo;
    }
}