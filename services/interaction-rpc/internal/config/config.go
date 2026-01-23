package config

import (
	"github.com/zeromicro/go-zero/core/stores/redis" // 引入 redis 定义
	"github.com/zeromicro/go-zero/zrpc"
)

type Config struct {
	zrpc.RpcServerConf

	// 对应 YAML 中的 BizRedis 部分
	// go-zero 会自动识别 structure 中的 json tag 来进行映射
	BizRedis redis.RedisConf

	// 对应 YAML 中的 RabbitMQ 部分
	RabbitMQ struct {
		DataSource string
	}

	Mongo struct {
		Uri      string
		Database string
	}
}
