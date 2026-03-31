package logic

import (
	"context"
	"time"

	appmetrics "notification-rpc/internal/metrics"
	"notification-rpc/internal/svc"
	"notification-rpc/notification"

	"github.com/zeromicro/go-zero/core/logx"
	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

type MarkBatchReadLogic struct {
	ctx    context.Context
	svcCtx *svc.ServiceContext
	logx.Logger
}

func NewMarkBatchReadLogic(ctx context.Context, svcCtx *svc.ServiceContext) *MarkBatchReadLogic {
	return &MarkBatchReadLogic{
		ctx:    ctx,
		svcCtx: svcCtx,
		Logger: logx.WithContext(ctx),
	}
}

// 对应当前 /api/message/read (批量已读)
// 前端传入的是字符串 id 列表，这里需要先转换成 Mongo ObjectID。
func (l *MarkBatchReadLogic) MarkBatchRead(in *notification.MarkBatchReadRequest) (*notification.Empty, error) {
	start := time.Now()
	if len(in.GetIds()) == 0 {
		appmetrics.ObserveRequest("mark_batch_read", "empty_ids", time.Since(start))
		return &notification.Empty{}, nil
	}

	objectIDs := make([]primitive.ObjectID, 0, len(in.GetIds()))
	for _, id := range in.GetIds() {
		oid, err := primitive.ObjectIDFromHex(id)
		if err != nil {
			// 旧接口语义里，单个非法 id 不应该把整个请求打成 500，
			// 所以这里选择跳过，最后如果一个都不合法再按 invalid_ids 计数返回空结果。
			continue
		}
		objectIDs = append(objectIDs, oid)
	}
	if len(objectIDs) == 0 {
		appmetrics.ObserveRequest("mark_batch_read", "invalid_ids", time.Since(start))
		return &notification.Empty{}, nil
	}

	_, err := l.svcCtx.Mongo.Collection("notifications").UpdateMany(
		l.ctx,
		bson.M{
			"_id":        bson.M{"$in": objectIDs},
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
		appmetrics.ObserveRequest("mark_batch_read", "mongo_error", time.Since(start))
		return nil, err
	}

	appmetrics.ObserveRequest("mark_batch_read", "success", time.Since(start))
	return &notification.Empty{}, nil
}
