package com.szu.afternoon3.platform.listener;

import com.szu.afternoon3.platform.config.RabbitConfig;
import com.szu.afternoon3.platform.entity.mongo.SearchHistoryDoc;
import com.szu.afternoon3.platform.event.UserSearchEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
public class SearchEventListener {

    @Autowired
    private MongoTemplate mongoTemplate;

    // 移除 @Async，替换为 @RabbitListener
    @RabbitListener(queues = RabbitConfig.QUEUE_SEARCH)
    public void handleSearchEvent(UserSearchEvent event) {
        Long userId = event.getUserId();
        String keyword = event.getKeyword();

        if (userId == null || keyword == null || keyword.trim().isEmpty()) {
            return;
        }

        try {
            // 1. 定义查询条件: userId + keyword (利用唯一索引)
            Query query = new Query(Criteria.where("userId").is(userId).and("keyword").is(keyword));

            // 2. 定义更新: 更新时间
            Update update = new Update();
            update.set("updatedAt", LocalDateTime.now());
            update.setOnInsert("userId", userId);
            update.setOnInsert("keyword", keyword);

            // 3. 执行 Upsert
            mongoTemplate.upsert(query, update, SearchHistoryDoc.class);
            log.debug("RabbitMQ 保存搜索历史: user={}, keyword={}", userId, keyword);

        } catch (Exception e) {
            log.warn("保存搜索历史失败: {}", e.getMessage());
        }
    }
}