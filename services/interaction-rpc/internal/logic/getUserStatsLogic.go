package logic

import (
	"context"

	"interaction-rpc/internal/svc"
	"interaction-rpc/interaction"

	"github.com/zeromicro/go-zero/core/logx"
	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/mongo"
)

type GetUserStatsLogic struct {
	ctx    context.Context
	svcCtx *svc.ServiceContext
	logx.Logger
}

func NewGetUserStatsLogic(ctx context.Context, svcCtx *svc.ServiceContext) *GetUserStatsLogic {
	return &GetUserStatsLogic{
		ctx:    ctx,
		svcCtx: svcCtx,
		Logger: logx.WithContext(ctx),
	}
}

func (l *GetUserStatsLogic) GetUserStats(in *interaction.UserStatsRequest) (*interaction.UserStatsResponse, error) {
	if in.GetUserId() <= 0 || l.svcCtx.Mongo == nil {
		return &interaction.UserStatsResponse{}, nil
	}

	followCount := l.countByField("user_follows", "userId", in.GetUserId())
	fanCount := l.countByField("user_follows", "targetUserId", in.GetUserId())
	receivedLikeCount := l.sumPostLikes(in.GetUserId())

	isFollowed := false
	if in.GetViewerId() > 0 {
		count := l.countFollowRelation(in.GetViewerId(), in.GetUserId())
		isFollowed = count > 0
	}

	return &interaction.UserStatsResponse{
		FollowCount:       followCount,
		FanCount:          fanCount,
		ReceivedLikeCount: receivedLikeCount,
		IsFollowed:        isFollowed,
	}, nil
}

func (l *GetUserStatsLogic) countByField(collection, field string, userId int64) int64 {
	coll := l.svcCtx.Mongo.Collection(collection)
	count, err := coll.CountDocuments(l.ctx, bson.M{field: userId})
	if err != nil {
		l.Logger.Errorf("count %s by %s failed: %v", collection, field, err)
		return 0
	}
	return count
}

func (l *GetUserStatsLogic) countFollowRelation(viewerId, targetId int64) int64 {
	coll := l.svcCtx.Mongo.Collection("user_follows")
	count, err := coll.CountDocuments(l.ctx, bson.M{"userId": viewerId, "targetUserId": targetId})
	if err != nil {
		l.Logger.Errorf("count follow relation failed: %v", err)
		return 0
	}
	return count
}

func (l *GetUserStatsLogic) sumPostLikes(userId int64) int64 {
	coll := l.svcCtx.Mongo.Collection("posts")
	pipeline := mongo.Pipeline{
		{{Key: "$match", Value: bson.M{"userId": userId, "isDeleted": 0, "status": 1}}},
		{{Key: "$group", Value: bson.M{"_id": nil, "total": bson.M{"$sum": "$likeCount"}}}},
	}
	cursor, err := coll.Aggregate(l.ctx, pipeline)
	if err != nil {
		l.Logger.Errorf("aggregate post likes failed: %v", err)
		return 0
	}
	defer cursor.Close(l.ctx)

	var result struct {
		Total int64 `bson:"total"`
	}
	if cursor.Next(l.ctx) {
		if err := cursor.Decode(&result); err == nil {
			return result.Total
		}
	}
	return 0
}
