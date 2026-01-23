package config

import (
	"github.com/zeromicro/go-zero/core/stores/redis"
	"github.com/zeromicro/go-zero/rest"
	"github.com/zeromicro/go-zero/zrpc"
)

type Config struct {
	rest.RestConf
	UserRpc        zrpc.RpcClientConf
	SearchRpc      zrpc.RpcClientConf
	InteractionRpc zrpc.RpcClientConf
	Redis          redis.RedisConf
	JavaApi        struct {
		BaseUrl   string
		TimeoutMs int64
	}
	Jwt struct {
		Secret            string
		IgnorePatterns    []string
		CheckTokenVersion bool
	}
}
