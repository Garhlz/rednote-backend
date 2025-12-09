package com.szu.afternoon3.platform.service.impl;

import com.szu.afternoon3.platform.entity.mongo.NotificationDoc;
import com.szu.afternoon3.platform.repository.NotificationRepository;
import com.szu.afternoon3.platform.service.NotificationService;
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

import java.util.HashMap;
import java.util.Map;

@Service
public class NotificationServiceImpl implements NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private MongoTemplate mongoTemplate; // 用于高效的批量更新

    @Override
    public long countUnread(Long userId) {
        return notificationRepository.countByReceiverIdAndIsReadFalse(userId);
    }

    @Override
    public Map<String, Object>  getMyNotifications(Long userId, Integer page, Integer size) {
        int pageNum = (page == null || page < 1) ? 0 : page - 1;
        int pageSize = (size == null || size < 1) ? 20 : size;

        // 按时间倒序
        Pageable pageable = PageRequest.of(pageNum, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        Page<NotificationDoc> pageData = notificationRepository.findByReceiverId(userId, pageable);

        Map<String, Object> result = new HashMap<>();
        result.put("records", pageData.getContent()); // 直接返回 Doc 即可，前端按需取字段
        result.put("total", pageData.getTotalElements());
        
        return result;
    }

    @Override
    public void markAllAsRead(Long userId) {
        // 使用 MongoTemplate 进行批量更新，性能优于先查后改
        // update notifications set isRead = true where receiverId = userId and isRead = false
        Query query = Query.query(Criteria.where("receiverId").is(userId).and("isRead").is(false));
        Update update = new Update().set("isRead", true);
        
        mongoTemplate.updateMulti(query, update, NotificationDoc.class);
    }

    @Override
    public void save(NotificationDoc doc) {
        notificationRepository.save(doc);
    }
}