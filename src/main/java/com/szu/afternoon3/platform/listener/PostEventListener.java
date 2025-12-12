package com.szu.afternoon3.platform.listener;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.szu.afternoon3.platform.config.RabbitConfig;
import com.szu.afternoon3.platform.entity.User;
import com.szu.afternoon3.platform.entity.mongo.CommentDoc;
import com.szu.afternoon3.platform.entity.mongo.PostDoc;
import com.szu.afternoon3.platform.event.PostCreateEvent;
import com.szu.afternoon3.platform.event.PostDeleteEvent;
import com.szu.afternoon3.platform.event.PostUpdateEvent;
import com.szu.afternoon3.platform.mapper.UserMapper;
import com.szu.afternoon3.platform.repository.*;
import com.szu.afternoon3.platform.service.impl.AiServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 帖子相关事件监听器
 * 统一监听 platform.post.queue，利用 @RabbitHandler 分发不同类型的事件
 */
@Component
@Slf4j
// 假设你在 RabbitConfig 中增加了这个队列常量，值为 "platform.post.queue"
@RabbitListener(queues = "platform.post.queue")
public class PostEventListener {

    @Autowired
    private PostLikeRepository postLikeRepository;
    @Autowired
    private PostCollectRepository postCollectRepository;
    @Autowired
    private CommentRepository commentRepository;
    @Autowired
    private PostRatingRepository postRatingRepository;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private CommentLikeRepository commentLikeRepository;
    @Autowired private AiServiceImpl aiService;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private UserMapper userMapper;
    /**
     * 处理帖子删除
     */
    @RabbitHandler
    public void handlePostDelete(PostDeleteEvent event) {
        String postId = event.getPostId();
        log.info("RabbitMQ 收到删帖事件，清理关联数据... postId={}", postId);

        // 1. 清理 Redis 缓存 (如果有)
        // redisTemplate.delete("post:detail:" + postId);

        // 2. 清理 MongoDB 关联数据
        try {
            postLikeRepository.deleteByPostId(postId);
            postCollectRepository.deleteByPostId(postId);
            postRatingRepository.deleteByPostId(postId);

            // 方案 A: 完美主义 (数据绝对干净，但性能稍慢)
            // 第一步：查出该贴下所有评论的 ID (只查 ID，很快)
//             List<CommentDoc> comments = commentRepository.findByPostIdAndParentIdIsNull(postId, Pageable.unpaged()).getContent();
//             List<String> commentIds = comments.stream().map(CommentDoc::getId).collect(Collectors.toList());

            // 第二步：批量删除这些评论的点赞
//             if (CollUtil.isNotEmpty(commentIds)) {
//                 // 需要在 Repository 加一个 deleteByCommentIdIn(List<String> ids)
//                 commentLikeRepository.deleteByCommentIdIn(commentIds);
//             }

            // 方案 B: 实用主义 (推荐)
            // 直接删除评论，忽略"评论点赞表"里的孤儿数据。
            // 因为 commentId 已经删了，依附于它的点赞数据永远查不出来，只是占一点点磁盘空间而已。
            commentRepository.deleteByPostId(postId);

            log.info("删帖清理完成");
        } catch (Exception e) {
            log.error("删帖清理失败: postId={}", postId, e);
        }
    }


    // 读取配置文件的机器人ID
    @Value("${app.bot.user-id}")
    private Long botUserId;

    @RabbitHandler
    public void handlePostCreate(PostCreateEvent event) {
        log.info("RabbitMQ 收到发帖事件: {}", event.getPostId());

        try {
            handleAutoComment(event);
        } catch (Exception e) {
            log.error("AI 自动评论失败", e);
        }
    }

    /**
     * 处理自动评论逻辑
     */
    private void handleAutoComment(PostCreateEvent event) {
        // TODO 审核逻辑
        // 1. 调用 AI 获取总结
        String summary = aiService.generatePostSummary(event.getTitle(),event.getContent());

        // 如果 AI 没返回（比如内容太短），直接跳过
        if (StrUtil.isBlank(summary)) return;

        log.info("AI 生成总结评论: {}", summary);

        // 2. 获取机器人用户信息 (为了填充冗余字段)
        User botUser = userMapper.selectById(botUserId);
        if (botUser == null) {
            log.warn("未找到机器人账号 ID={}, 取消评论", botUserId);
            return;
        }

        // 3. 构建评论对象
        CommentDoc comment = new CommentDoc();
        comment.setPostId(event.getPostId());
        comment.setUserId(botUserId);
        comment.setUserNickname(botUser.getNickname()); // "AI省流助手"
        comment.setUserAvatar(botUser.getAvatar());
        comment.setContent(summary);
        comment.setCreatedAt(LocalDateTime.now());
        comment.setLikeCount(0);
        comment.setReplyCount(0);
        comment.setParentId(null); // 这是一级评论

        // 4. 保存评论
        commentRepository.save(comment);

        // 5. 更新帖子的评论数 (commentCount + 1)
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(event.getPostId())),
                new Update().inc("commentCount", 1),
                PostDoc.class
        );
    }
    /**
     * 处理帖子更新 (审核逻辑)
     */
    @RabbitHandler
    public void handlePostUpdate(PostUpdateEvent event) {
        log.info("RabbitMQ 收到修帖事件，重新审核: {}", event.getPostId());
        // TODO: 重新审核逻辑
    }
}