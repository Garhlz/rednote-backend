package com.szu.afternoon3.platform.listener;

import cn.hutool.core.util.StrUtil;
import com.szu.afternoon3.platform.config.RabbitConfig;
import com.szu.afternoon3.platform.entity.User;
import com.szu.afternoon3.platform.entity.mongo.CommentDoc;
import com.szu.afternoon3.platform.entity.mongo.NotificationDoc;
import com.szu.afternoon3.platform.entity.mongo.PostDoc;
import com.szu.afternoon3.platform.enums.NotificationType;
import com.szu.afternoon3.platform.event.CommentEvent;
import com.szu.afternoon3.platform.mapper.UserMapper;
import com.szu.afternoon3.platform.repository.CommentLikeRepository;
import com.szu.afternoon3.platform.repository.CommentRepository;
import com.szu.afternoon3.platform.repository.PostRepository;
import com.szu.afternoon3.platform.service.NotificationService;
import com.szu.afternoon3.platform.service.impl.AiServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
@RabbitListener(queues = RabbitConfig.QUEUE_COMMENT)
public class CommentEventListener {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CommentLikeRepository commentLikeRepository;
    // 【新增】 需要查帖子和评论内容
    @Autowired private PostRepository postRepository;
    @Autowired private CommentRepository commentRepository;

    // 【新增】 AI 服务
    @Autowired private AiServiceImpl aiService;

    // 【新增】 机器人配置
    @Value("${app.bot.user-id}")
    private Long botUserId;

    @Value("${app.bot.user-nickname}")
    private String botNickname;

    /**
     * 【核心修复】唯一的入口方法
     * 根据 event.getType() 分发逻辑
     */
    @RabbitHandler
    public void handleCommentEvent(CommentEvent event) {
        if (event == null || event.getType() == null) {
            log.warn("收到无效的 CommentEvent");
            return;
        }

        switch (event.getType()) {
            case "CREATE":
                processCommentCreate(event);
                break;
            case "DELETE":
                processCommentDelete(event);
                break;
            default:
                log.warn("未知的评论事件类型: {}", event.getType());
        }
    }
    /**
     * 处理评论创建事件
     */
    private void processCommentCreate(CommentEvent event) {
        // 0. 【关键】防止无限死循环：如果是机器人自己发的，直接忽略
        if (botUserId.equals(event.getUserId())) {
            return;
        }
        if (!"CREATE".equals(event.getType())) return;

        log.info("RabbitMQ 处理评论创建: commentId={}", event.getCommentId());

        // 更新帖子总评论数 +1 (Atomic)
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(event.getPostId())),
                new Update().inc("commentCount", 1),
                PostDoc.class
        );

        // 二级评论对应的一级评论的评论数+1放在service中做了

        // 发送通知
        sendCommentNotification(event);

        // 3. 【新增】检测是否触发 AI 回复
        try {
            checkAndTriggerAiReply(event);
        } catch (Exception e) {
            log.error("AI 交互回复处理失败", e);
        }
    }
    /**
     * 检测并触发 AI 回复逻辑
     */
    private void checkAndTriggerAiReply(CommentEvent event) {
        // 触发条件 A: 明确回复了机器人 (点击了回复按钮)
        boolean isReplyToBot = botUserId.equals(event.getReplyToUserId());

        // 触发条件 B: 在内容里 @了机器人 (文本包含)
        // 注意：前端传过来的 content 可能是 "你好 @AI省流助手 帮我看下"
        boolean isMentionBot = event.getContent() != null && event.getContent().contains("@" + botNickname);

        if (!isReplyToBot && !isMentionBot) {
            return;
        }

        log.info("触发 AI 交互回复, 用户: {}, 内容: {}", event.getUserId(), event.getContent());

        // --- 准备数据 ---

        // 1. 获取帖子信息 (一级、二级回复都需要)
        PostDoc post = postRepository.findById(event.getPostId()).orElse(null);
        if (post == null) return;

        // 2. 获取父评论内容 (仅二级回复需要)
        String parentContent = null;
        if (StrUtil.isNotBlank(event.getParentId())) {
            // 如果是二级评论，查一下父评论说啥
            CommentDoc parent = commentRepository.findById(event.getParentId()).orElse(null);
            if (parent != null) {
                parentContent = parent.getContent();
            }
        }

        // 3. 调用 AI
        // 可以在这里把 content 里的 "@AI省流助手" 替换为空，避免 AI 读到自己的名字
        String cleanPrompt = event.getContent().replace("@" + botNickname, "").trim();

        String aiReplyContent = aiService.generateInteractiveReply(
                post.getTitle(),
                post.getContent(),
                parentContent,
                cleanPrompt
        );

        if (StrUtil.isBlank(aiReplyContent)) return;

        // 4. 保存 AI 的回复
        saveAiComment(event, aiReplyContent);
    }

    private void saveAiComment(CommentEvent userEvent, String aiContent) {
        // 获取机器人信息
        User botUser = userMapper.selectById(botUserId);
        if (botUser == null) return;

        CommentDoc aiComment = new CommentDoc();
        aiComment.setPostId(userEvent.getPostId());
        aiComment.setUserId(botUserId);
        aiComment.setUserNickname(botUser.getNickname());
        aiComment.setUserAvatar(botUser.getAvatar());
        aiComment.setContent(aiContent);
        aiComment.setCreatedAt(LocalDateTime.now());
        aiComment.setLikeCount(0);
        aiComment.setReplyCount(0);

        // 确定 AI 回复的层级
        // 策略：AI 总是回复触发它的那条评论
        aiComment.setParentId(userEvent.getCommentId());

        // 这里的逻辑是：
        // 如果用户发了一级评论 @AI，AI 回复它 (变成二级评论)
        // 如果用户发了二级评论 @AI，AI 回复它 (变成“三级”视觉上的回复，但在数据库结构里通常 parentId 指向被回复的 ID)

        commentRepository.save(aiComment);

        // 别忘了更新帖子评论数 +1
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(userEvent.getPostId())),
                new Update().inc("commentCount", 1),
                PostDoc.class
        );

        // 更新父评论（也就是用户的评论）的 replyCount +1
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(userEvent.getCommentId())),
                new Update().inc("replyCount", 1),
                CommentDoc.class
        );
    }
    /**
     * 处理评论删除事件
     */
    private void processCommentDelete(CommentEvent event) {
        if (!"DELETE".equals(event.getType())) return;

        String commentId = event.getCommentId();
        log.info("RabbitMQ 处理评论删除: commentId={}", commentId);

        // 1. 【新增】清理该评论的点赞记录
        // 对应 Repository 里的 void deleteByCommentId(String commentId);
        commentLikeRepository.deleteByCommentId(commentId);

        // 2. 更新帖子总评论数 -1
        // (注意：如果是级联删除帖子引发的，这一步其实是多余的，但在单删评论时是必须的)
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(event.getPostId()).and("commentCount").gt(0)),
                new Update().inc("commentCount", -1),
                PostDoc.class
        );

        // 3. 更新父评论回复数
        if (StrUtil.isNotBlank(event.getParentId())) {
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("id").is(event.getParentId()).and("replyCount").gt(0)),
                    new Update().inc("replyCount", -1),
                    CommentDoc.class
            );
        }
    }
    // --- 辅助方法：生成通知 ---
    private void sendCommentNotification(CommentEvent event) {
        Long senderId = event.getUserId(); // 评论者

        // 场景 A: 回复了某人 (二级评论) -> 类型 REPLY
        // event.getReplyToUserId() 在 DTO 转 Event 时被设置
        if (event.getReplyToUserId() != null) {
            // 如果是自己回复自己，不发通知
            if (senderId.equals(event.getReplyToUserId())) return;

            // 接收者是 被回复的人
            createNotify(senderId, event.getReplyToUserId(), NotificationType.REPLY, event);
        }
        // 场景 B: 直接评论帖子 (一级评论) -> 类型 COMMENT
        // 接收者是 帖子作者
        else if (event.getPostAuthorId() != null) {
            // 如果是帖子作者自己评论自己，不发通知
            if (senderId.equals(event.getPostAuthorId())) return;

            createNotify(senderId, event.getPostAuthorId(), NotificationType.COMMENT, event);
        }
    }

    private void createNotify(Long senderId, Long receiverId, NotificationType type, CommentEvent event) {
        // 1. 查发送者信息 (用于填充 NotificationDoc 的冗余字段)
        User sender = userMapper.selectById(senderId);
        if (sender == null) return;

        // 2. 构建通知对象
        NotificationDoc doc = new NotificationDoc();
        doc.setReceiverId(receiverId); // 接收者

        doc.setSenderId(senderId);
        doc.setSenderNickname(sender.getNickname());
        doc.setSenderAvatar(sender.getAvatar());

        doc.setType(type); // "COMMENT" or "REPLY

        // 关联ID：存 PostId。这样前端点击通知，可以直接跳到帖子详情页
        doc.setTargetId(event.getPostId());

        // 摘要：显示评论内容 (截取前 50 字)
        doc.setTargetPreview(StrUtil.subPre(event.getContent(), 50));

        // 3. 保存
        notificationService.save(doc);
    }
}