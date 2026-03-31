package config

import "github.com/zeromicro/go-zero/zrpc"

type Config struct {
	zrpc.RpcServerConf
	Metrics struct {
		Host string
		Port int
		Path string
	}

	Elasticsearch struct {
		Addresses []string
		Username  string
		Password  string
	}

	MongoDB struct {
		Url      string
		Database string
	}
}
