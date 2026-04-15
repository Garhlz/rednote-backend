package logic

import (
	"context"
	"errors"
	"time"

	"comment-rpc/comment"
	appmetrics "comment-rpc/internal/metrics"
	"comment-rpc/internal/model"
	"comment-rpc/internal/sensitive"
	"comment-rpc/internal/svc"

	"github.com/zeromicro/go-zero/core/logx"
	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/bson/primitive"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

type CreateCommentLogic struct {
	ctx    context.Context
	svcCtx *svc.ServiceContext
	logx.Logger
}

func NewCreateCommentLogic(ctx context.Context, svcCtx *svc.ServiceContext) *CreateCommentLogic {
	return &CreateCommentLogic{
		ctx:    ctx,
		svcCtx: svcCtx,
		Logger: logx.WithContext(ctx),
	}
}

func (l *CreateCommentLogic) CreateComment(in *comment.CreateCommentRequest) (*comment.Comment, error) {
	start := time.Now()
	if l.svcCtx.Mongo == nil {
		appmetrics.ObserveRequest("create_comment", "mongo_unavailable", time.Since(start))
		return nil, errors.New("mongo unavailable")
	}
	if matched := sensitive.FirstMatch(in.GetContent()); matched != "" {
		appmetrics.ObserveRequest("create_comment", "sensitive_word", time.Since(start))
		return nil, status.Error(codes.InvalidArgument, "评论包含违规词："+matched)
	}

	var post struct {
		UserID int64 `bson:"userId"`
	}
	postID, err := primitive.ObjectIDFromHex(in.GetPostId())
	if err != nil {
		appmetrics.ObserveRequest("create_comment", "invalid_post_id", time.Since(start))
		return nil, err
	}
	if err := l.svcCtx.Mongo.Collection("posts").FindOne(l.ctx, bson.M{"_id": postID}).Decode(&post); err != nil {
		appmetrics.ObserveRequest("create_comment", "post_not_found", time.Since(start))
		return nil, err
	}

	doc := model.CommentDoc{
		PostID:       in.GetPostId(),
		UserID:       in.GetCurrentUserId(),
		UserNickname: in.GetUserNickname(),
		UserAvatar:   in.GetUserAvatar(),
		Content:      in.GetContent(),
		ReplyCount:   0,
		LikeCount:    0,
		CreatedAt:    time.Now(),
	}

	if in.GetParentId() != "" {
		parentID, err := primitive.ObjectIDFromHex(in.GetParentId())
		if err != nil {
			appmetrics.ObserveRequest("create_comment", "invalid_parent_id", time.Since(start))
			return nil, err
		}

		var parent model.CommentDoc
		if err := l.svcCtx.Mongo.Collection("comments").FindOne(l.ctx, bson.M{"_id": parentID}).Decode(&parent); err != nil {
			appmetrics.ObserveRequest("create_comment", "parent_not_found", time.Since(start))
			return nil, err
		}

		rootID := parent.ID
		if parent.ParentID != "" {
			rootID, err = primitive.ObjectIDFromHex(parent.ParentID)
			if err != nil {
				appmetrics.ObserveRequest("create_comment", "invalid_root_id", time.Since(start))
				return nil, err
			}
		}

		doc.ParentID = rootID.Hex()
		doc.ReplyToUserID = parent.UserID
		doc.ReplyToUserNickname = parent.UserNickname

		_, err = l.svcCtx.Mongo.Collection("comments").UpdateOne(
			l.ctx,
			bson.M{"_id": rootID},
			bson.M{"$inc": bson.M{"replyCount": 1}},
		)
		if err != nil {
			appmetrics.ObserveRequest("create_comment", "reply_count_update_error", time.Since(start))
			return nil, err
		}
	}

	result, err := l.svcCtx.Mongo.Collection("comments").InsertOne(l.ctx, doc)
	if err != nil {
		appmetrics.ObserveRequest("create_comment", "insert_error", time.Since(start))
		return nil, err
	}
	if oid, ok := result.InsertedID.(primitive.ObjectID); ok {
		doc.ID = oid
	}

	if err := publishCommentEvent(l.ctx, l.svcCtx.MqChan, commentRoutingKeyCreate, &CommentEvent{
		Type:          "CREATE",
		CommentId:     doc.ID.Hex(),
		PostId:        doc.PostID,
		UserId:        doc.UserID,
		UserNickname:  doc.UserNickname,
		Content:       doc.Content,
		PostAuthorId:  post.UserID,
		ReplyToUserId: doc.ReplyToUserID,
		ParentId:      doc.ParentID,
	}); err != nil {
		rollbackCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()

		if _, delErr := l.svcCtx.Mongo.Collection("comments").DeleteOne(rollbackCtx, bson.M{"_id": doc.ID}); delErr != nil {
			l.Errorf("rollback delete inserted comment failed commentId=%s err=%v", doc.ID.Hex(), delErr)
		}

		if doc.ParentID != "" {
			rootID, parseErr := primitive.ObjectIDFromHex(doc.ParentID)
			if parseErr == nil {
				if _, decErr := l.svcCtx.Mongo.Collection("comments").UpdateOne(
					rollbackCtx,
					bson.M{"_id": rootID, "replyCount": bson.M{"$gt": 0}},
					bson.M{"$inc": bson.M{"replyCount": -1}},
				); decErr != nil {
					l.Errorf("rollback root replyCount failed rootId=%s err=%v", doc.ParentID, decErr)
				}
			}
		}

		appmetrics.ObserveRequest("create_comment", "mq_publish_error", time.Since(start))
		return nil, status.Error(codes.Unavailable, "评论创建失败，请稍后重试")
	}
	appmetrics.ObserveRequest("create_comment", "success", time.Since(start))

	return doc.ToProto(false), nil
}
