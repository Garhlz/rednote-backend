package main

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"log"
	"os"
	"strings"
	"time"

	elasticsearch "github.com/elastic/go-elasticsearch/v8"
	"github.com/elastic/go-elasticsearch/v8/esapi"
	amqp "github.com/rabbitmq/amqp091-go"
	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/bson/primitive"
	"go.mongodb.org/mongo-driver/mongo"
	"go.mongodb.org/mongo-driver/mongo/options"
)

// 配置常量 (可以通过环境变量覆盖)
var (
	RabbitMQURL = getEnv("RABBITMQ_URL", "amqp://admin:admin@rabbitmq:5672/")
	MongoURI    = getEnv("MONGO_URI", "mongodb://mongo:27017")
	ESAddress   = getEnv("ES_ADDRESS", "http://elasticsearch:9200")
	QueueName   = "platform.es.sync.queue"
	IndexName   = "post_index"
	AuditEnable = getEnv("POST_AUDIT_ENABLE", "false") == "true"
)

// ------ 数据结构定义 (对应 Java Event) ------

type PostCreateEvent struct {
	Id           string   `json:"id"`
	Title        string   `json:"title"`
	Content      string   `json:"content"`
	Tags         []string `json:"tags"`
	UserId       int64    `json:"userId"`
	UserNickname string   `json:"userNickname"`
	UserAvatar   string   `json:"userAvatar"`
	Type         int      `json:"type"`
	Cover        string   `json:"cover"`
	CoverWidth   int      `json:"coverWidth"`
	CoverHeight  int      `json:"coverHeight"`
	// Images/Video 在 JavaListener 里被转换了，这里我们直接映射 ES 需要的字段
	// 如果 Event 里有 images/video，需要手动转成 resources
	Images []string `json:"images"`
	Video  string   `json:"video"`
}

type PostUpdateEvent struct {
	PostId  string `json:"postId"`
	Title   string `json:"title"`
	Content string `json:"content"`
}

type PostDeleteEvent struct {
	PostId string `json:"postId"`
}

type UserUpdateEvent struct {
	UserId      int64  `json:"userId"`
	NewNickname string `json:"newNickname"`
	NewAvatar   string `json:"newAvatar"`
}

// Mongo 文档结构 (用于回查)
type PostDoc struct {
	ID           primitive.ObjectID `bson:"_id"`
	UserId       int64              `bson:"userId"`
	Title        string             `bson:"title"`
	Content      string             `bson:"content"`
	Tags         []string           `bson:"tags"`
	Type         int                `bson:"type"`
	Resources    []string           `bson:"resources"` // 注意：Mongo里存的是 resources
	Cover        string             `bson:"cover"`
	CoverWidth   int                `bson:"coverWidth"`
	CoverHeight  int                `bson:"coverHeight"`
	UserNickname string             `bson:"userNickname"`
	UserAvatar   string             `bson:"userAvatar"`
	LikeCount    int                `bson:"likeCount"`
	Status       int                `bson:"status"`    // 1=发布
	IsDeleted    int                `bson:"isDeleted"` // 0=未删除
	CreatedAt    time.Time          `bson:"createdAt"`
	UpdatedAt    time.Time          `bson:"updatedAt"`
}

// ES 文档结构 (最终写入 ES 的数据)
type PostEsDoc struct {
	Id           string   `json:"id"`
	UserId       int64    `json:"userId"`
	Title        string   `json:"title"`
	Content      string   `json:"content"`
	Tags         []string `json:"tags"`
	Type         int      `json:"type"`
	Resources    []string `json:"resources"` // 统一字段
	Cover        string   `json:"cover"`
	CoverWidth   int      `json:"coverWidth"`
	CoverHeight  int      `json:"coverHeight"`
	UserNickname string   `json:"userNickname"`
	UserAvatar   string   `json:"userAvatar"`
	LikeCount    int      `json:"likeCount"`
	CreatedAt    string   `json:"createdAt"`
}

var cstZone = time.FixedZone("CST", 8*3600) // 东八区

func main() {
	var err error
	cstZone, err = time.LoadLocation("Asia/Shanghai")
	if err != nil {
		log.Println("⚠️ Warning: Asia/Shanghai location not found, using default UTC or FixedZone")
		cstZone = time.FixedZone("CST", 8*3600) // 兜底方案
	}
	log.Println("🚀 Go ES-Sync Sidecar Starting...")
	// 【新增调试日志】注意：生产环境不要打印密码，这里是为了调试
	log.Printf("DEBUG: Connecting to: %s", RabbitMQURL)
	// 1. 初始化 MongoDB
	ctx := context.Background()
	mongoClient, err := mongo.Connect(ctx, options.Client().ApplyURI(MongoURI))
	if err != nil {
		log.Fatal(err)
	}
	postColl := mongoClient.Database("rednote").Collection("posts")

	// 2. 初始化 Elasticsearch
	es, err := elasticsearch.NewClient(elasticsearch.Config{
		Addresses: []string{ESAddress},
	})
	if err != nil {
		log.Fatal(err)
	}

	// 3. 初始化 RabbitMQ
	conn, err := amqp.Dial(RabbitMQURL)
	if err != nil {
		log.Fatal(err)
	}
	defer conn.Close()

	ch, err := conn.Channel()
	if err != nil {
		log.Fatal(err)
	}
	defer ch.Close()

	// 声明队列 (确保队列存在，参数需与 Java 端一致)
	// durable=true, autoDelete=false, exclusive=false, noWait=false
	// args 必须包含 x-dead-letter-exchange，否则会报错 PRECONDITION_FAILED
	args := amqp.Table{"x-dead-letter-exchange": "platform.dlx.exchange"}
	_, err = ch.QueueDeclare(QueueName, true, false, false, false, args)
	if err != nil {
		log.Fatal(err)
	}

	msgs, err := ch.Consume(QueueName, "", false, false, false, false, nil)
	if err != nil {
		log.Fatal(err)
	}

	log.Println("Waiting for messages...")

	forever := make(chan bool)

	go func() {
		for d := range msgs {
			// 获取 Spring 发送的 __TypeId__ Header
			typeId, ok := d.Headers["__TypeId__"].(string)
			if !ok {
				log.Printf("Unknown message type (no header), ignoring")
				d.Ack(false)
				continue
			}

			// 简单处理：只看类名后缀
			className := typeId[strings.LastIndex(typeId, ".")+1:]
			log.Printf("Received Event: %s", className)

			var handleErr error

			switch className {
			case "PostCreateEvent":
				if !AuditEnable {
					var e PostCreateEvent
					json.Unmarshal(d.Body, &e)
					handleErr = handleCreate(es, e)
				}
			case "PostAuditPassEvent":
				// 结构与 CreateEvent 基本一致，复用逻辑
				var e PostCreateEvent
				json.Unmarshal(d.Body, &e)
				handleErr = handleCreate(es, e)
			case "PostDeleteEvent":
				var e PostDeleteEvent
				json.Unmarshal(d.Body, &e)
				handleErr = handleDelete(es, e)
			case "PostUpdateEvent":
				var e PostUpdateEvent
				json.Unmarshal(d.Body, &e)
				handleErr = handleUpdate(es, postColl, e) // 传入 Mongo 集合
			case "UserUpdateEvent":
				var e UserUpdateEvent
				json.Unmarshal(d.Body, &e)
				handleErr = handleUserUpdate(es, e)
			default:
				log.Printf("Unhandled event type: %s", className)
			}

			if handleErr != nil {
				log.Printf("Error handling message: %v", handleErr)
				d.Nack(false, false) // 丢入死信队列
			} else {
				d.Ack(false)
			}
		}
	}()

	<-forever
}

// --- Handlers ---

func handleCreate(es *elasticsearch.Client, e PostCreateEvent) error {
	// 转换逻辑：把 Images/Video 转为 Resources
	resources := []string{}
	if len(e.Images) > 0 {
		resources = e.Images
	} else if e.Video != "" {
		resources = append(resources, e.Video)
	}

	doc := PostEsDoc{
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
		CreatedAt:    time.Now().In(cstZone).Format("2006-01-02T15:04:05.000"),
	}

	return indexEs(es, doc.Id, doc)
}

func handleDelete(es *elasticsearch.Client, e PostDeleteEvent) error {
	req := esapi.DeleteRequest{
		Index:      IndexName,
		DocumentID: e.PostId,
	}
	res, err := req.Do(context.Background(), es)
	if err != nil {
		return err
	}
	defer res.Body.Close()
	log.Printf("ES Delete: %s", e.PostId)
	return nil
}

// handleUpdate: 复刻 Java 逻辑，回查 Mongo
func handleUpdate(es *elasticsearch.Client, coll *mongo.Collection, e PostUpdateEvent) error {
	objId, _ := primitive.ObjectIDFromHex(e.PostId)

	// 1. 查询 Mongo
	var mongoDoc PostDoc
	err := coll.FindOne(context.Background(), bson.M{"_id": objId}).Decode(&mongoDoc)

	// 如果找不到，或者已删除，或者未发布 -> 从 ES 删除
	if err == mongo.ErrNoDocuments || mongoDoc.IsDeleted == 1 || mongoDoc.Status != 1 {
		log.Printf("Post invalid in Mongo, removing from ES: %s", e.PostId)
		// 调用删除逻辑
		return handleDelete(es, PostDeleteEvent{PostId: e.PostId})
	} else if err != nil {
		return err // Mongo 挂了，重试
	}

	// 2. 转换数据
	esDoc := PostEsDoc{
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
		CreatedAt:    mongoDoc.CreatedAt.In(cstZone).Format("2006-01-02T15:04:05.000"),
	}

	// 3. 覆盖写入 ES
	return indexEs(es, esDoc.Id, esDoc)
}

func handleUserUpdate(es *elasticsearch.Client, e UserUpdateEvent) error {
	// 使用 Painless 脚本批量更新
	// ctx._source.userNickname = params.nickname; ...
	source := `ctx._source.userNickname = params.nickname; ctx._source.userAvatar = params.avatar;`

	req := esapi.UpdateByQueryRequest{
		Index: []string{IndexName},
		Body: strings.NewReader(fmt.Sprintf(`{
			"script": {
				"source": "%s",
				"params": {
					"nickname": "%s",
					"avatar": "%s"
				}
			},
			"query": {
				"term": {
					"userId": %d
				}
			}
		}`, source, e.NewNickname, e.NewAvatar, e.UserId)),
	}

	res, err := req.Do(context.Background(), es)
	if err != nil {
		return err
	}
	defer res.Body.Close()
	log.Printf("User Info Updated in ES: %d", e.UserId)
	return nil
}

// 辅助方法：写入 ES
func indexEs(es *elasticsearch.Client, id string, doc interface{}) error {
	data, _ := json.Marshal(doc)
	req := esapi.IndexRequest{
		Index:      IndexName,
		DocumentID: id,
		Body:       bytes.NewReader(data),
		// Refresh:    "true", // 开发环境立即刷新，生产环境可以去掉
	}
	res, err := req.Do(context.Background(), es)
	if err != nil {
		return err
	}
	defer res.Body.Close()
	if res.IsError() {
		return fmt.Errorf("ES Index Error: %s", res.String())
	}
	log.Printf("ES Index Success: %s", id)
	return nil
}

func getEnv(key, fallback string) string {
	if value, ok := os.LookupEnv(key); ok {
		return value
	}
	return fallback
}
