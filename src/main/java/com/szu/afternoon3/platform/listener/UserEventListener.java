package com.szu.afternoon3.platform.listener;

import com.szu.afternoon3.platform.entity.mongo.*;
import com.szu.afternoon3.platform.event.UserDeleteEvent;
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

    /**
     * 处理用户注销/删除事件：清理 MongoDB 中的所有关联数据
     */
    @Async // 关键：异步执行，不阻塞管理员接口
    @EventListener
    public void handleUserDelete(UserDeleteEvent event) {
        Long userId = event.getUserId();
        log.info("开始异步清理用户数据: userId={}", userId);
        long start = System.currentTimeMillis();

        // 1. 删除该用户发布的帖子
        mongoTemplate.remove(Query.query(Criteria.where("userId").is(userId)), PostDoc.class);

        // 2. 删除该用户发布的评论
        mongoTemplate.remove(Query.query(Criteria.where("userId").is(userId)), CommentDoc.class);

        // 3. 删除该用户的点赞记录
        mongoTemplate.remove(Query.query(Criteria.where("userId").is(userId)), PostLikeDoc.class);
        mongoTemplate.remove(Query.query(Criteria.where("userId").is(userId)), CommentLikeDoc.class);

        // 4. 删除该用户的收藏记录
        mongoTemplate.remove(Query.query(Criteria.where("userId").is(userId)), PostCollectDoc.class);

        // 5. 删除该用户的评分记录
        mongoTemplate.remove(Query.query(Criteria.where("userId").is(userId)), PostRatingDoc.class);

        // 6. 删除该用户的关注关系 (作为粉丝)
        mongoTemplate.remove(Query.query(Criteria.where("userId").is(userId)), UserFollowDoc.class);

        // 7. 删除该用户的被关注关系 (作为博主) - 可选：也可以保留记录但把 targetUser 信息置空，直接删比较干净
        mongoTemplate.remove(Query.query(Criteria.where("targetUserId").is(userId)), UserFollowDoc.class);

        // 8. 删除搜索历史 & 浏览历史
        mongoTemplate.remove(Query.query(Criteria.where("userId").is(userId)), SearchHistoryDoc.class);
        mongoTemplate.remove(Query.query(Criteria.where("userId").is(userId)), PostViewHistoryDoc.class);

        log.info("用户数据清理完成，耗时: {}ms", System.currentTimeMillis() - start);
    }
}