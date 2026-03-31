package com.szu.afternoon3.platform.service.impl;

import com.szu.afternoon3.platform.entity.mongo.NotificationDoc;
import com.szu.afternoon3.platform.enums.NotificationType;
import com.szu.afternoon3.platform.service.NotificationService;
import com.szu.afternoon3.platform.vo.PageResult;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    @GrpcClient("notification-service")
    private notification.NotificationServiceGrpc.NotificationServiceBlockingStub notificationStub;

    @Override
    public long countUnread(Long userId) {
        notification.NotificationOuterClass.GetUnreadCountResponse response =
                notificationStub.getUnreadCount(notification.NotificationOuterClass.GetUnreadCountRequest.newBuilder()
                        .setUserId(userId)
                        .build());
        return response.getUnreadCount();
    }

    @Override
    public PageResult<NotificationDoc> getMyNotifications(Long userId, Integer page, Integer size) {
        int currentPage = (page == null || page < 1) ? 1 : page;
        int pageSize = (size == null || size < 1) ? 20 : size;

        notification.NotificationOuterClass.ListNotificationsResponse response =
                notificationStub.listNotifications(notification.NotificationOuterClass.ListNotificationsRequest.newBuilder()
                        .setUserId(userId)
                        .setPage(currentPage)
                        .setPageSize(pageSize)
                        .build());

        List<NotificationDoc> records = response.getItemsList().stream()
                .map(this::toDoc)
                .collect(Collectors.toList());

        return PageResult.of(records, response.getTotal(), response.getPage(), response.getPageSize());
    }

    @Override
    public void markAllAsRead(Long userId) {
        notificationStub.markAllRead(notification.NotificationOuterClass.MarkAllReadRequest.newBuilder()
                .setUserId(userId)
                .build());
    }

    @Override
    public void markBatchAsRead(Long userId, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        notificationStub.markBatchRead(notification.NotificationOuterClass.MarkBatchReadRequest.newBuilder()
                .setUserId(userId)
                .addAllIds(ids)
                .build());
    }

    @Override
    public void save(NotificationDoc doc) {
        if (doc == null) {
            return;
        }
        notification.NotificationOuterClass.NotificationPayload payload = toPayload(doc);
        if (isStatusNotification(doc.getType())) {
            notificationStub.upsertNotification(notification.NotificationOuterClass.UpsertNotificationRequest.newBuilder()
                    .setNotification(payload)
                    .build());
            return;
        }
        notificationStub.createNotification(notification.NotificationOuterClass.CreateNotificationRequest.newBuilder()
                .setNotification(payload)
                .build());
    }

    /**
     * 判断是否为状态类通知 (需要去重)
     */
    private boolean isStatusNotification(NotificationType type) {
        return type == NotificationType.LIKE_POST ||
                type == NotificationType.COLLECT_POST ||
                type == NotificationType.RATE_POST ||
                type == NotificationType.LIKE_COMMENT ||
                type == NotificationType.FOLLOW;
    }

    public long cleanDuplicateNotifications() {
        notification.NotificationOuterClass.CleanDuplicateNotificationsResponse response =
                notificationStub.cleanDuplicateNotifications(notification.NotificationOuterClass.CleanDuplicateNotificationsRequest.newBuilder().build());
        log.info("数据清洗完成，共删除了 {} 条重复通知", response.getDeletedCount());
        return response.getDeletedCount();
    }

    private notification.NotificationOuterClass.NotificationPayload toPayload(NotificationDoc doc) {
        return notification.NotificationOuterClass.NotificationPayload.newBuilder()
                .setReceiverId(doc.getReceiverId() == null ? 0L : doc.getReceiverId())
                .setSenderId(doc.getSenderId() == null ? 0L : doc.getSenderId())
                .setSenderNickname(doc.getSenderNickname() == null ? "" : doc.getSenderNickname())
                .setSenderAvatar(doc.getSenderAvatar() == null ? "" : doc.getSenderAvatar())
                .setType(toProtoType(doc.getType()))
                .setTargetId(doc.getTargetId() == null ? "" : doc.getTargetId())
                .setTargetPreview(doc.getTargetPreview() == null ? "" : doc.getTargetPreview())
                .build();
    }

    private NotificationDoc toDoc(notification.NotificationOuterClass.Notification item) {
        NotificationDoc doc = new NotificationDoc();
        doc.setId(item.getId());
        doc.setReceiverId(item.getReceiverId());
        doc.setSenderId(item.getSenderId());
        doc.setSenderNickname(item.getSenderNickname());
        doc.setSenderAvatar(item.getSenderAvatar());
        doc.setType(toJavaType(item.getType()));
        doc.setTargetId(item.getTargetId());
        doc.setTargetPreview(item.getTargetPreview());
        doc.setIsRead(item.getIsRead());
        doc.setCreatedAt(LocalDateTime.ofInstant(Instant.ofEpochSecond(item.getCreatedAt()), ZoneId.systemDefault()));
        return doc;
    }

    private notification.NotificationOuterClass.NotificationType toProtoType(NotificationType type) {
        if (type == null) {
            return notification.NotificationOuterClass.NotificationType.NOTIFICATION_TYPE_UNKNOWN;
        }
        try {
            return notification.NotificationOuterClass.NotificationType.valueOf(type.name());
        } catch (IllegalArgumentException e) {
            return notification.NotificationOuterClass.NotificationType.NOTIFICATION_TYPE_UNKNOWN;
        }
    }

    private NotificationType toJavaType(notification.NotificationOuterClass.NotificationType type) {
        if (type == null || type == notification.NotificationOuterClass.NotificationType.NOTIFICATION_TYPE_UNKNOWN) {
            return NotificationType.SYSTEM;
        }
        try {
            return NotificationType.valueOf(type.name());
        } catch (IllegalArgumentException e) {
            return NotificationType.SYSTEM;
        }
    }
}
