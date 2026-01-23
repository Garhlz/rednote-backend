package logic

import (
	"context"
	"strconv"

	"interaction-rpc/interaction"
	"interaction-rpc/internal/svc"

	"github.com/zeromicro/go-zero/core/logx"
	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/mongo/options"
)

type BatchPostStatsLogic struct {
	ctx    context.Context
	svcCtx *svc.ServiceContext
	logx.Logger
}

func NewBatchPostStatsLogic(ctx context.Context, svcCtx *svc.ServiceContext) *BatchPostStatsLogic {
	return &BatchPostStatsLogic{
		ctx:    ctx,
		svcCtx: svcCtx,
		Logger: logx.WithContext(ctx),
	}
}

func (l *BatchPostStatsLogic) BatchPostStats(in *interaction.BatchPostStatsRequest) (*interaction.BatchPostStatsResponse, error) {
	if len(in.GetPostIds()) == 0 {
		return &interaction.BatchPostStatsResponse{Stats: map[string]*interaction.PostStats{}}, nil
	}

	userId := in.GetUserId()
	stats := make(map[string]*interaction.PostStats, len(in.GetPostIds()))
	for _, postId := range in.GetPostIds() {
		stats[postId] = &interaction.PostStats{}
	}

	for _, postId := range in.GetPostIds() {
		collectCount, err := l.svcCtx.Redis.ScardCtx(l.ctx, KeyPostCollectSet+postId)
		if err != nil {
			l.Logger.Errorf("collect count error: %v", err)
		} else {
			stats[postId].CollectCount = int32(collectCount)
		}

		if userId > 0 {
			userKey := strconv.FormatInt(userId, 10)
			member, err := l.svcCtx.Redis.SismemberCtx(l.ctx, KeyPostCollectSet+postId, userKey)
			if err != nil {
				l.Logger.Errorf("collect status error: %v", err)
			} else {
				stats[postId].IsCollected = member
			}
			liked, err := l.svcCtx.Redis.SismemberCtx(l.ctx, KeyPostLikeSet+postId, userKey)
			if err != nil {
				l.Logger.Errorf("like status error: %v", err)
			} else {
				stats[postId].IsLiked = liked
			}
		}
	}

	if l.svcCtx.Mongo != nil {
		type postDoc struct {
			ID           string `bson:"_id"`
			CommentCount int32  `bson:"commentCount"`
		}
		cursor, err := l.svcCtx.Mongo.Collection("posts").Find(
			l.ctx,
			bson.M{"_id": bson.M{"$in": in.GetPostIds()}},
			options.Find().SetProjection(bson.M{"commentCount": 1}),
		)
		if err != nil {
			l.Logger.Errorf("mongo query error: %v", err)
		} else {
			defer cursor.Close(l.ctx)
			for cursor.Next(l.ctx) {
				var doc postDoc
				if err := cursor.Decode(&doc); err != nil {
					continue
				}
				if st, ok := stats[doc.ID]; ok {
					st.CommentCount = doc.CommentCount
				}
			}
		}
	}

	if userId > 0 && l.svcCtx.Mongo != nil && len(in.GetPostAuthorMap()) > 0 {
		authorSet := make(map[int64]struct{})
		for _, authorId := range in.GetPostAuthorMap() {
			if authorId > 0 {
				authorSet[authorId] = struct{}{}
			}
		}
		if len(authorSet) > 0 {
			authorIds := make([]int64, 0, len(authorSet))
			for id := range authorSet {
				authorIds = append(authorIds, id)
			}
			type followDoc struct {
				TargetUserId int64 `bson:"targetUserId"`
			}
			cursor, err := l.svcCtx.Mongo.Collection("user_follows").Find(
				l.ctx,
				bson.M{"userId": userId, "targetUserId": bson.M{"$in": authorIds}},
				options.Find().SetProjection(bson.M{"targetUserId": 1}),
			)
			if err != nil {
				l.Logger.Errorf("mongo follow query error: %v", err)
			} else {
				defer cursor.Close(l.ctx)
				followed := make(map[int64]struct{})
				for cursor.Next(l.ctx) {
					var doc followDoc
					if err := cursor.Decode(&doc); err != nil {
						continue
					}
					followed[doc.TargetUserId] = struct{}{}
				}
				for postId, authorId := range in.GetPostAuthorMap() {
					if st, ok := stats[postId]; ok {
						_, ok := followed[authorId]
						st.IsFollowed = ok
					}
				}
			}
		}
	}

	return &interaction.BatchPostStatsResponse{Stats: stats}, nil
}
