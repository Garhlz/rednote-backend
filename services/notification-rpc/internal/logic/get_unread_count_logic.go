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

type GetUnreadCountLogic struct {
	ctx    context.Context
	svcCtx *svc.ServiceContext
	logx.Logger
}

func NewGetUnreadCountLogic(ctx context.Context, svcCtx *svc.ServiceContext) *GetUnreadCountLogic {
	return &GetUnreadCountLogic{
		ctx:    ctx,
		svcCtx: svcCtx,
		Logger: logx.WithContext(ctx),
	}
}

// 对应当前 /api/message/unread-count
// 这是典型的读模型查询：
// 直接按 receiverId + isRead=false 统计，不再依赖 Java 内部 service。
func (l *GetUnreadCountLogic) GetUnreadCount(in *notification.GetUnreadCountRequest) (*notification.GetUnreadCountResponse, error) {
	start := time.Now()
	count, err := l.svcCtx.Mongo.Collection("notifications").CountDocuments(
		l.ctx,
		bson.M{
			"receiverId": in.GetUserId(),
			"isRead":     false,
		},
	)
	if err != nil {
		appmetrics.ObserveRequest("get_unread_count", "mongo_error", time.Since(start))
		return nil, err
	}

	appmetrics.ObserveRequest("get_unread_count", "success", time.Since(start))
	return &notification.GetUnreadCountResponse{UnreadCount: count}, nil
}
