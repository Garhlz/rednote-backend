package svc

import (
	"user-rpc/internal/config"
	"user-rpc/internal/model"
	"user-rpc/internal/mq"

	"github.com/zeromicro/go-zero/core/logx"
	"github.com/zeromicro/go-zero/core/stores/postgres"
	"github.com/zeromicro/go-zero/core/stores/redis"
	"github.com/zeromicro/go-zero/core/stores/sqlx"
)

type ServiceContext struct {
	Config    config.Config
	Db        sqlx.SqlConn
	Redis     *redis.Redis
	Users     model.UsersModel
	Publisher *mq.Publisher
}

func NewServiceContext(c config.Config) *ServiceContext {
	db := postgres.New(c.Postgres.DataSource)
	publisher, err := mq.NewPublisher(c)
	if err != nil {
		logx.Errorf("rabbitmq init failed: %v", err)
		publisher = nil
	}
	return &ServiceContext{
		Config:    c,
		Db:        db,
		Redis:     redis.MustNewRedis(c.BizRedis),
		Users:     model.NewUsersModel(db),
		Publisher: publisher,
	}
}
