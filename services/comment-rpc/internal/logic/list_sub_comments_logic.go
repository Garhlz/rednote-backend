package logic

import (
	"context"
	"time"

	"comment-rpc/comment"
	appmetrics "comment-rpc/internal/metrics"
	"comment-rpc/internal/model"
	"comment-rpc/internal/svc"

	"github.com/zeromicro/go-zero/core/logx"
	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/mongo/options"
)

type ListSubCommentsLogic struct {
	ctx    context.Context
	svcCtx *svc.ServiceContext
	logx.Logger
}

func NewListSubCommentsLogic(ctx context.Context, svcCtx *svc.ServiceContext) *ListSubCommentsLogic {
	return &ListSubCommentsLogic{
		ctx:    ctx,
		svcCtx: svcCtx,
		Logger: logx.WithContext(ctx),
	}
}

func (l *ListSubCommentsLogic) ListSubComments(in *comment.ListSubCommentsRequest) (*comment.CommentPageResponse, error) {
	start := time.Now()
	page := in.GetPage()
	if page < 1 {
		page = 1
	}
	pageSize := in.GetPageSize()
	if pageSize < 1 {
		pageSize = 20
	}

	filter := bson.M{"parentId": in.GetRootCommentId()}
	total, err := l.svcCtx.Mongo.Collection("comments").CountDocuments(l.ctx, filter)
	if err != nil {
		appmetrics.ObserveRequest("list_sub_comments", "count_error", time.Since(start))
		return nil, err
	}

	cursor, err := l.svcCtx.Mongo.Collection("comments").Find(
		l.ctx,
		filter,
		options.Find().
			SetSort(bson.D{{Key: "createdAt", Value: 1}}).
			SetSkip(int64((page-1)*pageSize)).
			SetLimit(int64(pageSize)),
	)
	if err != nil {
		appmetrics.ObserveRequest("list_sub_comments", "find_error", time.Since(start))
		return nil, err
	}
	defer cursor.Close(l.ctx)

	var children []model.CommentDoc
	if err := cursor.All(l.ctx, &children); err != nil {
		appmetrics.ObserveRequest("list_sub_comments", "decode_error", time.Since(start))
		return nil, err
	}

	liked := make(map[string]bool)
	if in.GetCurrentUserId() > 0 && len(children) > 0 {
		ids := make([]string, 0, len(children))
		for _, item := range children {
			ids = append(ids, item.ID.Hex())
		}
		likeCursor, err := l.svcCtx.Mongo.Collection("comment_likes").Find(
			l.ctx,
			bson.M{"userId": in.GetCurrentUserId(), "commentId": bson.M{"$in": ids}},
		)
		if err == nil {
			defer likeCursor.Close(l.ctx)
			var likes []model.CommentLikeDoc
			if err := likeCursor.All(l.ctx, &likes); err == nil {
				for _, like := range likes {
					liked[like.CommentID] = true
				}
			}
		}
	}

	items := make([]*comment.Comment, 0, len(children))
	for _, child := range children {
		items = append(items, child.ToProto(liked[child.ID.Hex()]))
	}

	appmetrics.ObserveRequest("list_sub_comments", "success", time.Since(start))
	return &comment.CommentPageResponse{
		Items:    items,
		Total:    total,
		Page:     page,
		PageSize: pageSize,
	}, nil
}
