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

func makeListSvcCtx(mt *mtest.T) *svc.ServiceContext {
	return &svc.ServiceContext{Mongo: mt.DB}
}

// ---- 分页参数兜底 ----

func TestListRootComments_PageDefaults(t *testing.T) {
	mt := mtest.New(t, mtest.NewOptions().ClientType(mtest.Mock))

	mt.Run("page<1 兜底为 1，pageSize<1 兜底为 20", func(mt *mtest.T) {
		// CountDocuments
		mt.AddMockResponses(bson.D{
			{Key: "ok", Value: 1},
			{Key: "cursor", Value: bson.D{
				{Key: "firstBatch", Value: bson.A{bson.D{{Key: "n", Value: int32(0)}}}},
				{Key: "id", Value: int64(0)},
				{Key: "ns", Value: "rednote.comments"},
			}},
		})
		// Find 返回空列表
		mt.AddMockResponses(mtest.CreateCursorResponse(0, "rednote.comments", mtest.FirstBatch))

		logic := NewListRootCommentsLogic(context.Background(), makeListSvcCtx(mt))
		resp, err := logic.ListRootComments(&comment.ListRootCommentsRequest{
			PostId:   primitive.NewObjectID().Hex(),
			Page:     0,
			PageSize: 0,
		})

		require.NoError(t, err)
		assert.Equal(t, int32(1), resp.GetPage(), "page<1 应兜底为 1")
		assert.Equal(t, int32(20), resp.GetPageSize(), "pageSize<1 应兜底为 20")
	})
}

// ---- 匿名态不查 comment_likes ----

func TestListRootComments_Anonymous_IsLikedAllFalse(t *testing.T) {
	mt := mtest.New(t, mtest.NewOptions().ClientType(mtest.Mock))

	mt.Run("currentUserId=0 时 isLiked 全为 false，不查 comment_likes", func(mt *mtest.T) {
		postId := primitive.NewObjectID().Hex()
		commentOid := primitive.NewObjectID()

		// CountDocuments
		mt.AddMockResponses(bson.D{
			{Key: "ok", Value: 1},
			{Key: "cursor", Value: bson.D{
				{Key: "firstBatch", Value: bson.A{bson.D{{Key: "n", Value: int32(1)}}}},
				{Key: "id", Value: int64(0)},
				{Key: "ns", Value: "rednote.comments"},
			}},
		})
		// Find 返回 1 条评论
		mt.AddMockResponses(mtest.CreateCursorResponse(0, "rednote.comments", mtest.FirstBatch,
			bson.D{
				{Key: "_id", Value: commentOid},
				{Key: "postId", Value: postId},
				{Key: "userId", Value: int64(10)},
				{Key: "content", Value: "测试评论"},
				{Key: "parentId", Value: ""},
				{Key: "replyCount", Value: int32(0)},
				{Key: "likeCount", Value: int32(5)},
			},
		))
		// 匿名态不会查 comment_likes，无需额外 mock

		logic := NewListRootCommentsLogic(context.Background(), makeListSvcCtx(mt))
		resp, err := logic.ListRootComments(&comment.ListRootCommentsRequest{
			PostId:        postId,
			Page:          1,
			PageSize:      20,
			CurrentUserId: 0, // 匿名
		})

		require.NoError(t, err)
		require.Len(t, resp.GetItems(), 1)
		assert.False(t, resp.GetItems()[0].GetIsLiked(), "匿名用户 isLiked 应为 false")
	})
}

// ---- 登录态：isLiked 从 comment_likes 正确聚合 ----

func TestListRootComments_LoggedIn_IsLikedFromMongo(t *testing.T) {
	mt := mtest.New(t, mtest.NewOptions().ClientType(mtest.Mock))

	mt.Run("登录态下 isLiked 从 comment_likes 正确聚合", func(mt *mtest.T) {
		postId := primitive.NewObjectID().Hex()
		commentOid1 := primitive.NewObjectID()
		commentOid2 := primitive.NewObjectID()

		// CountDocuments
		mt.AddMockResponses(bson.D{
			{Key: "ok", Value: 1},
			{Key: "cursor", Value: bson.D{
				{Key: "firstBatch", Value: bson.A{bson.D{{Key: "n", Value: int32(2)}}}},
				{Key: "id", Value: int64(0)},
				{Key: "ns", Value: "rednote.comments"},
			}},
		})
		// Find 返回 2 条评论
		mt.AddMockResponses(mtest.CreateCursorResponse(0, "rednote.comments", mtest.FirstBatch,
			bson.D{
				{Key: "_id", Value: commentOid1},
				{Key: "postId", Value: postId},
				{Key: "userId", Value: int64(10)},
				{Key: "content", Value: "评论1"},
				{Key: "parentId", Value: ""},
				{Key: "replyCount", Value: int32(0)},
			},
			bson.D{
				{Key: "_id", Value: commentOid2},
				{Key: "postId", Value: postId},
				{Key: "userId", Value: int64(11)},
				{Key: "content", Value: "评论2"},
				{Key: "parentId", Value: ""},
				{Key: "replyCount", Value: int32(0)},
			},
		))
		// Find("comment_likes") — 当前用户仅点赞了 commentOid1
		mt.AddMockResponses(mtest.CreateCursorResponse(0, "rednote.comment_likes", mtest.FirstBatch,
			bson.D{
				{Key: "_id", Value: primitive.NewObjectID()},
				{Key: "userId", Value: int64(42)},
				{Key: "commentId", Value: commentOid1.Hex()},
			},
		))

		logic := NewListRootCommentsLogic(context.Background(), makeListSvcCtx(mt))
		resp, err := logic.ListRootComments(&comment.ListRootCommentsRequest{
			PostId:        postId,
			Page:          1,
			PageSize:      20,
			CurrentUserId: 42,
		})

		require.NoError(t, err)
		require.Len(t, resp.GetItems(), 2)

		// 找到 commentOid1 和 commentOid2 的 isLiked 状态
		likedMap := map[string]bool{}
		for _, item := range resp.GetItems() {
			likedMap[item.GetId()] = item.GetIsLiked()
		}
		assert.True(t, likedMap[commentOid1.Hex()], "commentOid1 应为已点赞")
		assert.False(t, likedMap[commentOid2.Hex()], "commentOid2 应为未点赞")
	})
}

// ---- 空列表返回 ----

func TestListRootComments_EmptyResult(t *testing.T) {
	mt := mtest.New(t, mtest.NewOptions().ClientType(mtest.Mock))

	mt.Run("无评论时 items 为空数组，total 为 0", func(mt *mtest.T) {
		// CountDocuments 返回 0
		mt.AddMockResponses(bson.D{
			{Key: "ok", Value: 1},
			{Key: "cursor", Value: bson.D{
				{Key: "firstBatch", Value: bson.A{bson.D{{Key: "n", Value: int32(0)}}}},
				{Key: "id", Value: int64(0)},
				{Key: "ns", Value: "rednote.comments"},
			}},
		})
		// Find 返回空
		mt.AddMockResponses(mtest.CreateCursorResponse(0, "rednote.comments", mtest.FirstBatch))

		logic := NewListRootCommentsLogic(context.Background(), makeListSvcCtx(mt))
		resp, err := logic.ListRootComments(&comment.ListRootCommentsRequest{
			PostId:   primitive.NewObjectID().Hex(),
			Page:     1,
			PageSize: 20,
		})

		require.NoError(t, err)
		assert.Equal(t, int64(0), resp.GetTotal())
		assert.Empty(t, resp.GetItems(), "无评论时 items 应为空")
	})
}
