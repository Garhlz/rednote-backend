package logic

import (
	"context"
	"testing"

	"comment-rpc/comment"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/bson/primitive"
	"go.mongodb.org/mongo-driver/mongo/integration/mtest"
)

// ---- 分页参数兜底 ----

func TestListSubComments_PageDefaults(t *testing.T) {
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
		// Find 返回空
		mt.AddMockResponses(mtest.CreateCursorResponse(0, "rednote.comments", mtest.FirstBatch))

		logic := NewListSubCommentsLogic(context.Background(), makeListSvcCtx(mt))
		resp, err := logic.ListSubComments(&comment.ListSubCommentsRequest{
			RootCommentId: primitive.NewObjectID().Hex(),
			Page:          0,
			PageSize:      0,
		})

		require.NoError(t, err)
		assert.Equal(t, int32(1), resp.GetPage(), "page<1 应兜底为 1")
		assert.Equal(t, int32(20), resp.GetPageSize(), "pageSize<1 应兜底为 20")
	})
}

// ---- 空结果 ----

func TestListSubComments_EmptyResult(t *testing.T) {
	mt := mtest.New(t, mtest.NewOptions().ClientType(mtest.Mock))

	mt.Run("无子评论时 items 为空数组，total 为 0", func(mt *mtest.T) {
		mt.AddMockResponses(bson.D{
			{Key: "ok", Value: 1},
			{Key: "cursor", Value: bson.D{
				{Key: "firstBatch", Value: bson.A{bson.D{{Key: "n", Value: int32(0)}}}},
				{Key: "id", Value: int64(0)},
				{Key: "ns", Value: "rednote.comments"},
			}},
		})
		mt.AddMockResponses(mtest.CreateCursorResponse(0, "rednote.comments", mtest.FirstBatch))

		logic := NewListSubCommentsLogic(context.Background(), makeListSvcCtx(mt))
		resp, err := logic.ListSubComments(&comment.ListSubCommentsRequest{
			RootCommentId: primitive.NewObjectID().Hex(),
			Page:          1,
			PageSize:      20,
		})

		require.NoError(t, err)
		assert.Equal(t, int64(0), resp.GetTotal())
		assert.Empty(t, resp.GetItems(), "无子评论时 items 应为空")
	})
}

// ---- 匿名态不查 comment_likes ----

func TestListSubComments_Anonymous_IsLikedAllFalse(t *testing.T) {
	mt := mtest.New(t, mtest.NewOptions().ClientType(mtest.Mock))

	mt.Run("currentUserId=0 时 isLiked 全为 false，不查 comment_likes", func(mt *mtest.T) {
		rootId := primitive.NewObjectID()
		childOid := primitive.NewObjectID()

		// CountDocuments
		mt.AddMockResponses(bson.D{
			{Key: "ok", Value: 1},
			{Key: "cursor", Value: bson.D{
				{Key: "firstBatch", Value: bson.A{bson.D{{Key: "n", Value: int32(1)}}}},
				{Key: "id", Value: int64(0)},
				{Key: "ns", Value: "rednote.comments"},
			}},
		})
		// Find 返回 1 条子评论
		mt.AddMockResponses(mtest.CreateCursorResponse(0, "rednote.comments", mtest.FirstBatch,
			bson.D{
				{Key: "_id", Value: childOid},
				{Key: "postId", Value: primitive.NewObjectID().Hex()},
				{Key: "userId", Value: int64(10)},
				{Key: "content", Value: "子评论内容"},
				{Key: "parentId", Value: rootId.Hex()},
				{Key: "likeCount", Value: int32(3)},
			},
		))
		// 匿名态不查 comment_likes，无需额外 mock

		logic := NewListSubCommentsLogic(context.Background(), makeListSvcCtx(mt))
		resp, err := logic.ListSubComments(&comment.ListSubCommentsRequest{
			RootCommentId: rootId.Hex(),
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

func TestListSubComments_LoggedIn_IsLikedFromMongo(t *testing.T) {
	mt := mtest.New(t, mtest.NewOptions().ClientType(mtest.Mock))

	mt.Run("登录态下已点赞子评论 isLiked=true，未点赞 isLiked=false", func(mt *mtest.T) {
		rootId := primitive.NewObjectID()
		childOid1 := primitive.NewObjectID()
		childOid2 := primitive.NewObjectID()

		// CountDocuments
		mt.AddMockResponses(bson.D{
			{Key: "ok", Value: 1},
			{Key: "cursor", Value: bson.D{
				{Key: "firstBatch", Value: bson.A{bson.D{{Key: "n", Value: int32(2)}}}},
				{Key: "id", Value: int64(0)},
				{Key: "ns", Value: "rednote.comments"},
			}},
		})
		// Find 返回 2 条子评论
		mt.AddMockResponses(mtest.CreateCursorResponse(0, "rednote.comments", mtest.FirstBatch,
			bson.D{
				{Key: "_id", Value: childOid1},
				{Key: "postId", Value: primitive.NewObjectID().Hex()},
				{Key: "userId", Value: int64(10)},
				{Key: "content", Value: "子评论1"},
				{Key: "parentId", Value: rootId.Hex()},
			},
			bson.D{
				{Key: "_id", Value: childOid2},
				{Key: "postId", Value: primitive.NewObjectID().Hex()},
				{Key: "userId", Value: int64(11)},
				{Key: "content", Value: "子评论2"},
				{Key: "parentId", Value: rootId.Hex()},
			},
		))
		// Find("comment_likes") — 用户只点赞了 childOid1
		mt.AddMockResponses(mtest.CreateCursorResponse(0, "rednote.comment_likes", mtest.FirstBatch,
			bson.D{
				{Key: "_id", Value: primitive.NewObjectID()},
				{Key: "userId", Value: int64(42)},
				{Key: "commentId", Value: childOid1.Hex()},
			},
		))

		logic := NewListSubCommentsLogic(context.Background(), makeListSvcCtx(mt))
		resp, err := logic.ListSubComments(&comment.ListSubCommentsRequest{
			RootCommentId: rootId.Hex(),
			Page:          1,
			PageSize:      20,
			CurrentUserId: 42,
		})

		require.NoError(t, err)
		require.Len(t, resp.GetItems(), 2)

		likedMap := map[string]bool{}
		for _, item := range resp.GetItems() {
			likedMap[item.GetId()] = item.GetIsLiked()
		}
		assert.True(t, likedMap[childOid1.Hex()], "childOid1 应为已点赞")
		assert.False(t, likedMap[childOid2.Hex()], "childOid2 应为未点赞")
	})
}
