package svc

import (
	"context"

	"notification-rpc/internal/config"

	"go.mongodb.org/mongo-driver/mongo"
	"go.mongodb.org/mongo-driver/mongo/options"
)

type ServiceContext struct {
	Config config.Config
	// Mongo 指向通知所在的业务库。
	// 当前 notification-rpc 还没有引入缓存层，读写都直接命中这个集合。
	Mongo *mongo.Database
}

func NewServiceContext(c config.Config) *ServiceContext {
	var mongoDb *mongo.Database
	if c.Mongo.Uri != "" {
		// 通知服务当前的数据主存储就是 MongoDB。
		// 如果连接失败，直接 panic，让容器尽快失败重启，而不是带着半残状态运行。
		client, err := mongo.Connect(context.Background(), options.Client().ApplyURI(c.Mongo.Uri))
		if err != nil {
			panic("failed to connect MongoDB: " + err.Error())
		}
		dbName := c.Mongo.Database
		if dbName == "" {
			dbName = "rednote"
		}
		mongoDb = client.Database(dbName)
	}

	return &ServiceContext{
		Config: c,
		Mongo:  mongoDb,
	}
}
