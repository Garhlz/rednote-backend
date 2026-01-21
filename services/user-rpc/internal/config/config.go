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
	Redis redis.RedisConf
	Mail  struct {
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
