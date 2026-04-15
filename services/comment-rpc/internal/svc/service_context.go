package svc

import (
	"context"
	"fmt"
	"log"
	"time"

	"comment-rpc/internal/config"

	"github.com/rabbitmq/amqp091-go"
	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/mongo"
	"go.mongodb.org/mongo-driver/mongo/options"
)

type ServiceContext struct {
	Config config.Config
	Mongo  *mongo.Database
	MqConn *amqp091.Connection
	MqChan *amqp091.Channel
}

func NewServiceContext(c config.Config) *ServiceContext {
	var mongoDB *mongo.Database
	if c.Mongo.Uri != "" {
		client, err := mongo.Connect(context.Background(), options.Client().ApplyURI(c.Mongo.Uri))
		if err != nil {
			panic("failed to connect MongoDB: " + err.Error())
		}
		dbName := c.Mongo.Database
		if dbName == "" {
			dbName = "rednote"
		}
		mongoDB = client.Database(dbName)
		ensureCommentIndexes(mongoDB)
	}

	var mqConn *amqp091.Connection
	var mqChan *amqp091.Channel
	if c.RabbitMQ.DataSource != "" {
		conn, ch, err := connectRabbitMQWithRetry(c.RabbitMQ.DataSource, 12, 2*time.Second)
		if err != nil {
			panic("failed to connect RabbitMQ: " + err.Error())
		}
		mqConn = conn
		mqChan = ch
	}

	return &ServiceContext{
		Config: c,
		Mongo:  mongoDB,
		MqConn: mqConn,
		MqChan: mqChan,
	}
}

func connectRabbitMQWithRetry(dataSource string, maxAttempts int, interval time.Duration) (*amqp091.Connection, *amqp091.Channel, error) {
	var lastErr error
	for attempt := 1; attempt <= maxAttempts; attempt++ {
		conn, err := amqp091.Dial(dataSource)
		if err == nil {
			ch, chErr := conn.Channel()
			if chErr == nil {
				if attempt > 1 {
					log.Printf("rabbitmq connected after retry attempt=%d", attempt)
				}
				return conn, ch, nil
			}
			_ = conn.Close()
			err = fmt.Errorf("open channel: %w", chErr)
		}

		lastErr = err
		log.Printf("rabbitmq connect retry attempt=%d/%d err=%v", attempt, maxAttempts, err)
		if attempt < maxAttempts {
			time.Sleep(interval)
		}
	}

	return nil, nil, lastErr
}

func ensureCommentIndexes(db *mongo.Database) {
	ctx := context.Background()

	_, err := db.Collection("comments").Indexes().CreateMany(ctx, []mongo.IndexModel{
		{
			Keys:    bson.D{{Key: "postId", Value: 1}, {Key: "parentId", Value: 1}, {Key: "createdAt", Value: -1}},
			Options: options.Index().SetName("idx_comments_post_parent_created"),
		},
		{
			Keys:    bson.D{{Key: "parentId", Value: 1}, {Key: "likeCount", Value: -1}, {Key: "createdAt", Value: -1}},
			Options: options.Index().SetName("idx_comments_parent_like_created"),
		},
	})
	if err != nil {
		panic("failed to create comment indexes: " + err.Error())
	}

	_, err = db.Collection("comment_likes").Indexes().CreateMany(ctx, []mongo.IndexModel{
		{
			Keys: bson.D{{Key: "userId", Value: 1}, {Key: "commentId", Value: 1}},
			// 与 Java 侧 CommentLikeDoc 上已有的唯一索引名保持一致，
			// 否则 Mongo 会因为“同键模式、不同索引名”报 IndexOptionsConflict，
			// 导致 comment-rpc 启动失败。
			Options: options.Index().SetName("idx_user_comment_unique").SetUnique(true),
		},
		{
			Keys: bson.D{{Key: "commentId", Value: 1}},
			// 与 Java 侧 CommentLikeDoc 上已有的单字段索引名保持一致。
			Options: options.Index().SetName("commentId"),
		},
	})
	if err != nil {
		// 老环境里 comment_likes 的索引可能已由 Java 侧以不同方式创建过，
		// 或者历史数据不满足唯一约束。这里记录告警但不阻断服务启动，
		// 避免 comment-rpc 因索引兼容问题无法注册到 etcd，导致整条评论链路 502。
		log.Printf("warning: failed to create comment like indexes: %v", err)
	}
}
