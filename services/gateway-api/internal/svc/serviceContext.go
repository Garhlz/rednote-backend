package svc

import (
	"net"
	"net/http"
	"time"

	"gateway-api/internal/config"

	"github.com/zeromicro/go-zero/core/stores/redis"
	"github.com/zeromicro/go-zero/zrpc"
)

type ServiceContext struct {
	Config         config.Config
	UserRpc        zrpc.Client
	SearchRpc      zrpc.Client
	InteractionRpc zrpc.Client
	Redis          *redis.Redis
	JavaHttpClient *http.Client
	JavaApiBaseUrl string
}

func NewServiceContext(c config.Config) *ServiceContext {
	userClient := zrpc.MustNewClient(c.UserRpc)
	searchClient := zrpc.MustNewClient(c.SearchRpc)
	interactionClient := zrpc.MustNewClient(c.InteractionRpc)

	timeout := time.Duration(c.JavaApi.TimeoutMs) * time.Millisecond
	if timeout == 0 {
		timeout = 8 * time.Second
	}
	transport := &http.Transport{
		Proxy: http.ProxyFromEnvironment,
		DialContext: (&net.Dialer{
			Timeout:   5 * time.Second,
			KeepAlive: 30 * time.Second,
		}).DialContext,
		ResponseHeaderTimeout: timeout,
		IdleConnTimeout:       90 * time.Second,
		MaxIdleConns:          100,
		MaxIdleConnsPerHost:   10,
	}

	return &ServiceContext{
		Config:         c,
		UserRpc:        userClient,
		SearchRpc:      searchClient,
		InteractionRpc: interactionClient,
		Redis:          redis.MustNewRedis(c.Redis),
		JavaHttpClient: &http.Client{Timeout: timeout, Transport: transport},
		JavaApiBaseUrl: c.JavaApi.BaseUrl,
	}
}
