package logic

import (
	"context"
	"time"

	appmetrics "notification-rpc/internal/metrics"
	"notification-rpc/internal/model"
	"notification-rpc/internal/svc"
	"notification-rpc/notification"

	"github.com/zeromicro/go-zero/core/logx"
)

type CreateNotificationLogic struct {
	ctx    context.Context
	svcCtx *svc.ServiceContext
	logx.Logger
}

func NewCreateNotificationLogic(ctx context.Context, svcCtx *svc.ServiceContext) *CreateNotificationLogic {
	return &CreateNotificationLogic{
		ctx:    ctx,
		svcCtx: svcCtx,
		Logger: logx.WithContext(ctx),
	}
}

// 普通插入：评论、回复、系统类通知
// 这类通知保留每一条历史记录，不做去重。
func (l *CreateNotificationLogic) CreateNotification(in *notification.CreateNotificationRequest) (*notification.Empty, error) {
	start := time.Now()
	payload := in.GetNotification()
	if payload == nil {
		appmetrics.ObserveRequest("create_notification", "empty_payload", time.Since(start))
		return &notification.Empty{}, nil
	}

	doc := model.NotificationDoc{
		ReceiverID:     payload.GetReceiverId(),
		SenderID:       payload.GetSenderId(),
		SenderNickname: payload.GetSenderNickname(),
		SenderAvatar:   payload.GetSenderAvatar(),
		Type:           model.FormatNotificationType(payload.GetType()),
		TargetID:       payload.GetTargetId(),
		TargetPreview:  payload.GetTargetPreview(),
		IsRead:         false,
		CreatedAt:      time.Now(),
	}

	_, err := l.svcCtx.Mongo.Collection("notifications").InsertOne(l.ctx, doc)
	if err != nil {
		appmetrics.ObserveRequest("create_notification", "mongo_error", time.Since(start))
		return nil, err
	}

	appmetrics.ObserveRequest("create_notification", "success", time.Since(start))
	return &notification.Empty{}, nil
}
