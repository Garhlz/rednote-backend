package logic

import (
	"context"
	"time"

	appmetrics "notification-rpc/internal/metrics"
	"notification-rpc/internal/model"
	"notification-rpc/internal/svc"
	"notification-rpc/notification"

	"github.com/zeromicro/go-zero/core/logx"
	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/mongo/options"
)

type UpsertNotificationLogic struct {
	ctx    context.Context
	svcCtx *svc.ServiceContext
	logx.Logger
}

func NewUpsertNotificationLogic(ctx context.Context, svcCtx *svc.ServiceContext) *UpsertNotificationLogic {
	return &UpsertNotificationLogic{
		ctx:    ctx,
		svcCtx: svcCtx,
		Logger: logx.WithContext(ctx),
	}
}

// 去重 upsert：点赞、收藏、评分、关注类通知
// 这类通知属于“状态型通知”，同一发送者对同一目标的重复动作只保留一条最新记录。
func (l *UpsertNotificationLogic) UpsertNotification(in *notification.UpsertNotificationRequest) (*notification.Empty, error) {
	start := time.Now()
	payload := in.GetNotification()
	if payload == nil {
		appmetrics.ObserveRequest("upsert_notification", "empty_payload", time.Since(start))
		return &notification.Empty{}, nil
	}

	typeName := model.FormatNotificationType(payload.GetType())
	filter := bson.M{
		"receiverId": payload.GetReceiverId(),
		"senderId":   payload.GetSenderId(),
		"type":       typeName,
		"targetId":   payload.GetTargetId(),
	}
	update := bson.M{
		"$set": bson.M{
			// 重复动作再次发生时，直接刷新展示字段与 createdAt，
			// 让消息中心里这条通知表现为“最近又发生了一次”。
			"senderNickname": payload.GetSenderNickname(),
			"senderAvatar":   payload.GetSenderAvatar(),
			"targetPreview":  payload.GetTargetPreview(),
			"isRead":         false,
			"createdAt":      time.Now(),
		},
		"$setOnInsert": bson.M{
			"receiverId": payload.GetReceiverId(),
			"senderId":   payload.GetSenderId(),
			"type":       typeName,
			"targetId":   payload.GetTargetId(),
		},
	}

	_, err := l.svcCtx.Mongo.Collection("notifications").UpdateOne(
		l.ctx,
		filter,
		update,
		options.Update().SetUpsert(true),
	)
	if err != nil {
		appmetrics.ObserveRequest("upsert_notification", "mongo_error", time.Since(start))
		return nil, err
	}

	appmetrics.ObserveRequest("upsert_notification", "success", time.Since(start))
	return &notification.Empty{}, nil
}
