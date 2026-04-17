package logic

import (
	"context"
	"testing"
	"time"

	"notification-rpc/notification"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/bson/primitive"
	"go.mongodb.org/mongo-driver/mongo/integration/mtest"
)

func TestListNotifications_PageDefaults(t *testing.T) {
	mt := mtest.New(t, mtest.NewOptions().ClientType(mtest.Mock))

	mt.Run("page<1 兜底为 1，pageSize<1 兜底为 20", func(mt *mtest.T) {
		// CountDocuments
		mt.AddMockResponses(bson.D{
			{Key: "ok", Value: 1},
			{Key: "cursor", Value: bson.D{
				{Key: "firstBatch", Value: bson.A{bson.D{{Key: "n", Value: int32(0)}}}},
				{Key: "id", Value: int64(0)},
				{Key: "ns", Value: "rednote.notifications"},
			}},
		})
		// Find 返回空
		mt.AddMockResponses(mtest.CreateCursorResponse(0, "rednote.notifications", mtest.FirstBatch))

		logic := NewListNotificationsLogic(context.Background(), makeSvcCtx(mt))
		resp, err := logic.ListNotifications(&notification.ListNotificationsRequest{
			UserId:   1,
			Page:     0,
			PageSize: 0,
		})

		require.NoError(t, err)
		assert.Equal(t, int32(1), resp.GetPage(), "page<1 应兜底为 1")
		assert.Equal(t, int32(20), resp.GetPageSize(), "pageSize<1 应兜底为 20")
	})
}

func TestListNotifications_EmptyResult(t *testing.T) {
	mt := mtest.New(t, mtest.NewOptions().ClientType(mtest.Mock))

	mt.Run("无通知时 items 为空数组", func(mt *mtest.T) {
		mt.AddMockResponses(bson.D{
			{Key: "ok", Value: 1},
			{Key: "cursor", Value: bson.D{
				{Key: "firstBatch", Value: bson.A{bson.D{{Key: "n", Value: int32(0)}}}},
				{Key: "id", Value: int64(0)},
				{Key: "ns", Value: "rednote.notifications"},
			}},
		})
		mt.AddMockResponses(mtest.CreateCursorResponse(0, "rednote.notifications", mtest.FirstBatch))

		logic := NewListNotificationsLogic(context.Background(), makeSvcCtx(mt))
		resp, err := logic.ListNotifications(&notification.ListNotificationsRequest{
			UserId:   1,
			Page:     1,
			PageSize: 20,
		})

		require.NoError(t, err)
		assert.Equal(t, int64(0), resp.GetTotal())
		assert.Empty(t, resp.GetItems(), "无通知时 items 应为空数组")
	})
}

func TestListNotifications_ReturnsItems(t *testing.T) {
	mt := mtest.New(t, mtest.NewOptions().ClientType(mtest.Mock))

	mt.Run("返回通知列表并正确映射字段", func(mt *mtest.T) {
		notifOid := primitive.NewObjectID()
		now := time.Now()

		// CountDocuments
		mt.AddMockResponses(bson.D{
			{Key: "ok", Value: 1},
			{Key: "cursor", Value: bson.D{
				{Key: "firstBatch", Value: bson.A{bson.D{{Key: "n", Value: int32(1)}}}},
				{Key: "id", Value: int64(0)},
				{Key: "ns", Value: "rednote.notifications"},
			}},
		})
		// Find 返回 1 条通知
		mt.AddMockResponses(mtest.CreateCursorResponse(0, "rednote.notifications", mtest.FirstBatch,
			bson.D{
				{Key: "_id", Value: notifOid},
				{Key: "receiverId", Value: int64(10)},
				{Key: "senderId", Value: int64(20)},
				{Key: "senderNickname", Value: "Alice"},
				{Key: "type", Value: "LIKE_POST"},
				{Key: "targetId", Value: "post-001"},
				{Key: "targetPreview", Value: "帖子摘要"},
				{Key: "isRead", Value: false},
				{Key: "createdAt", Value: now},
			},
		))

		logic := NewListNotificationsLogic(context.Background(), makeSvcCtx(mt))
		resp, err := logic.ListNotifications(&notification.ListNotificationsRequest{
			UserId:   10,
			Page:     1,
			PageSize: 20,
		})

		require.NoError(t, err)
		require.Len(t, resp.GetItems(), 1)

		item := resp.GetItems()[0]
		assert.Equal(t, notifOid.Hex(), item.GetId())
		assert.Equal(t, int64(10), item.GetReceiverId())
		assert.Equal(t, int64(20), item.GetSenderId())
		assert.Equal(t, "Alice", item.GetSenderNickname())
		assert.Equal(t, notification.NotificationType_LIKE_POST, item.GetType())
		assert.Equal(t, "post-001", item.GetTargetId())
		assert.False(t, item.GetIsRead())
	})
}

func TestListNotifications_UnknownType_FallsBackToUnknown(t *testing.T) {
	mt := mtest.New(t, mtest.NewOptions().ClientType(mtest.Mock))

	mt.Run("旧数据中未知通知类型应映射为 UNKNOWN，不 panic", func(mt *mtest.T) {
		// CountDocuments
		mt.AddMockResponses(bson.D{
			{Key: "ok", Value: 1},
			{Key: "cursor", Value: bson.D{
				{Key: "firstBatch", Value: bson.A{bson.D{{Key: "n", Value: int32(1)}}}},
				{Key: "id", Value: int64(0)},
				{Key: "ns", Value: "rednote.notifications"},
			}},
		})
		// Find 返回包含未知 type 的通知
		mt.AddMockResponses(mtest.CreateCursorResponse(0, "rednote.notifications", mtest.FirstBatch,
			bson.D{
				{Key: "_id", Value: primitive.NewObjectID()},
				{Key: "receiverId", Value: int64(10)},
				{Key: "type", Value: "SOME_FUTURE_TYPE"},
				{Key: "isRead", Value: false},
				{Key: "createdAt", Value: time.Now()},
			},
		))

		logic := NewListNotificationsLogic(context.Background(), makeSvcCtx(mt))
		resp, err := logic.ListNotifications(&notification.ListNotificationsRequest{
			UserId:   10,
			Page:     1,
			PageSize: 20,
		})

		require.NoError(t, err, "未知类型不应 panic 或返回错误")
		require.Len(t, resp.GetItems(), 1)
		assert.Equal(t, notification.NotificationType_NOTIFICATION_TYPE_UNKNOWN, resp.GetItems()[0].GetType(),
			"未知类型应映射为 NOTIFICATION_TYPE_UNKNOWN")
	})
}
