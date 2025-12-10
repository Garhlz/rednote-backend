package com.szu.afternoon3.platform.listener;

import com.szu.afternoon3.platform.entity.mongo.SearchHistoryDoc;
import com.szu.afternoon3.platform.event.UserSearchEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
public class SearchEventListener {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Async("taskExecutor") // 显式指定线程池(如果在AsyncConfig配置了的话)，或者直接用@Async
    @EventListener
    public void handleSearchEvent(UserSearchEvent event) {
        Long userId = event.getUserId();
        String keyword = event.getKeyword();

        // 简单的判空，防止无效记录
        if (userId == null || keyword == null || keyword.trim().isEmpty()) {
            return;
        }

        try {
            // 1. 定义查询条件: userId + keyword (利用唯一索引)
            Query query = new Query(Criteria.where("userId").is(userId).and("keyword").is(keyword));

            // 2. 定义更新: 更新时间
            Update update = new Update();
            update.set("updatedAt", LocalDateTime.now());
            // 如果是新插入，Mongo会自动设置 userId 和 keyword (因为在Query里)
            // 但为了保险，显式setOnInsert一下
            update.setOnInsert("userId", userId);
            update.setOnInsert("keyword", keyword);

            // 3. 执行 Upsert (存在则更新时间，不存在则插入)
            mongoTemplate.upsert(query, update, SearchHistoryDoc.class);
            
            // log.debug("异步保存搜索历史成功: user={}, keyword={}", userId, keyword);

        } catch (Exception e) {
            log.warn("保存搜索历史失败: {}", e.getMessage());
        }
    }
}