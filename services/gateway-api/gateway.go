// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package main

import (
	"context"
	"flag"
	"fmt"

	"gateway-api/internal/config"
	"gateway-api/internal/handler"
	"gateway-api/internal/middleware"
	"gateway-api/internal/response"
	"gateway-api/internal/svc"

	"github.com/zeromicro/go-zero/core/conf"
	"github.com/zeromicro/go-zero/rest"
	"github.com/zeromicro/go-zero/rest/httpx"
)

var configFile = flag.String("f", "etc/gateway.yaml", "the config file")

func main() {
	flag.Parse()

	var c config.Config
	conf.MustLoad(*configFile, &c, conf.UseEnv())

	server := rest.MustNewServer(c.RestConf)
	defer server.Stop()

	httpx.SetOkHandler(func(_ context.Context, data any) any {
		return response.Ok(data)
	})
	httpx.SetErrorHandlerCtx(response.ErrorHandler)

	ctx := svc.NewServiceContext(c)
	server.Use(middleware.NewAuthMiddleware(c, ctx.Redis))
	handler.RegisterHandlers(server, ctx)

	fmt.Printf("Starting server at %s:%d...\n", c.Host, c.Port)
	server.Start()
}
