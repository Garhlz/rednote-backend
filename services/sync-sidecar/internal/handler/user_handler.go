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

// 处理用户删除事件 (逻辑删除适配版)
func (h *UserHandler) handleUserDelete(e event.UserDeleteEvent) error {
	log.Printf("🚫 [User-Mongo] Anonymizing & Cleaning User Data for UserId: %d", e.UserId)

	db := h.Infra.Mongo.Database("rednote")
	ctx := context.Background()

	// 定义已注销用户的统一形象
	anonymizedName := "已注销用户"
	anonymizedAvatar := "https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/deleted_user.png" // 建议搞个灰色的默认头像

	// ==========================================
	// 1. 【内容脱敏】: 帖子、评论
	//    保留文档，但把昵称头像改掉，断开与原身份的视觉联系
	// ==========================================

	// 构造更新语句：修改昵称、头像
	// 如果你的 PostDoc/CommentDoc 有 isDeleted 字段，也可以在这里 set isDeleted = 1
	updateAnonymize := bson.M{
		"$set": bson.M{
			"userNickname": anonymizedName,
			"userAvatar":   anonymizedAvatar,
			// "isDeleted": 1, // 可选：如果你希望前端能识别并置灰
		},
	}

	h.updateManySafe(ctx, db.Collection("posts"), bson.M{"userId": e.UserId}, updateAnonymize)
	h.updateManySafe(ctx, db.Collection("comments"), bson.M{"userId": e.UserId}, updateAnonymize)

	// 特殊处理：更新别人评论里 "回复 @某某" 的昵称
	h.updateManySafe(ctx, db.Collection("comments"),
		bson.M{"replyToUserId": e.UserId},
		bson.M{"$set": bson.M{"replyToUserNickname": anonymizedName}},
	)

	// ==========================================
	// 2. 【关系清理】: 关注、粉丝、点赞、收藏、评分
	//    这些数据代表"活跃状态"，人走了关系自然要断开，建议物理删除
	// ==========================================

	// 2.1 删除 关注/粉丝 关系
	// 我关注了谁 -> 删
	h.deleteManySafe(ctx, db.Collection("user_follows"), bson.M{"userId": e.UserId})
	// 谁关注了我 -> 删 (这样我就从别人的粉丝列表消失了)
	h.deleteManySafe(ctx, db.Collection("user_follows"), bson.M{"targetUserId": e.UserId})

	// 2.2 删除 互动数据 (点赞、收藏等)
	// 注意：删除这些数据后，相关帖子的 likeCount/collectCount 可能会偏高(因为没有触发减1逻辑)
	// 如果对数字精确性要求极高，这里需要发消息去触发计数减少，或者跑定时任务修正。
	// 但作为"注销"场景，通常可以容忍这一点点数据偏差，直接删即可。
	interactionCollections := []string{
		"post_likes",
		"post_collects",
		"post_ratings",
		"comment_likes",
		"notifications", // 通知的接收者是该用户，或者发送者是该用户，都建议清理
	}

	for _, collName := range interactionCollections {
		// 删除 "我" 发起的操作
		h.deleteManySafe(ctx, db.Collection(collName), bson.M{"userId": e.UserId})
	}

	// 清理以我为接收者的通知 (也没人看了)
	h.deleteManySafe(ctx, db.Collection("notifications"), bson.M{"receiverId": e.UserId})

	// 2.3 删除 隐私历史
	h.deleteManySafe(ctx, db.Collection("search_histories"), bson.M{"userId": e.UserId})
	h.deleteManySafe(ctx, db.Collection("post_view_histories"), bson.M{"userId": e.UserId})

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
