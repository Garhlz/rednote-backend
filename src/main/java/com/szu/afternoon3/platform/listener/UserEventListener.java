package com.szu.afternoon3.platform.listener;

import com.szu.afternoon3.platform.entity.mongo.CommentDoc;
import com.szu.afternoon3.platform.entity.mongo.PostDoc;
import com.szu.afternoon3.platform.entity.mongo.UserFollowDoc;
import com.szu.afternoon3.platform.event.UserUpdateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UserEventListener {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Async
    @EventListener
    public void handleUserUpdate(UserUpdateEvent event) {
        Long userId = event.getUserId();
        String newNickname = event.getNewNickname();
        String newAvatar = event.getNewAvatar();

        log.info("收到用户信息变更，开始全量同步 MongoDB... userId={}", userId);

        // =====================================================
        // 场景 1: 更新“我主动产生的数据” (我是作者/评论者/粉丝)
        // 涉及字段: userNickname, userAvatar
        // =====================================================
        Update updateSelf = new Update();
        if (newNickname != null) updateSelf.set("userNickname", newNickname);
        if (newAvatar != null) updateSelf.set("userAvatar", newAvatar);

        // 1.1 更新我的帖子
        mongoTemplate.updateMulti(
                Query.query(Criteria.where("userId").is(userId)),
                updateSelf,
                PostDoc.class
        );

        // 1.2 更新我的评论
        mongoTemplate.updateMulti(
                Query.query(Criteria.where("userId").is(userId)),
                updateSelf,
                CommentDoc.class
        );

        // 1.3 更新我发起的关注 (在别人的粉丝列表里显示我)
        mongoTemplate.updateMulti(
                Query.query(Criteria.where("userId").is(userId)),
                updateSelf,
                UserFollowDoc.class
        );

        // =====================================================
        // 场景 2: 更新“我作为被关注者的数据” (我是博主)
        // 涉及字段: targetUserNickname, targetUserAvatar
        // =====================================================
        Update updateTarget = new Update();
        if (newNickname != null) updateTarget.set("targetUserNickname", newNickname);
        if (newAvatar != null) updateTarget.set("targetUserAvatar", newAvatar);

        // 2.1 更新关注我的人的列表 (在别人的关注列表里显示我)
        mongoTemplate.updateMulti(
                Query.query(Criteria.where("targetUserId").is(userId)),
                updateTarget,
                UserFollowDoc.class
        );

        // =====================================================
        // 场景 3: 更新“我被回复的数据” (评论区里 '回复 @我')
        // 涉及字段: replyToUserNickname, replyToUserAvatar
        // =====================================================
        Update updateReply = new Update();
        if (newNickname != null) updateReply.set("replyToUserNickname", newNickname);
        if (newAvatar != null) updateReply.set("replyToUserAvatar", newAvatar);

        // 3.1 更新所有回复给我的评论
        mongoTemplate.updateMulti(
                Query.query(Criteria.where("replyToUserId").is(userId)),
                updateReply,
                CommentDoc.class
        );

        log.info("MongoDB 数据同步完成 (异步)");
    }
}