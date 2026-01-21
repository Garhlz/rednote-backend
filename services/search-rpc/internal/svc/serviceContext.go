package svc

import (
	"context"
	"time"

	"search-rpc/internal/config"

	"github.com/elastic/go-elasticsearch/v8"
	"go.mongodb.org/mongo-driver/mongo"
	"go.mongodb.org/mongo-driver/mongo/options"
)

type ServiceContext struct {
	Config config.Config
	Es     *elasticsearch.Client
	Mongo  *mongo.Database
}

func NewServiceContext(c config.Config) *ServiceContext {
	// 1. 初始化 Elasticsearch 客户端
	esConfig := elasticsearch.Config{
		Addresses: c.Elasticsearch.Addresses,
		Username:  c.Elasticsearch.Username,
		Password:  c.Elasticsearch.Password,
	}
	esClient, err := elasticsearch.NewClient(esConfig)
	if err != nil {
		panic("Failed to init ES client: " + err.Error())
	}

	// 2. 初始化 MongoDB 客户端
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	// 设置 MongoDB 连接选项
	clientOptions := options.Client().ApplyURI(c.MongoDB.Url)
	mongoClient, err := mongo.Connect(ctx, clientOptions)
	if err != nil {
		panic("Failed to connect to MongoDB: " + err.Error())
	}

	// 验证连接
	if err := mongoClient.Ping(ctx, nil); err != nil {
		panic("Failed to ping MongoDB: " + err.Error())
	}

	return &ServiceContext{
		Config: c,
		Es:     esClient,
		Mongo:  mongoClient.Database(c.MongoDB.Database),
	}
}
