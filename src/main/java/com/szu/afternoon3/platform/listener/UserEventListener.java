package com.szu.afternoon3.platform.listener;

import com.szu.afternoon3.platform.config.RabbitConfig;
import com.szu.afternoon3.platform.entity.mongo.*;
import com.szu.afternoon3.platform.event.UserDeleteEvent;
import com.szu.afternoon3.platform.event.UserUpdateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
@Slf4j
// 1. 监听整个队列
@RabbitListener(queues = RabbitConfig.QUEUE_USER)
public class UserEventListener {

    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * 2. 专门处理 UserUpdateEvent 的 Handler
     */
    @RabbitHandler
    public void handleUserUpdate(UserUpdateEvent event) {
        Long userId = event.getUserId();
        String newNickname = event.getNewNickname();
        String newAvatar = event.getNewAvatar();

        log.info("RabbitMQ 开始同步用户信息... userId={}", userId);

        Update updateSelf = new Update();
        if (newNickname != null) updateSelf.set("userNickname", newNickname);
        if (newAvatar != null) updateSelf.set("userAvatar", newAvatar);

        // 1. 更新帖子
        mongoTemplate.updateMulti(Query.query(Criteria.where("userId").is(userId)), updateSelf, PostDoc.class);
        // 2. 更新评论
        mongoTemplate.updateMulti(Query.query(Criteria.where("userId").is(userId)), updateSelf, CommentDoc.class);
        // 3. 更新我发起的关注
        mongoTemplate.updateMulti(Query.query(Criteria.where("userId").is(userId)), updateSelf, UserFollowDoc.class);

        Update updateTarget = new Update();
        if (newNickname != null) updateTarget.set("targetUserNickname", newNickname);
        if (newAvatar != null) updateTarget.set("targetUserAvatar", newAvatar);

        // 4. 更新关注我的人的列表
        mongoTemplate.updateMulti(Query.query(Criteria.where("targetUserId").is(userId)), updateTarget, UserFollowDoc.class);

        Update updateReply = new Update();
        if (newNickname != null) updateReply.set("replyToUserNickname", newNickname);
        if (newAvatar != null) updateReply.set("replyToUserAvatar", newAvatar);

        // 5. 更新回复我的
        mongoTemplate.updateMulti(Query.query(Criteria.where("replyToUserId").is(userId)), updateReply, CommentDoc.class);

        log.info("RabbitMQ 用户数据同步完成");
    }

    /**
     * 3. 专门处理 UserDeleteEvent 的 Handler
     */
    @RabbitHandler
    public void handleUserDelete(UserDeleteEvent event) {
        Long userId = event.getUserId();
        log.info("RabbitMQ 开始清理注销用户数据: userId={}", userId);
        long start = System.currentTimeMillis();

        mongoTemplate.remove(Query.query(Criteria.where("userId").is(userId)), PostDoc.class);
        mongoTemplate.remove(Query.query(Criteria.where("userId").is(userId)), CommentDoc.class);
        mongoTemplate.remove(Query.query(Criteria.where("userId").is(userId)), PostLikeDoc.class);
        mongoTemplate.remove(Query.query(Criteria.where("userId").is(userId)), CommentLikeDoc.class);
        mongoTemplate.remove(Query.query(Criteria.where("userId").is(userId)), PostCollectDoc.class);
        mongoTemplate.remove(Query.query(Criteria.where("userId").is(userId)), PostRatingDoc.class);
        mongoTemplate.remove(Query.query(Criteria.where("userId").is(userId)), UserFollowDoc.class);
        mongoTemplate.remove(Query.query(Criteria.where("targetUserId").is(userId)), UserFollowDoc.class);
        mongoTemplate.remove(Query.query(Criteria.where("userId").is(userId)), SearchHistoryDoc.class);
        mongoTemplate.remove(Query.query(Criteria.where("userId").is(userId)), PostViewHistoryDoc.class);

        log.info("用户数据清理完成，耗时: {}ms", System.currentTimeMillis() - start);
    }
}