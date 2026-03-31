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

type ListNotificationsLogic struct {
	ctx    context.Context
	svcCtx *svc.ServiceContext
	logx.Logger
}

func NewListNotificationsLogic(ctx context.Context, svcCtx *svc.ServiceContext) *ListNotificationsLogic {
	return &ListNotificationsLogic{
		ctx:    ctx,
		svcCtx: svcCtx,
		Logger: logx.WithContext(ctx),
	}
}

// 对应当前 /api/message/notifications
// 客户端当前仍然使用“轮询拉取通知列表”的方式，因此这里保持分页查询模型。
func (l *ListNotificationsLogic) ListNotifications(in *notification.ListNotificationsRequest) (*notification.ListNotificationsResponse, error) {
	start := time.Now()
	page := in.GetPage()
	if page < 1 {
		page = 1
	}
	pageSize := in.GetPageSize()
	if pageSize < 1 {
		pageSize = 20
	}

	filter := bson.M{"receiverId": in.GetUserId()}
	total, err := l.svcCtx.Mongo.Collection("notifications").CountDocuments(l.ctx, filter)
	if err != nil {
		appmetrics.ObserveRequest("list_notifications", "count_error", time.Since(start))
		return nil, err
	}

	opts := options.Find().
		SetSort(bson.D{{Key: "createdAt", Value: -1}}).
		SetSkip(int64((page - 1) * pageSize)).
		SetLimit(int64(pageSize))

	cursor, err := l.svcCtx.Mongo.Collection("notifications").Find(l.ctx, filter, opts)
	if err != nil {
		appmetrics.ObserveRequest("list_notifications", "find_error", time.Since(start))
		return nil, err
	}
	defer cursor.Close(l.ctx)

	var docs []model.NotificationDoc
	if err := cursor.All(l.ctx, &docs); err != nil {
		appmetrics.ObserveRequest("list_notifications", "decode_error", time.Since(start))
		return nil, err
	}

	items := make([]*notification.Notification, 0, len(docs))
	for _, doc := range docs {
		items = append(items, doc.ToProto())
	}

	// 响应结构刻意保持和旧 Java 分页结果可映射，
	// 便于 gateway-api 无痛切换到 notification-rpc。
	appmetrics.ObserveRequest("list_notifications", "success", time.Since(start))
	return &notification.ListNotificationsResponse{
		Items:    items,
		Total:    total,
		Page:     page,
		PageSize: pageSize,
	}, nil
}
