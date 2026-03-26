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
	// 启动时读取网关配置，并允许环境变量覆盖。
	// 这样在 Docker 内外切换 Java 地址、Redis 地址、RPC 地址时，不必反复改源码。
	conf.MustLoad(*configFile, &c, conf.UseEnv())

	// 创建 go-zero 的 HTTP Server。
	// gateway-api 本身就是整个系统对外暴露的统一入口。
	server := rest.MustNewServer(c.RestConf)
	defer server.Stop()

	// 统一成功响应包装。
	// 业务 logic 返回的 data 会在这里被包成 {code, message, data} 风格的统一响应。
	httpx.SetOkHandler(func(_ context.Context, data any) any {
		return response.Ok(data)
	})
	// 统一错误响应包装。
	// 这样各个 logic / middleware 不需要自己关心最终的 JSON 错误结构。
	httpx.SetErrorHandlerCtx(response.ErrorHandler)

	// ServiceContext 是网关的依赖注入中心。
	// 里面统一初始化：
	// 1. user/search/interaction 三个 RPC client
	// 2. Redis
	// 3. Java 代理用的 HTTP Client
	ctx := svc.NewServiceContext(c)

	// 所有请求先经过鉴权中间件：
	// 1. 判断是否跳过鉴权
	// 2. 解析 JWT
	// 3. 校验 token version
	// 4. 把 userId / role / nickname 等信息放进 context
	server.Use(middleware.NewAuthMiddleware(c, ctx.Redis))

	// 注册所有路由。
	// 路由层只决定“这个请求由谁处理”：
	// 1. 纯代理到 Java
	// 2. 直接调用 Go RPC
	// 3. 由网关自己做聚合编排
	handler.RegisterHandlers(server, ctx)

	fmt.Printf("Starting server at %s:%d...\n", c.Host, c.Port)
	server.Start()
}
