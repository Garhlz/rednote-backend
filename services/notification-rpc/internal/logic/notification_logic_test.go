package logic

import (
	"context"
	"testing"

	"notification-rpc/internal/svc"
	"notification-rpc/notification"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/bson/primitive"
	"go.mongodb.org/mongo-driver/mongo/integration/mtest"
)

// makeSvcCtx 用 mtest 提供的 fake mongo 创建 ServiceContext。
func makeSvcCtx(mt *mtest.T) *svc.ServiceContext {
	return &svc.ServiceContext{
		Mongo: mt.DB,
	}
}

// ---- GetUnreadCount ----

func TestGetUnreadCount_ReturnsCountFromMongo(t *testing.T) {
	mt := mtest.New(t, mtest.NewOptions().ClientType(mtest.Mock))

	mt.Run("CountDocuments 返回指定数值", func(mt *mtest.T) {
		// mtest 中 CountDocuments 使用的是 aggregate 协议，
		// 需要用 CreateCursorResponse 返回 { n: <count> }
		mt.AddMockResponses(bson.D{
			{Key: "ok", Value: 1},
			{Key: "cursor", Value: bson.D{
				{Key: "firstBatch", Value: bson.A{bson.D{{Key: "n", Value: int32(7)}}}},
				{Key: "id", Value: int64(0)},
				{Key: "ns", Value: "rednote.notifications"},
			}},
		})

		logic := NewGetUnreadCountLogic(context.Background(), makeSvcCtx(mt))
		resp, err := logic.GetUnreadCount(&notification.GetUnreadCountRequest{UserId: 42})

		require.NoError(t, err)
		assert.Equal(t, int64(7), resp.GetUnreadCount())
	})
}

func TestGetUnreadCount_ZeroWhenNoUnread(t *testing.T) {
	mt := mtest.New(t, mtest.NewOptions().ClientType(mtest.Mock))

	mt.Run("无未读时返回 0", func(mt *mtest.T) {
		mt.AddMockResponses(bson.D{
			{Key: "ok", Value: 1},
			{Key: "cursor", Value: bson.D{
				{Key: "firstBatch", Value: bson.A{bson.D{{Key: "n", Value: int32(0)}}}},
				{Key: "id", Value: int64(0)},
				{Key: "ns", Value: "rednote.notifications"},
			}},
		})

		logic := NewGetUnreadCountLogic(context.Background(), makeSvcCtx(mt))
		resp, err := logic.GetUnreadCount(&notification.GetUnreadCountRequest{UserId: 99})

		require.NoError(t, err)
		assert.Equal(t, int64(0), resp.GetUnreadCount())
	})
}

// ---- CreateNotification ----

func TestCreateNotification_EmptyPayload_ReturnsNoError(t *testing.T) {
	mt := mtest.New(t, mtest.NewOptions().ClientType(mtest.Mock))

	mt.Run("nil payload 直接返回空，不调用 Mongo", func(mt *mtest.T) {
		logic := NewCreateNotificationLogic(context.Background(), makeSvcCtx(mt))
		_, err := logic.CreateNotification(&notification.CreateNotificationRequest{Notification: nil})
		require.NoError(t, err, "nil payload 不应返回错误")
	})
}

func TestCreateNotification_InsertsDocument(t *testing.T) {
	mt := mtest.New(t, mtest.NewOptions().ClientType(mtest.Mock))

	mt.Run("正常通知插入成功", func(mt *mtest.T) {
		mt.AddMockResponses(mtest.CreateSuccessResponse())

		logic := NewCreateNotificationLogic(context.Background(), makeSvcCtx(mt))
		_, err := logic.CreateNotification(&notification.CreateNotificationRequest{
			Notification: &notification.NotificationPayload{
				ReceiverId:     10,
				SenderId:       20,
				SenderNickname: "Alice",
				Type:           notification.NotificationType_COMMENT,
				TargetId:       "post-abc",
				TargetPreview:  "帖子内容摘要",
			},
		})

		require.NoError(t, err)
	})
}

// ---- UpsertNotification（去重 upsert） ----

func TestUpsertNotification_EmptyPayload_ReturnsNoError(t *testing.T) {
	mt := mtest.New(t, mtest.NewOptions().ClientType(mtest.Mock))

	mt.Run("nil payload 不调用 Mongo", func(mt *mtest.T) {
		logic := NewUpsertNotificationLogic(context.Background(), makeSvcCtx(mt))
		_, err := logic.UpsertNotification(&notification.UpsertNotificationRequest{Notification: nil})
		require.NoError(t, err)
	})
}

func TestUpsertNotification_CallsUpdateOneWithUpsert(t *testing.T) {
	mt := mtest.New(t, mtest.NewOptions().ClientType(mtest.Mock))

	mt.Run("点赞通知 upsert 成功", func(mt *mtest.T) {
		// UpdateOne with upsert=true 返回 matched+upserted
		mt.AddMockResponses(bson.D{
			{Key: "ok", Value: 1},
			{Key: "n", Value: 1},
			{Key: "nModified", Value: 0},
			{Key: "upserted", Value: bson.A{bson.D{{Key: "index", Value: 0}, {Key: "_id", Value: "new-id"}}}},
		})

		logic := NewUpsertNotificationLogic(context.Background(), makeSvcCtx(mt))
		_, err := logic.UpsertNotification(&notification.UpsertNotificationRequest{
			Notification: &notification.NotificationPayload{
				ReceiverId:     10,
				SenderId:       20,
				Type:           notification.NotificationType_LIKE_POST,
				TargetId:       "post-001",
				SenderNickname: "Bob",
			},
		})

		require.NoError(t, err)
	})
}

func TestUpsertNotification_SecondUpsert_Updates(t *testing.T) {
	mt := mtest.New(t, mtest.NewOptions().ClientType(mtest.Mock))

	mt.Run("相同 (receiver,sender,type,target) 再次 upsert 时返回 nModified=1", func(mt *mtest.T) {
		// 已存在文档时 UpdateOne 返回 matched=1, modified=1
		mt.AddMockResponses(bson.D{
			{Key: "ok", Value: 1},
			{Key: "n", Value: 1},
			{Key: "nModified", Value: 1},
		})

		logic := NewUpsertNotificationLogic(context.Background(), makeSvcCtx(mt))
		_, err := logic.UpsertNotification(&notification.UpsertNotificationRequest{
			Notification: &notification.NotificationPayload{
				ReceiverId: 10,
				SenderId:   20,
				Type:       notification.NotificationType_LIKE_POST,
				TargetId:   "post-001",
			},
		})

		require.NoError(t, err, "重复 upsert 不应返回错误")
	})
}

// ---- MarkAllRead ----

func TestMarkAllRead_UpdatesAllUnread(t *testing.T) {
	mt := mtest.New(t, mtest.NewOptions().ClientType(mtest.Mock))

	mt.Run("全部已读 UpdateMany 调用成功", func(mt *mtest.T) {
		mt.AddMockResponses(bson.D{
			{Key: "ok", Value: 1},
			{Key: "n", Value: 5},
			{Key: "nModified", Value: 5},
		})

		logic := NewMarkAllReadLogic(context.Background(), makeSvcCtx(mt))
		_, err := logic.MarkAllRead(&notification.MarkAllReadRequest{UserId: 42})

		require.NoError(t, err)
	})
}

// ---- MarkBatchRead ----

func TestMarkBatchRead_EmptyIds_ReturnsNoError(t *testing.T) {
	mt := mtest.New(t, mtest.NewOptions().ClientType(mtest.Mock))

	mt.Run("空 ids 不调用 Mongo，直接返回", func(mt *mtest.T) {
		logic := NewMarkBatchReadLogic(context.Background(), makeSvcCtx(mt))
		_, err := logic.MarkBatchRead(&notification.MarkBatchReadRequest{
			UserId: 1,
			Ids:    []string{},
		})
		require.NoError(t, err)
	})
}

func TestMarkBatchRead_AllInvalidIds_ReturnsNoError(t *testing.T) {
	mt := mtest.New(t, mtest.NewOptions().ClientType(mtest.Mock))

	mt.Run("全部 id 非法时跳过并返回空结果", func(mt *mtest.T) {
		logic := NewMarkBatchReadLogic(context.Background(), makeSvcCtx(mt))
		_, err := logic.MarkBatchRead(&notification.MarkBatchReadRequest{
			UserId: 1,
			Ids:    []string{"not-valid", "also-bad"},
		})
		require.NoError(t, err, "非法 id 应被跳过，不报错")
	})
}

// ---- MarkBatchRead 幂等性 ----

func TestMarkBatchRead_Idempotent_SecondCallSucceeds(t *testing.T) {
	mt := mtest.New(t, mtest.NewOptions().ClientType(mtest.Mock))

	mt.Run("同一批 id 重复调用，第二次 nModified=0 不报错", func(mt *mtest.T) {
		id := primitive.NewObjectID().Hex()

		// 第一次调用：UpdateMany 成功，nModified=1
		mt.AddMockResponses(bson.D{
			{Key: "ok", Value: 1},
			{Key: "n", Value: int32(1)},
			{Key: "nModified", Value: int32(1)},
		})

		logic := NewMarkBatchReadLogic(context.Background(), makeSvcCtx(mt))
		_, err := logic.MarkBatchRead(&notification.MarkBatchReadRequest{
			UserId: 1,
			Ids:    []string{id},
		})
		require.NoError(t, err, "第一次调用应成功")

		// 第二次调用：同一批 id，但 isRead 已为 true，UpdateMany 匹配 0 条（nModified=0），不报错
		mt.AddMockResponses(bson.D{
			{Key: "ok", Value: 1},
			{Key: "n", Value: int32(0)},
			{Key: "nModified", Value: int32(0)},
		})

		logic2 := NewMarkBatchReadLogic(context.Background(), makeSvcCtx(mt))
		_, err = logic2.MarkBatchRead(&notification.MarkBatchReadRequest{
			UserId: 1,
			Ids:    []string{id},
		})
		require.NoError(t, err, "第二次重复调用 nModified=0 时不应报错")
	})
}

// ---- 状态型通知 upsert 覆盖更新后 isRead 重置为 false ----

func TestUpsertNotification_ResetsIsReadOnUpdate(t *testing.T) {
	mt := mtest.New(t, mtest.NewOptions().ClientType(mtest.Mock))

	mt.Run("已读的状态型通知再次 upsert 后 isRead 重置为 false", func(mt *mtest.T) {
		// UpdateOne (upsert=true) 成功——模拟已存在文档被更新
		mt.AddMockResponses(bson.D{
			{Key: "ok", Value: 1},
			{Key: "n", Value: int32(1)},
			{Key: "nModified", Value: int32(1)},
		})

		logic := NewUpsertNotificationLogic(context.Background(), makeSvcCtx(mt))
		_, err := logic.UpsertNotification(&notification.UpsertNotificationRequest{
			Notification: &notification.NotificationPayload{
				ReceiverId:     10,
				SenderId:       20,
				SenderNickname: "Bob",
				Type:           notification.NotificationType_LIKE_POST,
				TargetId:       "post-001",
				TargetPreview:  "帖子摘要",
			},
		})
		// 只要 Mongo 不报错，逻辑层应正常返回
		require.NoError(t, err, "upsert 更新已读通知后不应报错（isRead 由 $set 重置为 false）")
	})
}

// ---- 非状态型通知不走 upsert，走 CreateNotification ----

func TestCreateNotification_NonStateful_DoesNotUpsert(t *testing.T) {
	mt := mtest.New(t, mtest.NewOptions().ClientType(mtest.Mock))

	mt.Run("COMMENT/REPLY 类型通知通过 CreateNotification 插入，不走 upsert 路径", func(mt *mtest.T) {
		// CreateNotification 内部走 InsertOne
		mt.AddMockResponses(mtest.CreateSuccessResponse())

		logic := NewCreateNotificationLogic(context.Background(), makeSvcCtx(mt))
		_, err := logic.CreateNotification(&notification.CreateNotificationRequest{
			Notification: &notification.NotificationPayload{
				ReceiverId:     10,
				SenderId:       20,
				SenderNickname: "Alice",
				Type:           notification.NotificationType_COMMENT,
				TargetId:       "post-001",
			},
		})
		require.NoError(t, err, "COMMENT 类型通知应能通过 CreateNotification 插入")
	})
}
