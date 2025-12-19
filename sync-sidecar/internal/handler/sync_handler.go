package handler

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"log"
	"strings"
	"time"

	"sync-sidecar/internal/event"
	"sync-sidecar/internal/model"
	"sync-sidecar/internal/service"

	"github.com/elastic/go-elasticsearch/v8"
	"github.com/elastic/go-elasticsearch/v8/esapi"
	amqp "github.com/rabbitmq/amqp091-go"
	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/bson/primitive"
	"go.mongodb.org/mongo-driver/mongo"
)

type SyncHandler struct {
	Infra *service.Infra
}

func (h *SyncHandler) Handle(d amqp.Delivery) {
	typeId, ok := d.Headers["__TypeId__"].(string)
	if !ok {
		d.Nack(false, false)
		return
	}
	className := typeId[strings.LastIndex(typeId, ".")+1:]

	var err error
	es := h.Infra.ES
	cfg := h.Infra.Cfg

	switch className {
	case "PostCreateEvent", "PostAuditPassEvent":
		if !cfg.AuditEnable {
			var e event.PostCreateEvent
			if err = json.Unmarshal(d.Body, &e); err == nil {
				log.Printf("📥 [ES-Sync] Received Create: %s", e.Id)
				err = h.handleCreate(es, e)
			}
		}
	case "PostDeleteEvent":
		var e event.PostDeleteEvent
		if err = json.Unmarshal(d.Body, &e); err == nil {
			log.Printf("📥 [ES-Sync] Received Delete: %s", e.PostId)
			err = h.handleDelete(es, e)
		}
	case "PostUpdateEvent":
		var e event.PostUpdateEvent
		if err = json.Unmarshal(d.Body, &e); err == nil {
			log.Printf("📥 [ES-Sync] Received Update: %s", e.PostId)
			// 需要回查 Mongo 以获取最新完整数据
			postColl := h.Infra.Mongo.Database("rednote").Collection("posts")
			err = h.handleUpdate(es, postColl, e)
		}
	case "UserUpdateEvent":
		var e event.UserUpdateEvent
		if err = json.Unmarshal(d.Body, &e); err == nil {
			// 这里只负责 ES 的数据同步，Mongo 的归 UserHandler 管
			err = h.handleESUserUpdate(es, e)
		}
	default:
		log.Printf("⚠️ [ES-Sync] Unknown event: %s", className)
		d.Ack(false)
		return
	}

	if err != nil {
		log.Printf("❌ [ES-Sync] Handle Error [%s]: %v", className, err)
		d.Nack(false, false)
	} else {
		d.Ack(false)
	}
}

// --- 具体业务逻辑 ---

func (h *SyncHandler) handleCreate(es *elasticsearch.Client, e event.PostCreateEvent) error {
	resources := []string{}
	if len(e.Images) > 0 {
		resources = e.Images
	} else if e.Video != "" {
		resources = append(resources, e.Video)
	}
	// ES 时间格式化
	formattedTime := time.Now().In(h.Infra.Cfg.TimeLocation).Format("2006-01-02T15:04:05.000")

	doc := model.PostEsDoc{
		Id:           e.Id,
		UserId:       e.UserId,
		Title:        e.Title,
		Content:      e.Content,
		Tags:         e.Tags,
		Type:         e.Type,
		Resources:    resources,
		Cover:        e.Cover,
		CoverWidth:   e.CoverWidth,
		CoverHeight:  e.CoverHeight,
		UserNickname: e.UserNickname,
		UserAvatar:   e.UserAvatar,
		LikeCount:    0,
		CreatedAt:    formattedTime,
	}

	err := h.indexEs(es, doc.Id, doc)
	if err == nil {
		log.Printf("✅ [ES-Sync] Post Indexed: %s", e.Id)
	}
	return err
}

func (h *SyncHandler) handleDelete(es *elasticsearch.Client, e event.PostDeleteEvent) error {
	req := esapi.DeleteRequest{
		Index:      h.Infra.Cfg.IndexName,
		DocumentID: e.PostId,
	}
	res, err := req.Do(context.Background(), es)
	if err != nil {
		return err
	}
	defer res.Body.Close()

	if res.IsError() && res.StatusCode != 404 {
		return fmt.Errorf("ES Delete Error: %s", res.Status())
	}
	log.Printf("🗑️ [ES-Sync] Post Deleted: %s", e.PostId)
	return nil
}

func (h *SyncHandler) handleUpdate(es *elasticsearch.Client, coll *mongo.Collection, e event.PostUpdateEvent) error {
	objId, _ := primitive.ObjectIDFromHex(e.PostId)

	var mongoDoc model.PostDoc
	err := coll.FindOne(context.Background(), bson.M{"_id": objId}).Decode(&mongoDoc)

	// 如果 Mongo 里没了，或者状态不对，ES 也要删掉
	if err == mongo.ErrNoDocuments || mongoDoc.IsDeleted == 1 || mongoDoc.Status != 1 {
		log.Printf("⚠️ [ES-Sync] Post invalid in Mongo, removing from ES: %s", e.PostId)
		return h.handleDelete(es, event.PostDeleteEvent{PostId: e.PostId})
	} else if err != nil {
		return err
	}

	formattedTime := mongoDoc.CreatedAt.In(h.Infra.Cfg.TimeLocation).Format("2006-01-02T15:04:05.000")

	esDoc := model.PostEsDoc{
		Id:           mongoDoc.ID.Hex(),
		UserId:       mongoDoc.UserId,
		Title:        mongoDoc.Title,
		Content:      mongoDoc.Content,
		Tags:         mongoDoc.Tags,
		Type:         mongoDoc.Type,
		Resources:    mongoDoc.Resources,
		Cover:        mongoDoc.Cover,
		CoverWidth:   mongoDoc.CoverWidth,
		CoverHeight:  mongoDoc.CoverHeight,
		UserNickname: mongoDoc.UserNickname,
		UserAvatar:   mongoDoc.UserAvatar,
		LikeCount:    mongoDoc.LikeCount,
		CreatedAt:    formattedTime,
	}

	err = h.indexEs(es, esDoc.Id, esDoc)
	if err == nil {
		log.Printf("🔄 [ES-Sync] Post Updated: %s", e.PostId)
	}
	return err
}

func (h *SyncHandler) handleESUserUpdate(es *elasticsearch.Client, e event.UserUpdateEvent) error {
	// 使用 Painless 脚本更新所有文档
	source := `ctx._source.userNickname = params.nickname; ctx._source.userAvatar = params.avatar;`

	req := esapi.UpdateByQueryRequest{
		Index: []string{h.Infra.Cfg.IndexName},
		Body: strings.NewReader(fmt.Sprintf(`{
            "script": { "source": "%s", "params": { "nickname": "%s", "avatar": "%s" } },
            "query": { "term": { "userId": %d } }
        }`, source, e.NewNickname, e.NewAvatar, e.UserId)),
	}

	res, err := req.Do(context.Background(), es)
	if err != nil {
		return err
	}
	defer res.Body.Close()
	if res.IsError() {
		return fmt.Errorf("ES UpdateByQuery Error: %s", res.Status())
	}

	log.Printf("👤 [ES-Sync] User Info Batch Updated: UserId=%d", e.UserId)
	return nil
}

func (h *SyncHandler) indexEs(es *elasticsearch.Client, id string, doc interface{}) error {
	data, err := json.Marshal(doc)
	if err != nil {
		return err
	}

	req := esapi.IndexRequest{
		Index:      h.Infra.Cfg.IndexName,
		DocumentID: id,
		Body:       bytes.NewReader(data),
	}

	res, err := req.Do(context.Background(), es)
	if err != nil {
		return err
	}
	defer res.Body.Close()

	if res.IsError() {
		return fmt.Errorf("ES Index Error [%s]", res.Status())
	}
	return nil
}
