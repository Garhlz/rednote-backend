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
		originalDirector(req)

		headers := make(http.Header)
		for _, key := range passHeaders {
			if value := req.Header.Get(key); value != "" {
				headers.Set(key, value)
			}
		}
		if headers.Get("X-Request-Id") == "" {
			headers.Set("X-Request-Id", newRequestId())
		}

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
		if resp.StatusCode >= http.StatusInternalServerError {
			logx.WithContext(resp.Request.Context()).Errorf("upstream status %d: %s %s", resp.StatusCode, resp.Request.Method, resp.Request.URL.Path)
		}
		return nil
	}

	return func(w http.ResponseWriter, r *http.Request) {
		proxy.ServeHTTP(w, r)
	}
}

func writeProxyError(w http.ResponseWriter, status int, message string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(map[string]any{
		"code":    status * 100,
		"message": message,
		"data":    nil,
	})
}

func newRequestId() string {
	now := time.Now().UnixNano()
	return strconv.FormatInt(now, 36)
}
