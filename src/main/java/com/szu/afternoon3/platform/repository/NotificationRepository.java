package com.szu.afternoon3.platform.repository;

import com.szu.afternoon3.platform.entity.mongo.NotificationDoc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends MongoRepository<NotificationDoc, String> {

    // 1. 统计未读数 (用于轮询接口)
    long countByReceiverIdAndIsReadFalse(Long receiverId);

    // 2. 分页查询我的消息列表
    Page<NotificationDoc> findByReceiverId(Long receiverId, Pageable pageable);
}