package main

import (
	"flag"
	"fmt"

	"interaction-rpc/interaction"
	"interaction-rpc/internal/config"
	appmetrics "interaction-rpc/internal/metrics"
	"interaction-rpc/internal/server"
	"interaction-rpc/internal/svc"

	"github.com/zeromicro/go-zero/core/conf"
	"github.com/zeromicro/go-zero/core/service"
	"github.com/zeromicro/go-zero/zrpc"
	"google.golang.org/grpc"
	"google.golang.org/grpc/reflection"
)

var configFile = flag.String("f", "etc/interaction.yaml", "the config file")

func main() {
	flag.Parse()

	var c config.Config
	conf.MustLoad(*configFile, &c)
	ctx := svc.NewServiceContext(c)
	// 互动服务额外起一个独立的 metrics 端口，供 Prometheus 抓取。
	// 这样不会影响 gRPC 监听端口，也不需要把 /metrics 混进 RPC 服务里。
	appmetrics.StartServer(c.Metrics.Host, c.Metrics.Port, c.Metrics.Path)

	s := zrpc.MustNewServer(c.RpcServerConf, func(grpcServer *grpc.Server) {
		interaction.RegisterInteractionServiceServer(grpcServer, server.NewInteractionServiceServer(ctx))

		if c.Mode == service.DevMode || c.Mode == service.TestMode {
			reflection.Register(grpcServer)
		}
	})
	defer s.Stop()

	fmt.Printf("Starting rpc server at %s...\n", c.ListenOn)
	s.Start()
}
