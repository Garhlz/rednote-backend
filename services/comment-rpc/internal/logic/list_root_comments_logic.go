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

type ListRootCommentsLogic struct {
	ctx    context.Context
	svcCtx *svc.ServiceContext
	logx.Logger
}

func NewListRootCommentsLogic(ctx context.Context, svcCtx *svc.ServiceContext) *ListRootCommentsLogic {
	return &ListRootCommentsLogic{
		ctx:    ctx,
		svcCtx: svcCtx,
		Logger: logx.WithContext(ctx),
	}
}

func (l *ListRootCommentsLogic) ListRootComments(in *comment.ListRootCommentsRequest) (*comment.CommentPageResponse, error) {
	start := time.Now()
	page := in.GetPage()
	if page < 1 {
		page = 1
	}
	pageSize := in.GetPageSize()
	if pageSize < 1 {
		pageSize = 20
	}

	filter := bson.M{"postId": in.GetPostId(), "parentId": bson.M{"$in": []any{nil, ""}}}
	total, err := l.svcCtx.Mongo.Collection("comments").CountDocuments(l.ctx, filter)
	if err != nil {
		appmetrics.ObserveRequest("list_root_comments", "count_error", time.Since(start))
		return nil, err
	}

	cursor, err := l.svcCtx.Mongo.Collection("comments").Find(
		l.ctx,
		filter,
		options.Find().
			SetSort(bson.D{{Key: "createdAt", Value: -1}}).
			SetSkip(int64((page-1)*pageSize)).
			SetLimit(int64(pageSize)),
	)
	if err != nil {
		appmetrics.ObserveRequest("list_root_comments", "find_error", time.Since(start))
		return nil, err
	}
	defer cursor.Close(l.ctx)

	var roots []model.CommentDoc
	if err := cursor.All(l.ctx, &roots); err != nil {
		appmetrics.ObserveRequest("list_root_comments", "decode_error", time.Since(start))
		return nil, err
	}

	liked, err := l.loadLikedSet(in.GetCurrentUserId(), roots)
	if err != nil {
		appmetrics.ObserveRequest("list_root_comments", "like_lookup_error", time.Since(start))
		return nil, err
	}

	items := make([]*comment.Comment, 0, len(roots))
	for _, root := range roots {
		item := root.ToProto(liked[root.ID.Hex()])
		if root.ReplyCount > 0 {
			children, err := l.loadPreviewChildren(root.ID.Hex(), in.GetCurrentUserId())
			if err != nil {
				appmetrics.ObserveRequest("list_root_comments", "preview_load_error", time.Since(start))
				return nil, err
			}
			item.ChildComments = children
		}
		items = append(items, item)
	}

	appmetrics.ObserveRequest("list_root_comments", "success", time.Since(start))
	return &comment.CommentPageResponse{
		Items:    items,
		Total:    total,
		Page:     page,
		PageSize: pageSize,
	}, nil
}

func (l *ListRootCommentsLogic) loadPreviewChildren(rootID string, currentUserID int64) ([]*comment.Comment, error) {
	cursor, err := l.svcCtx.Mongo.Collection("comments").Find(
		l.ctx,
		bson.M{"parentId": rootID},
		options.Find().
			SetSort(bson.D{{Key: "likeCount", Value: -1}}).
			SetLimit(3),
	)
	if err != nil {
		return nil, err
	}
	defer cursor.Close(l.ctx)

	var children []model.CommentDoc
	if err := cursor.All(l.ctx, &children); err != nil {
		return nil, err
	}

	liked, err := l.loadLikedSet(currentUserID, children)
	if err != nil {
		return nil, err
	}

	result := make([]*comment.Comment, 0, len(children))
	for _, child := range children {
		result = append(result, child.ToProto(liked[child.ID.Hex()]))
	}
	return result, nil
}

func (l *ListRootCommentsLogic) loadLikedSet(currentUserID int64, comments []model.CommentDoc) (map[string]bool, error) {
	result := make(map[string]bool)
	if currentUserID <= 0 || len(comments) == 0 {
		return result, nil
	}

	ids := make([]string, 0, len(comments))
	for _, item := range comments {
		ids = append(ids, item.ID.Hex())
	}

	cursor, err := l.svcCtx.Mongo.Collection("comment_likes").Find(
		l.ctx,
		bson.M{"userId": currentUserID, "commentId": bson.M{"$in": ids}},
	)
	if err != nil {
		return nil, err
	}
	defer cursor.Close(l.ctx)

	var likes []model.CommentLikeDoc
	if err := cursor.All(l.ctx, &likes); err != nil {
		return nil, err
	}
	for _, like := range likes {
		result[like.CommentID] = true
	}
	return result, nil
}
