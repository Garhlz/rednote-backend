package logic

import (
	"context"
	"testing"

	"comment-rpc/comment"
	"comment-rpc/internal/svc"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/bson/primitive"
	"go.mongodb.org/mongo-driver/mongo/integration/mtest"
)

func makeDeleteSvcCtx(mt *mtest.T) *svc.ServiceContext {
	return &svc.ServiceContext{
		Mongo:  mt.DB,
		MqChan: nil, // nil channel：publishCommentEvent 直接返回 nil
	}
}

// ---- 权限拒绝 ----

func TestDeleteComment_MongoUnavailable_ReturnsError(t *testing.T) {
	svcCtx := &svc.ServiceContext{Mongo: nil}
	logic := NewDeleteCommentLogic(context.Background(), svcCtx)
	_, err := logic.DeleteComment(&comment.DeleteCommentRequest{
		CommentId: primitive.NewObjectID().Hex(),
	})
	require.Error(t, err)
	assert.Contains(t, err.Error(), "mongo unavailable")
}

func TestDeleteComment_InvalidCommentId_ReturnsError(t *testing.T) {
	mt := mtest.New(t, mtest.NewOptions().ClientType(mtest.Mock))
	mt.Run("非法 commentId 格式应返回错误", func(mt *mtest.T) {
		logic := NewDeleteCommentLogic(context.Background(), makeDeleteSvcCtx(mt))
		_, err := logic.DeleteComment(&comment.DeleteCommentRequest{
			CommentId: "not-an-objectid",
		})
		require.Error(t, err)
	})
}

func TestDeleteComment_PermissionDenied_ReturnsError(t *testing.T) {
	mt := mtest.New(t, mtest.NewOptions().ClientType(mtest.Mock))

	mt.Run("既非管理员、非评论作者、也非帖主时应拒绝", func(mt *mtest.T) {
		commentOid := primitive.NewObjectID()
		postOid := primitive.NewObjectID()

		// 1. FindOne("comments") — 评论作者为 userId=10
		mt.AddMockResponses(mtest.CreateCursorResponse(0, "rednote.comments", mtest.FirstBatch,
			bson.D{
				{Key: "_id", Value: commentOid},
				{Key: "userId", Value: int64(10)},
				{Key: "postId", Value: postOid.Hex()},
				{Key: "parentId", Value: ""},
			},
		))
		// 2. FindOne("posts") — 帖主为 userId=20
		mt.AddMockResponses(mtest.CreateCursorResponse(0, "rednote.posts", mtest.FirstBatch,
			bson.D{{Key: "userId", Value: int64(20)}},
		))

		logic := NewDeleteCommentLogic(context.Background(), makeDeleteSvcCtx(mt))
		_, err := logic.DeleteComment(&comment.DeleteCommentRequest{
			CommentId:       commentOid.Hex(),
			CurrentUserId:   99, // 既不是 10（评论作者）也不是 20（帖主）
			CurrentUserRole: "USER",
		})

		require.Error(t, err)
		assert.Contains(t, err.Error(), "permission denied")
	})
}

func TestDeleteComment_AdminCanDelete(t *testing.T) {
	mt := mtest.New(t, mtest.NewOptions().ClientType(mtest.Mock))

	mt.Run("管理员可删除任意评论（根评论→软删）", func(mt *mtest.T) {
		commentOid := primitive.NewObjectID()
		postOid := primitive.NewObjectID()

		// FindOne("comments")
		mt.AddMockResponses(mtest.CreateCursorResponse(0, "rednote.comments", mtest.FirstBatch,
			bson.D{
				{Key: "_id", Value: commentOid},
				{Key: "userId", Value: int64(10)},
				{Key: "postId", Value: postOid.Hex()},
				{Key: "parentId", Value: ""},
			},
		))
		// FindOne("posts") — 帖主非当前用户，但管理员不需要
		mt.AddMockResponses(mtest.CreateCursorResponse(0, "rednote.posts", mtest.FirstBatch,
			bson.D{{Key: "userId", Value: int64(20)}},
		))
		// DeleteMany("comment_likes")
		mt.AddMockResponses(bson.D{{Key: "ok", Value: 1}, {Key: "n", Value: 0}})
		// UpdateOne("comments") 软删
		mt.AddMockResponses(bson.D{{Key: "ok", Value: 1}, {Key: "n", Value: 1}, {Key: "nModified", Value: 1}})

		logic := NewDeleteCommentLogic(context.Background(), makeDeleteSvcCtx(mt))
		_, err := logic.DeleteComment(&comment.DeleteCommentRequest{
			CommentId:       commentOid.Hex(),
			CurrentUserId:   999,
			CurrentUserRole: "ADMIN",
		})

		require.NoError(t, err, "管理员应能成功删除评论")
	})
}

// ---- 根评论软删 ----

func TestDeleteComment_RootComment_SoftDeleted(t *testing.T) {
	mt := mtest.New(t, mtest.NewOptions().ClientType(mtest.Mock))

	mt.Run("根评论（parentId 为空）应执行软删，不发 MQ 事件", func(mt *mtest.T) {
		commentOid := primitive.NewObjectID()
		postOid := primitive.NewObjectID()

		// FindOne("comments")
		mt.AddMockResponses(mtest.CreateCursorResponse(0, "rednote.comments", mtest.FirstBatch,
			bson.D{
				{Key: "_id", Value: commentOid},
				{Key: "userId", Value: int64(42)},
				{Key: "postId", Value: postOid.Hex()},
				{Key: "parentId", Value: ""},
			},
		))
		// FindOne("posts") — 帖主与评论作者相同
		mt.AddMockResponses(mtest.CreateCursorResponse(0, "rednote.posts", mtest.FirstBatch,
			bson.D{{Key: "userId", Value: int64(42)}},
		))
		// DeleteMany("comment_likes") 清理点赞
		mt.AddMockResponses(bson.D{{Key: "ok", Value: 1}, {Key: "n", Value: 2}})
		// UpdateOne("comments") 软删（content → "该评论已删除"）
		mt.AddMockResponses(bson.D{{Key: "ok", Value: 1}, {Key: "n", Value: 1}, {Key: "nModified", Value: 1}})
		// 注意：根评论软删后不发 MQ，此处不需要额外 mock

		logic := NewDeleteCommentLogic(context.Background(), makeDeleteSvcCtx(mt))
		_, err := logic.DeleteComment(&comment.DeleteCommentRequest{
			CommentId:       commentOid.Hex(),
			CurrentUserId:   42,
			CurrentUserRole: "USER",
		})

		require.NoError(t, err, "根评论软删应成功")
	})
}

// ---- 子评论硬删 ----

func TestDeleteComment_SubComment_HardDeleted(t *testing.T) {
	mt := mtest.New(t, mtest.NewOptions().ClientType(mtest.Mock))

	mt.Run("子评论（parentId 非空）应物理删除，nil MqChan 不报错", func(mt *mtest.T) {
		commentOid := primitive.NewObjectID()
		parentOid := primitive.NewObjectID()
		postOid := primitive.NewObjectID()

		// FindOne("comments") — 子评论
		mt.AddMockResponses(mtest.CreateCursorResponse(0, "rednote.comments", mtest.FirstBatch,
			bson.D{
				{Key: "_id", Value: commentOid},
				{Key: "userId", Value: int64(42)},
				{Key: "postId", Value: postOid.Hex()},
				{Key: "parentId", Value: parentOid.Hex()},
			},
		))
		// FindOne("posts")
		mt.AddMockResponses(mtest.CreateCursorResponse(0, "rednote.posts", mtest.FirstBatch,
			bson.D{{Key: "userId", Value: int64(42)}},
		))
		// DeleteMany("comment_likes")
		mt.AddMockResponses(bson.D{{Key: "ok", Value: 1}, {Key: "n", Value: 0}})
		// DeleteOne("comments") 硬删
		mt.AddMockResponses(bson.D{{Key: "ok", Value: 1}, {Key: "n", Value: 1}})
		// MqChan == nil，publishCommentEvent 直接返回 nil，无需额外 mock

		logic := NewDeleteCommentLogic(context.Background(), makeDeleteSvcCtx(mt))
		_, err := logic.DeleteComment(&comment.DeleteCommentRequest{
			CommentId:       commentOid.Hex(),
			CurrentUserId:   42,
			CurrentUserRole: "USER",
		})

		require.NoError(t, err, "子评论硬删应成功")
	})
}

// ---- comment_likes 清理失败 ----

func TestDeleteComment_LikeCleanupFailed_ReturnsError(t *testing.T) {
	mt := mtest.New(t, mtest.NewOptions().ClientType(mtest.Mock))

	mt.Run("comment_likes 清理失败时应返回错误", func(mt *mtest.T) {
		commentOid := primitive.NewObjectID()
		postOid := primitive.NewObjectID()

		// FindOne("comments")
		mt.AddMockResponses(mtest.CreateCursorResponse(0, "rednote.comments", mtest.FirstBatch,
			bson.D{
				{Key: "_id", Value: commentOid},
				{Key: "userId", Value: int64(42)},
				{Key: "postId", Value: postOid.Hex()},
				{Key: "parentId", Value: ""},
			},
		))
		// FindOne("posts")
		mt.AddMockResponses(mtest.CreateCursorResponse(0, "rednote.posts", mtest.FirstBatch,
			bson.D{{Key: "userId", Value: int64(42)}},
		))
		// DeleteMany("comment_likes") 返回错误
		mt.AddMockResponses(mtest.CreateCommandErrorResponse(mtest.CommandError{
			Code:    11000,
			Message: "simulated like cleanup error",
		}))

		logic := NewDeleteCommentLogic(context.Background(), makeDeleteSvcCtx(mt))
		_, err := logic.DeleteComment(&comment.DeleteCommentRequest{
			CommentId:       commentOid.Hex(),
			CurrentUserId:   42,
			CurrentUserRole: "USER",
		})

		require.Error(t, err, "comment_likes 清理失败时应返回错误")
	})
}
