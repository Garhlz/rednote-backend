package svc

import (
	"net"
	"net/http"
	"os"
	"time"

	"gateway-api/internal/config"

	"github.com/zeromicro/go-zero/core/stores/redis"
	"github.com/zeromicro/go-zero/zrpc"
)

type ServiceContext struct {
	Config          config.Config
	UserRpc         zrpc.Client
	SearchRpc       zrpc.Client
	InteractionRpc  zrpc.Client
	NotificationRpc zrpc.Client
	Redis           *redis.Redis
	JavaHttpClient  *http.Client
	// JavaApiBaseUrl 是 Java 主业务服务的入口地址。
	// 当前项目里，很多帖子/评论/通知/后台接口仍然由 Java 负责，网关需要反向代理到这里。
	JavaApiBaseUrl string
}

func NewServiceContext(c config.Config) *ServiceContext {
	// 三个 RPC client 对应网关后面的 Go 微服务。
	// gateway-api 自己不保存业务状态，它主要负责把请求分发到这些后端能力上。
	userClient := zrpc.MustNewClient(c.UserRpc)
	searchClient := zrpc.MustNewClient(c.SearchRpc)
	interactionClient := zrpc.MustNewClient(c.InteractionRpc)
	notificationClient := zrpc.MustNewClient(c.NotificationRpc)

	// Java 地址支持三层来源：
	// 1. 配置文件
	// 2. 环境变量
	// 3. 默认回退值
	// 这样能兼容“Java 跑容器内”和“Java 跑宿主机”两种开发模式。
	javaBaseURL := c.JavaApi.BaseUrl
	if javaBaseURL == "" {
		javaBaseURL = os.Getenv("JAVA_API_BASE_URL")
	}
	if javaBaseURL == "" {
		javaBaseURL = "http://host.docker.internal:8080"
	}

	timeout := time.Duration(c.JavaApi.TimeoutMs) * time.Millisecond
	if timeout == 0 {
		timeout = 8 * time.Second
	}
	// Java 代理单独维护一个 HTTP Client，统一控制超时、连接池和代理设置。
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
		Config:          c,
		UserRpc:         userClient,
		SearchRpc:       searchClient,
		InteractionRpc:  interactionClient,
		NotificationRpc: notificationClient,
		Redis:           redis.MustNewRedis(c.Redis),
		JavaHttpClient:  &http.Client{Timeout: timeout, Transport: transport},
		JavaApiBaseUrl:  javaBaseURL,
	}
}
