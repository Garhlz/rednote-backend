package com.szu.afternoon3.platform.service;

import com.szu.afternoon3.platform.entity.mongo.NotificationDoc;
import com.szu.afternoon3.platform.vo.PageResult;

import java.util.List;
import java.util.Map;

public interface NotificationService {
    // 获取未读数
    long countUnread(Long userId);

    // 获取消息列表
    PageResult<NotificationDoc> getMyNotifications(Long userId, Integer page, Integer size);

    // 全部已读
    void markAllAsRead(Long userId);

    // 保存通知 (给 Listener 调用)
    void save(NotificationDoc doc);

    // 批量设置已读
    void markBatchAsRead(Long userId, List<String> ids);

    // 通知数据清洗去重
    long cleanDuplicateNotifications();
}