package javaproxy

import (
	"context"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"

	"gateway-api/internal/svc"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// makeTestSvcCtx 创建一个指向假 Java 后端的 ServiceContext，用于测试代理行为。
func makeTestSvcCtx(upstream *httptest.Server) *svc.ServiceContext {
	return &svc.ServiceContext{
		JavaApiBaseUrl: upstream.URL,
		JavaHttpClient: upstream.Client(),
	}
}

func TestProxyHandler_PassesUserHeaders(t *testing.T) {
	// 假 Java 后端：记录收到的请求头
	var capturedUserID, capturedRole, capturedNickname string
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		capturedUserID = r.Header.Get("X-User-Id")
		capturedRole = r.Header.Get("X-User-Role")
		capturedNickname = r.Header.Get("X-User-Nickname")
		w.WriteHeader(http.StatusOK)
	}))
	defer upstream.Close()

	svcCtx := makeTestSvcCtx(upstream)
	handler := ProxyHandler(svcCtx)

	// 构造一个已注入用户上下文的请求（模拟鉴权中间件处理后的状态）
	req := httptest.NewRequest(http.MethodGet, "/api/some/path", nil)
	ctx := context.WithValue(req.Context(), "userId", int64(42))
	ctx = context.WithValue(ctx, "role", "admin")
	ctx = context.WithValue(ctx, "nickname", "小红")
	req = req.WithContext(ctx)

	rec := httptest.NewRecorder()
	handler(rec, req)

	assert.Equal(t, "42", capturedUserID, "X-User-Id 应透传 userId")
	assert.Equal(t, "admin", capturedRole, "X-User-Role 应透传 role")
	assert.Equal(t, "小红", capturedNickname, "X-User-Nickname 应透传 nickname")
}

func TestProxyHandler_AnonymousRequestNoUserHeaders(t *testing.T) {
	// 匿名请求（ctx 中无 userId）不应发送用户头到 Java
	var capturedUserID string
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		capturedUserID = r.Header.Get("X-User-Id")
		w.WriteHeader(http.StatusOK)
	}))
	defer upstream.Close()

	svcCtx := makeTestSvcCtx(upstream)
	handler := ProxyHandler(svcCtx)

	req := httptest.NewRequest(http.MethodGet, "/api/post/list", nil)
	rec := httptest.NewRecorder()
	handler(rec, req)

	assert.Empty(t, capturedUserID, "匿名请求不应透传 X-User-Id")
}

func TestProxyHandler_UpstreamErrorReturns502(t *testing.T) {
	// 上游不可达时应返回 502
	svcCtx := &svc.ServiceContext{
		JavaApiBaseUrl: "http://127.0.0.1:1", // 不可达端口
		JavaHttpClient: &http.Client{},
	}
	handler := ProxyHandler(svcCtx)

	req := httptest.NewRequest(http.MethodGet, "/api/test", nil)
	rec := httptest.NewRecorder()
	handler(rec, req)

	assert.Equal(t, http.StatusBadGateway, rec.Code, "上游不可达应返回 502")
}

func TestProxyHandler_UpstreamResponsePassedThrough(t *testing.T) {
	// 上游正常响应时，状态码和 body 应透传
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusCreated)
		_, _ = w.Write([]byte(`{"code":200,"data":null}`))
	}))
	defer upstream.Close()

	svcCtx := makeTestSvcCtx(upstream)
	handler := ProxyHandler(svcCtx)

	req := httptest.NewRequest(http.MethodPost, "/api/post/create", nil)
	rec := httptest.NewRecorder()
	handler(rec, req)

	require.Equal(t, http.StatusCreated, rec.Code)
	body, _ := io.ReadAll(rec.Body)
	assert.Contains(t, string(body), `"code":200`)
}

func TestProxyHandler_InvalidUpstreamConfigReturns502(t *testing.T) {
	// JavaApiBaseUrl 为非法 URL 时，handler 初始化即失败并返回 502
	svcCtx := &svc.ServiceContext{
		JavaApiBaseUrl: "://invalid-url",
	}
	handler := ProxyHandler(svcCtx)

	req := httptest.NewRequest(http.MethodGet, "/api/test", nil)
	rec := httptest.NewRecorder()
	handler(rec, req)

	assert.Equal(t, http.StatusBadGateway, rec.Code)
}
