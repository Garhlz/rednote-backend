package com.szu.afternoon3.platform.grpc;

import com.szu.afternoon3.platform.entity.mongo.NotificationDoc;
import com.szu.afternoon3.platform.enums.NotificationType;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationRpcClient {

    @GrpcClient("notification-service")
    private notification.NotificationServiceGrpc.NotificationServiceBlockingStub notificationStub;

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

    public long cleanDuplicateNotifications() {
        notification.NotificationOuterClass.CleanDuplicateNotificationsResponse response =
                notificationStub.cleanDuplicateNotifications(notification.NotificationOuterClass.CleanDuplicateNotificationsRequest.newBuilder().build());
        log.info("数据清洗完成，共删除了 {} 条重复通知", response.getDeletedCount());
        return response.getDeletedCount();
    }

    private boolean isStatusNotification(NotificationType type) {
        return type == NotificationType.LIKE_POST ||
                type == NotificationType.COLLECT_POST ||
                type == NotificationType.RATE_POST ||
                type == NotificationType.LIKE_COMMENT ||
                type == NotificationType.FOLLOW;
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
}
