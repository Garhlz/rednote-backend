package logic

import (
	"context"

	"search-rpc/internal/svc"
	"search-rpc/search"

	"github.com/zeromicro/go-zero/core/logx"
	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/mongo/options"
)

type GetHistoryLogic struct {
	ctx    context.Context
	svcCtx *svc.ServiceContext
	logx.Logger
}

func NewGetHistoryLogic(ctx context.Context, svcCtx *svc.ServiceContext) *GetHistoryLogic {
	return &GetHistoryLogic{
		ctx:    ctx,
		svcCtx: svcCtx,
		Logger: logx.WithContext(ctx),
	}
}

type SearchHistoryDoc struct {
	Keyword string `bson:"keyword"`
}

func (l *GetHistoryLogic) GetHistory(in *search.HistoryRequest) (*search.HistoryResponse, error) {
	collection := l.svcCtx.Mongo.Collection("search_histories")

	// 1. 构建查询选项: 过滤 userId, 按 updatedAt 倒序, 取前 10 条
	filter := bson.M{"userId": in.UserId}
	findOptions := options.Find()
	findOptions.SetSort(bson.D{{Key: "updatedAt", Value: -1}}) // 倒序
	findOptions.SetLimit(10)

	cursor, err := collection.Find(l.ctx, filter, findOptions)
	if err != nil {
		return nil, err
	}
	defer cursor.Close(l.ctx)

	// 2. 遍历结果
	var keywords []string
	for cursor.Next(l.ctx) {
		var doc SearchHistoryDoc
		if err := cursor.Decode(&doc); err == nil {
			keywords = append(keywords, doc.Keyword)
		}
	}

	return &search.HistoryResponse{Keywords: keywords}, nil
}
