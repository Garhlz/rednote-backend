package javaproxy

import (
	"context"
	"encoding/json"
	"errors"
	"net"
	"net/http"
	"net/http/httputil"
	"net/url"
	"strconv"
	"time"

	"gateway-api/internal/pkg/ctxutil"
	"gateway-api/internal/svc"

	"github.com/zeromicro/go-zero/core/logx"
)

var passHeaders = []string{
	"Authorization",
	"Content-Type",
	"Content-Length",
	"X-Admin-Token",
	"X-Request-Id",
	"X-Forwarded-For",
	"X-Real-Ip",
	"User-Agent",
}

const (
	userIdHeader   = "X-User-Id"
	userRoleHeader = "X-User-Role"
	userNameHeader = "X-User-Nickname"
)

// ProxyHandler 是网关中“纯反向代理”职责的核心实现。
// 对于还没有迁移到 Go 微服务的 Java 能力，网关不重复实现业务逻辑，
// 而是统一做：
// 1. 请求转发
// 2. 用户头透传
// 3. 错误包装
// 4. 超时控制
func ProxyHandler(svcCtx *svc.ServiceContext) http.HandlerFunc {
	target, err := url.Parse(svcCtx.JavaApiBaseUrl)
	if err != nil {
		return func(w http.ResponseWriter, r *http.Request) {
			writeProxyError(w, http.StatusBadGateway, "invalid upstream config")
		}
	}

	proxy := httputil.NewSingleHostReverseProxy(target)
	originalDirector := proxy.Director
	proxy.Director = func(req *http.Request) {
		// 先让标准 ReverseProxy 根据 target 改写 URL。
		originalDirector(req)

		// 然后重新构造请求头白名单。
		// 这样可以避免把所有客户端头无差别透传给 Java，减少耦合和脏头风险。
		headers := make(http.Header)
		for _, key := range passHeaders {
			if value := req.Header.Get(key); value != "" {
				headers.Set(key, value)
			}
		}
		if headers.Get("X-Request-Id") == "" {
			headers.Set("X-Request-Id", newRequestId())
		}

		// 把鉴权中间件解析出的用户上下文转成 HTTP 头透传给 Java。
		// 这样 Java 不需要重复解析 JWT，就能拿到用户身份、角色、昵称等信息。
		userId := ctxutil.UserID(req.Context())
		if userId > 0 && headers.Get(userIdHeader) == "" {
			headers.Set(userIdHeader, strconv.FormatInt(userId, 10))
		}
		if role := ctxutil.Role(req.Context()); role != "" && headers.Get(userRoleHeader) == "" {
			headers.Set(userRoleHeader, role)
		}
		if nickname := ctxutil.Nickname(req.Context()); nickname != "" && headers.Get(userNameHeader) == "" {
			headers.Set(userNameHeader, nickname)
		}

		req.Header = headers
	}
	if svcCtx.JavaHttpClient != nil && svcCtx.JavaHttpClient.Transport != nil {
		proxy.Transport = svcCtx.JavaHttpClient.Transport
	}
	proxy.ErrorHandler = func(w http.ResponseWriter, r *http.Request, err error) {
		// 所有 Java 上游错误在这里统一收口，避免直接把底层错误暴露给前端。
		logx.WithContext(r.Context()).Errorf("proxy error: %s %s: %v", r.Method, r.URL.Path, err)
		if errors.Is(err, context.DeadlineExceeded) {
			writeProxyError(w, http.StatusGatewayTimeout, "upstream timeout")
			return
		}
		if netErr, ok := err.(net.Error); ok && netErr.Timeout() {
			writeProxyError(w, http.StatusGatewayTimeout, "upstream timeout")
			return
		}
		writeProxyError(w, http.StatusBadGateway, "upstream error")
	}
	proxy.ModifyResponse = func(resp *http.Response) error {
		// Java 返回 5xx 时，这里统一打日志，便于从网关入口侧观察上游状态。
		if resp.StatusCode >= http.StatusInternalServerError {
			logx.WithContext(resp.Request.Context()).Errorf("upstream status %d: %s %s", resp.StatusCode, resp.Request.Method, resp.Request.URL.Path)
		}
		return nil
	}

	return func(w http.ResponseWriter, r *http.Request) {
		proxy.ServeHTTP(w, r)
	}
}

// writeProxyError 用统一 JSON 结构包装代理错误。
// 这样前端无论面对 RPC 失败还是 Java 代理失败，都能拿到一致风格的错误对象。
func writeProxyError(w http.ResponseWriter, status int, message string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(map[string]any{
		"code":    status * 100,
		"message": message,
		"data":    nil,
	})
}

// newRequestId 为代理请求生成兜底 request id，便于 Java 和网关日志串联。
func newRequestId() string {
	now := time.Now().UnixNano()
	return strconv.FormatInt(now, 36)
}
