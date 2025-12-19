package handler

import (
	"context"
	"encoding/json"
	"log"
	"strings"
	"time"

	amqp "github.com/rabbitmq/amqp091-go"
	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/mongo"

	"sync-sidecar/internal/event"
	"sync-sidecar/internal/service"
)

type UserHandler struct {
	Infra *service.Infra
}

func (h *UserHandler) Handle(d amqp.Delivery) {
	typeId, ok := d.Headers["__TypeId__"].(string)
	if !ok {
		d.Nack(false, false)
		return
	}
	className := typeId[strings.LastIndex(typeId, ".")+1:]

	var err error
	start := time.Now() // ⏱️ 开始计时

	switch className {
	case "UserUpdateEvent":
		var e event.UserUpdateEvent
		if err = json.Unmarshal(d.Body, &e); err == nil {
			err = h.handleUserUpdate(e)
		}
	case "UserDeleteEvent":
		var e event.UserDeleteEvent
		if err = json.Unmarshal(d.Body, &e); err == nil {
			err = h.handleUserDelete(e)
		}
	default:
		// 忽略非 User 事件
		d.Ack(false)
		return
	}

	if err != nil {
		log.Printf("❌ [User-Mongo] Error handling %s: %v", className, err)
		d.Nack(false, false)
	} else {
		// ✅ 打印耗时，方便观察性能
		duration := time.Since(start)
		log.Printf("✅ [User-Mongo] Processed %s in %v", className, duration)
		d.Ack(false)
	}
}

func (h *UserHandler) handleUserUpdate(e event.UserUpdateEvent) error {
	log.Printf("🔄 [User-Mongo] Syncing User Info for UserId: %d", e.UserId)

	db := h.Infra.Mongo.Database("rednote")
	ctx := context.Background()

	// 1. 更新 "我" 的基础信息 (帖子、评论、我关注的列表)
	updateSelf := bson.M{}
	if e.NewNickname != "" {
		updateSelf["userNickname"] = e.NewNickname
	}
	if e.NewAvatar != "" {
		updateSelf["userAvatar"] = e.NewAvatar
	}

	if len(updateSelf) > 0 {
		updateCmd := bson.M{"$set": updateSelf}

		h.updateManySafe(ctx, db.Collection("posts"), bson.M{"userId": e.UserId}, updateCmd)
		h.updateManySafe(ctx, db.Collection("comments"), bson.M{"userId": e.UserId}, updateCmd)
		h.updateManySafe(ctx, db.Collection("user_follows"), bson.M{"userId": e.UserId}, updateCmd)
	}

	// 2. 更新 "我作为被关注者" 的信息 (关注我的人的列表)
	updateTarget := bson.M{}
	if e.NewNickname != "" {
		updateTarget["targetUserNickname"] = e.NewNickname
	}
	if e.NewAvatar != "" {
		updateTarget["targetUserAvatar"] = e.NewAvatar
	}

	if len(updateTarget) > 0 {
		h.updateManySafe(ctx, db.Collection("user_follows"), bson.M{"targetUserId": e.UserId}, bson.M{"$set": updateTarget})
	}

	// 3. 更新 "回复我的评论" 中的昵称 (replyToUserNickname)
	if e.NewNickname != "" {
		h.updateManySafe(ctx, db.Collection("comments"),
			bson.M{"replyToUserId": e.UserId},
			bson.M{"$set": bson.M{"replyToUserNickname": e.NewNickname}},
		)
	}

	return nil
}

func (h *UserHandler) handleUserDelete(e event.UserDeleteEvent) error {
	log.Printf("🗑️ [User-Mongo] Deleting User Data for UserId: %d", e.UserId)

	db := h.Infra.Mongo.Database("rednote")
	ctx := context.Background()
	filter := bson.M{"userId": e.UserId}

	// 需要清理的集合列表
	collections := []string{
		"posts",
		"comments",
		"post_likes",
		"comment_likes",
		"post_collects",
		"post_ratings",
		"search_histories",
		"post_view_histories",
	}

	for _, collName := range collections {
		h.deleteManySafe(ctx, db.Collection(collName), filter)
	}

	// 特殊处理关注表
	h.deleteManySafe(ctx, db.Collection("user_follows"), filter)                           // 我关注的
	h.deleteManySafe(ctx, db.Collection("user_follows"), bson.M{"targetUserId": e.UserId}) // 关注我的

	return nil
}

// 辅助方法：安全的 UpdateMany (记录错误但不中断流程)
func (h *UserHandler) updateManySafe(ctx context.Context, coll *mongo.Collection, filter interface{}, update interface{}) {
	res, err := coll.UpdateMany(ctx, filter, update)
	if err != nil {
		log.Printf("⚠️ [User-Mongo] UpdateMany failed for %s: %v", coll.Name(), err)
	} else if res.ModifiedCount > 0 {
		log.Printf("   -> Updated %d docs in %s", res.ModifiedCount, coll.Name())
	}
}

// 辅助方法：安全的 DeleteMany
func (h *UserHandler) deleteManySafe(ctx context.Context, coll *mongo.Collection, filter interface{}) {
	res, err := coll.DeleteMany(ctx, filter)
	if err != nil {
		log.Printf("⚠️ [User-Mongo] DeleteMany failed for %s: %v", coll.Name(), err)
	} else if res.DeletedCount > 0 {
		log.Printf("   -> Deleted %d docs from %s", res.DeletedCount, coll.Name())
	}
}
