package main

import (
	"context"
	"flag"
	"fmt"
	"time"

	"search-rpc/internal/config"
	appmetrics "search-rpc/internal/metrics"
	"search-rpc/internal/server"
	"search-rpc/internal/svc"
	"search-rpc/internal/trace"
	"search-rpc/search"

	"github.com/zeromicro/go-zero/core/conf"
	"github.com/zeromicro/go-zero/core/logx"
	"github.com/zeromicro/go-zero/core/service"
	"github.com/zeromicro/go-zero/zrpc"
	"google.golang.org/grpc"
	"google.golang.org/grpc/reflection"
)

var configFile = flag.String("f", "etc/search.yaml", "the config file")

func main() {
	flag.Parse()

	var c config.Config
	conf.MustLoad(*configFile, &c, conf.UseEnv())
	shutdownTelemetry, err := trace.InitProvider(context.Background(), "search-rpc")
	if err != nil {
		panic(err)
	}
	defer func() {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		_ = shutdownTelemetry(ctx)
	}()
	logx.AddGlobalFields(logx.Field("service", "search-rpc"))
	ctx := svc.NewServiceContext(c)
	appmetrics.StartServer(c.Metrics.Host, c.Metrics.Port, c.Metrics.Path)

	s := zrpc.MustNewServer(c.RpcServerConf, func(grpcServer *grpc.Server) {
		search.RegisterSearchServiceServer(grpcServer, server.NewSearchServiceServer(ctx))

		if c.Mode == service.DevMode || c.Mode == service.TestMode {
			reflection.Register(grpcServer)
		}
	})
	defer s.Stop()
	s.AddUnaryInterceptors(trace.UnaryServerInterceptor("search-rpc"))

	fmt.Printf("Starting rpc server at %s...\n", c.ListenOn)
	s.Start()
}
