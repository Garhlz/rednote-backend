package handler

import (
	"context"
	"encoding/json"
	"log"
	"sync-sidecar/internal/event"
	"sync-sidecar/internal/service"
	"time"

	amqp "github.com/rabbitmq/amqp091-go"
	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/mongo/options"
)

type SearchHandler struct {
	Infra *service.Infra
}

func (h *SearchHandler) Handle(d amqp.Delivery) {
	var e event.UserSearchEvent
	if err := json.Unmarshal(d.Body, &e); err == nil && e.UserId > 0 && e.Keyword != "" {
		coll := h.Infra.Mongo.Database("rednote").Collection("search_histories")
		now := time.Now().In(h.Infra.Cfg.TimeLocation)
		filter := bson.M{"userId": e.UserId, "keyword": e.Keyword}
		update := bson.M{
			"$set": bson.M{"updatedAt": now},
			"$setOnInsert": bson.M{
				"userId":  e.UserId,
				"keyword": e.Keyword,
			},
		}

		opts := options.Update().SetUpsert(true)
		_, err := coll.UpdateOne(context.Background(), filter, update, opts)

		if err != nil {
			log.Printf("❌ [Search] Update Error: %v", err)
		} else {
			// ✅ 增加成功日志
			log.Printf("🔍 [Search] History Updated: User(%d) -> '%s'", e.UserId, e.Keyword)
		}
	}
	d.Ack(false)
}
