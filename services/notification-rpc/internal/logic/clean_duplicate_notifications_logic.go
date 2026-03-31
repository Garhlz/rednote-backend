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
	"go.mongodb.org/mongo-driver/bson/primitive"
	"go.mongodb.org/mongo-driver/mongo"
)

type CleanDuplicateNotificationsLogic struct {
	ctx    context.Context
	svcCtx *svc.ServiceContext
	logx.Logger
}

type duplicateGroup struct {
	LatestID primitive.ObjectID   `bson:"latestId"`
	AllIDs   []primitive.ObjectID `bson:"allIds"`
}

func NewCleanDuplicateNotificationsLogic(ctx context.Context, svcCtx *svc.ServiceContext) *CleanDuplicateNotificationsLogic {
	return &CleanDuplicateNotificationsLogic{
		ctx:    ctx,
		svcCtx: svcCtx,
		Logger: logx.WithContext(ctx),
	}
}

// 仅供管理/数据修复使用，便于迁移旧数据时执行一次清洗。
// 该接口不是主业务链路的一部分，主要用于把历史库里重复的状态型通知压缩成一条。
func (l *CleanDuplicateNotificationsLogic) CleanDuplicateNotifications(in *notification.CleanDuplicateNotificationsRequest) (*notification.CleanDuplicateNotificationsResponse, error) {
	start := time.Now()
	typesToCheck := []string{
		model.FormatNotificationType(notification.NotificationType_LIKE_POST),
		model.FormatNotificationType(notification.NotificationType_COLLECT_POST),
		model.FormatNotificationType(notification.NotificationType_RATE_POST),
		model.FormatNotificationType(notification.NotificationType_FOLLOW),
		model.FormatNotificationType(notification.NotificationType_LIKE_COMMENT),
	}

	pipeline := mongo.Pipeline{
		bson.D{{Key: "$match", Value: bson.M{"type": bson.M{"$in": typesToCheck}}}},
		bson.D{{Key: "$sort", Value: bson.D{{Key: "createdAt", Value: -1}}}},
		bson.D{{Key: "$group", Value: bson.D{
			{Key: "_id", Value: bson.D{
				{Key: "receiverId", Value: "$receiverId"},
				{Key: "senderId", Value: "$senderId"},
				{Key: "type", Value: "$type"},
				{Key: "targetId", Value: "$targetId"},
			}},
			{Key: "latestId", Value: bson.D{{Key: "$first", Value: "$_id"}}},
			{Key: "allIds", Value: bson.D{{Key: "$push", Value: "$_id"}}},
		}}},
	}

	cursor, err := l.svcCtx.Mongo.Collection("notifications").Aggregate(l.ctx, pipeline)
	if err != nil {
		appmetrics.ObserveRequest("clean_duplicate_notifications", "aggregate_error", time.Since(start))
		return nil, err
	}
	defer cursor.Close(l.ctx)

	var groups []duplicateGroup
	if err := cursor.All(l.ctx, &groups); err != nil {
		appmetrics.ObserveRequest("clean_duplicate_notifications", "decode_error", time.Since(start))
		return nil, err
	}

	var idsToDelete []primitive.ObjectID
	for _, group := range groups {
		if len(group.AllIDs) <= 1 {
			continue
		}
		for _, id := range group.AllIDs {
			if id != group.LatestID {
				idsToDelete = append(idsToDelete, id)
			}
		}
	}

	if len(idsToDelete) > 0 {
		const batchSize = 1000
		// 大批量删除按批次做，避免单次 DeleteMany 参数过大。
		for batchStart := 0; batchStart < len(idsToDelete); batchStart += batchSize {
			end := batchStart + batchSize
			if end > len(idsToDelete) {
				end = len(idsToDelete)
			}
			_, err := l.svcCtx.Mongo.Collection("notifications").DeleteMany(
				l.ctx,
				bson.M{"_id": bson.M{"$in": idsToDelete[batchStart:end]}},
			)
			if err != nil {
				appmetrics.ObserveRequest("clean_duplicate_notifications", "delete_error", time.Since(start))
				return nil, err
			}
		}
	}

	appmetrics.ObserveRequest("clean_duplicate_notifications", "success", time.Since(start))
	return &notification.CleanDuplicateNotificationsResponse{
		DeletedCount: int64(len(idsToDelete)),
	}, nil
}
