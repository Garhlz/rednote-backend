package svc

import (
	"user-rpc/internal/config"
	"user-rpc/internal/model"

	"github.com/zeromicro/go-zero/core/stores/postgres"
	"github.com/zeromicro/go-zero/core/stores/redis"
	"github.com/zeromicro/go-zero/core/stores/sqlx"
)

type ServiceContext struct {
	Config config.Config
	Db     sqlx.SqlConn
	Redis  *redis.Redis
	Users  model.UsersModel
}

func NewServiceContext(c config.Config) *ServiceContext {
	db := postgres.New(c.Postgres.DataSource)
	return &ServiceContext{
		Config: c,
		Db:     db,
		Redis:  redis.MustNewRedis(c.Redis),
		Users:  model.NewUsersModel(db),
	}
}
