package config

import (
	"github.com/zeromicro/go-zero/core/stores/redis"
	"github.com/zeromicro/go-zero/zrpc"
)

type Config struct {
	zrpc.RpcServerConf
	Postgres struct {
		DataSource string
	}
	BizRedis redis.RedisConf
	RabbitMQ struct {
		Enabled              string
		Url                  string
		Exchange             string
		UserUpdateRoutingKey string
		UserDeleteRoutingKey string
	}
	Mail struct {
		Host   string
		Port   int
		User   string
		Pass   string
		From   string
		UseSSL bool
	}
	Jwt struct {
		Secret               string
		AccessExpireSeconds  int64
		RefreshExpireSeconds int64
		Issuer               string
	}
	User struct {
		DefaultAvatar string
	}
}
