package logic

import (
	"context"
	"time"

	appmetrics "notification-rpc/internal/metrics"
	"notification-rpc/internal/svc"
	"notification-rpc/notification"

	"github.com/zeromicro/go-zero/core/logx"
	"go.mongodb.org/mongo-driver/bson"
)

type MarkAllReadLogic struct {
	ctx    context.Context
	svcCtx *svc.ServiceContext
	logx.Logger
}

func NewMarkAllReadLogic(ctx context.Context, svcCtx *svc.ServiceContext) *MarkAllReadLogic {
	return &MarkAllReadLogic{
		ctx:    ctx,
		svcCtx: svcCtx,
		Logger: logx.WithContext(ctx),
	}
}

// 对应当前 /api/message/read (全部已读)
// 这里是一个批量状态变更动作，只关心当前用户自己的未读通知。
func (l *MarkAllReadLogic) MarkAllRead(in *notification.MarkAllReadRequest) (*notification.Empty, error) {
	start := time.Now()
	_, err := l.svcCtx.Mongo.Collection("notifications").UpdateMany(
		l.ctx,
		bson.M{
			"receiverId": in.GetUserId(),
			"isRead":     false,
		},
		bson.M{
			"$set": bson.M{
				"isRead": true,
			},
		},
	)
	if err != nil {
		appmetrics.ObserveRequest("mark_all_read", "mongo_error", time.Since(start))
		return nil, err
	}

	appmetrics.ObserveRequest("mark_all_read", "success", time.Since(start))
	return &notification.Empty{}, nil
}
