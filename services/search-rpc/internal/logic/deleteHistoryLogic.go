package logic

import (
	"context"

	"search-rpc/internal/svc"
	"search-rpc/search"

	"github.com/zeromicro/go-zero/core/logx"
	"go.mongodb.org/mongo-driver/bson"
)

type DeleteHistoryLogic struct {
	ctx    context.Context
	svcCtx *svc.ServiceContext
	logx.Logger
}

func NewDeleteHistoryLogic(ctx context.Context, svcCtx *svc.ServiceContext) *DeleteHistoryLogic {
	return &DeleteHistoryLogic{
		ctx:    ctx,
		svcCtx: svcCtx,
		Logger: logx.WithContext(ctx),
	}
}

func (l *DeleteHistoryLogic) DeleteHistory(in *search.ClearHistoryRequest) (*search.Empty, error) {
	collection := l.svcCtx.Mongo.Collection("search_histories")

	var filter bson.M
	if in.Keyword == "" {
		// 删除该用户所有历史
		filter = bson.M{"userId": in.UserId}
		_, err := collection.DeleteMany(l.ctx, filter)
		if err != nil {
			return nil, err
		}
	} else {
		// 删除单条
		filter = bson.M{"userId": in.UserId, "keyword": in.Keyword}
		_, err := collection.DeleteOne(l.ctx, filter)
		if err != nil {
			return nil, err
		}
	}

	return &search.Empty{}, nil
}
