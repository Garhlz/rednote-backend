package svc

import (
	"context"
	"interaction-rpc/internal/config"

	"github.com/rabbitmq/amqp091-go"         // RabbitMQ 官方驱动
	"github.com/zeromicro/go-zero/core/logx" // 用于打印日志
	"github.com/zeromicro/go-zero/core/stores/redis"
	"go.mongodb.org/mongo-driver/mongo"
	"go.mongodb.org/mongo-driver/mongo/options"
)

type ServiceContext struct {
	Config config.Config

	// 1. 定义 Redis 客户端变量
	Redis *redis.Redis

	// 2. 定义 RabbitMQ 连接和通道
	// 注意：为了演示简单，这里只保存 Channel。
	// 生产环境通常需要封装一个断线重连的 Wrapper。
	MqConn    *amqp091.Connection
	MqChannel *amqp091.Channel

	Mongo *mongo.Database
}

func NewServiceContext(c config.Config) *ServiceContext {
	// A. 初始化 BizRedis
	// MustNewRedis 意味着如果连不上(比如配置错了)，程序直接崩溃报错，防止带病运行
	redisClient := redis.MustNewRedis(c.BizRedis)

	// B. 初始化 RabbitMQ
	// 建立 TCP 连接
	conn, err := amqp091.Dial(c.RabbitMQ.DataSource)
	if err != nil {
		// 既然是微服务，如果连不上 MQ，启动没有任何意义，直接 panic
		panic("Failed to connect to RabbitMQ: " + err.Error())
	}

	// 建立 Channel (轻量级信道，后续发消息都通过它)
	ch, err := conn.Channel()
	if err != nil {
		panic("Failed to open a RabbitMQ channel: " + err.Error())
	}

	var mongoDb *mongo.Database
	if c.Mongo.Uri != "" {
		client, err := mongo.Connect(context.Background(), options.Client().ApplyURI(c.Mongo.Uri))
		if err != nil {
			panic("Failed to connect MongoDB: " + err.Error())
		}
		dbName := c.Mongo.Database
		if dbName == "" {
			dbName = "rednote"
		}
		mongoDb = client.Database(dbName)
	}

	logx.Info("RabbitMQ, BizRedis, Mongo initialized successfully!")

	return &ServiceContext{
		Config:    c,
		Redis:     redisClient,
		MqConn:    conn,
		MqChannel: ch,
		Mongo:     mongoDb,
	}
}
