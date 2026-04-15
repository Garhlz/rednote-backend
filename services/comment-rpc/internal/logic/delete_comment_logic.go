package logic

import (
	"context"
	"errors"
	"time"

	"comment-rpc/comment"
	appmetrics "comment-rpc/internal/metrics"
	"comment-rpc/internal/model"
	"comment-rpc/internal/svc"

	"github.com/zeromicro/go-zero/core/logx"
	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

type DeleteCommentLogic struct {
	ctx    context.Context
	svcCtx *svc.ServiceContext
	logx.Logger
}

func NewDeleteCommentLogic(ctx context.Context, svcCtx *svc.ServiceContext) *DeleteCommentLogic {
	return &DeleteCommentLogic{
		ctx:    ctx,
		svcCtx: svcCtx,
		Logger: logx.WithContext(ctx),
	}
}

func (l *DeleteCommentLogic) DeleteComment(in *comment.DeleteCommentRequest) (*comment.Empty, error) {
	start := time.Now()
	if l.svcCtx.Mongo == nil {
		appmetrics.ObserveRequest("delete_comment", "mongo_unavailable", time.Since(start))
		return nil, errors.New("mongo unavailable")
	}

	commentID, err := primitive.ObjectIDFromHex(in.GetCommentId())
	if err != nil {
		appmetrics.ObserveRequest("delete_comment", "invalid_comment_id", time.Since(start))
		return nil, err
	}

	var doc model.CommentDoc
	if err := l.svcCtx.Mongo.Collection("comments").FindOne(l.ctx, bson.M{"_id": commentID}).Decode(&doc); err != nil {
		appmetrics.ObserveRequest("delete_comment", "comment_not_found", time.Since(start))
		return nil, err
	}

	currentUserID := in.GetCurrentUserId()
	role := in.GetCurrentUserRole()
	isAdmin := role == "ADMIN"
	isCommentAuthor := doc.UserID == currentUserID
	isPostAuthor := false

	postID, err := primitive.ObjectIDFromHex(doc.PostID)
	if err != nil {
		appmetrics.ObserveRequest("delete_comment", "invalid_post_id", time.Since(start))
		return nil, err
	}
	var post struct {
		UserID int64 `bson:"userId"`
	}
	if err := l.svcCtx.Mongo.Collection("posts").FindOne(l.ctx, bson.M{"_id": postID}).Decode(&post); err == nil {
		isPostAuthor = post.UserID == currentUserID
	}
	if !isAdmin && !isCommentAuthor && !isPostAuthor {
		appmetrics.ObserveRequest("delete_comment", "permission_denied", time.Since(start))
		return nil, errors.New("permission denied")
	}

	// 无论软删还是硬删，都优先清理当前评论自己的点赞关系。
	// 这样即便后续 MQ 事件丢失，也不会留下“已删除评论仍有点赞”的脏状态。
	_, err = l.svcCtx.Mongo.Collection("comment_likes").DeleteMany(l.ctx, bson.M{"commentId": in.GetCommentId()})
	if err != nil {
		appmetrics.ObserveRequest("delete_comment", "like_cleanup_error", time.Since(start))
		return nil, err
	}

	if doc.ParentID == "" {
		_, err = l.svcCtx.Mongo.Collection("comments").UpdateOne(
			l.ctx,
			bson.M{"_id": commentID},
			bson.M{"$set": bson.M{"content": "该评论已删除"}},
		)
	} else {
		_, err = l.svcCtx.Mongo.Collection("comments").DeleteOne(l.ctx, bson.M{"_id": commentID})
	}
	if err != nil {
		appmetrics.ObserveRequest("delete_comment", "mongo_error", time.Since(start))
		return nil, err
	}

	// 只有物理删除的二级评论才发 DELETE 事件。
	// 一级评论是软删除，占位仍会保留在列表中，不能再让下游重复扣减帖子评论数。
	if doc.ParentID != "" {
		if err := publishCommentEvent(l.ctx, l.svcCtx.MqChan, commentRoutingKeyDelete, &CommentEvent{
			Type:      "DELETE",
			CommentId: in.GetCommentId(),
			PostId:    doc.PostID,
			ParentId:  doc.ParentID,
		}); err != nil {
			appmetrics.ObserveRequest("delete_comment", "mq_publish_error", time.Since(start))
			return nil, err
		}
	}
	appmetrics.ObserveRequest("delete_comment", "success", time.Since(start))

	return &comment.Empty{}, nil
}
