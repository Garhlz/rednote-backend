package middleware_test

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"gateway-api/internal/middleware"
	"gateway-api/internal/pkg/ctxutil"
	"gateway-api/internal/svc"

	javaproxy "gateway-api/internal/handler/javaproxy"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// TestRequestIdMiddleware_PropagatesRequestId 验证 RequestIdMiddleware 生成的 requestId
// 能正确写入响应头，且 context 中可以通过 ctxutil 取到相同值。
func TestRequestIdMiddleware_PropagatesRequestId(t *testing.T) {
	const fixedRequestId = "test-request-id-123"

	m := middleware.NewRequestIdMiddleware()
	handler := m.Handle(func(w http.ResponseWriter, r *http.Request) {
		// 在 handler 内验证 context 中已注入 requestId
		gotFromCtx := ctxutil.RequestID(r.Context())
		w.Header().Set("X-Got-From-Ctx", gotFromCtx)
		w.WriteHeader(http.StatusOK)
	})

	req := httptest.NewRequest(http.MethodGet, "/api/test", nil)
	req.Header.Set("X-Request-Id", fixedRequestId)
	rec := httptest.NewRecorder()

	handler(rec, req)

	assert.Equal(t, fixedRequestId, rec.Header().Get("X-Request-Id"),
		"响应头应包含透传的 requestId")
	assert.Equal(t, fixedRequestId, rec.Header().Get("X-Trace-Id"),
		"traceId 应与 requestId 同步")
	assert.Equal(t, fixedRequestId, rec.Header().Get("X-Got-From-Ctx"),
		"context 中应能取到注入的 requestId")
}

// TestRequestIdMiddleware_GeneratesIdWhenMissing 验证没有客户端传入时自动生成 requestId。
func TestRequestIdMiddleware_GeneratesIdWhenMissing(t *testing.T) {
	m := middleware.NewRequestIdMiddleware()
	handler := m.Handle(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	})

	req := httptest.NewRequest(http.MethodGet, "/api/test", nil)
	rec := httptest.NewRecorder()

	handler(rec, req)

	generated := rec.Header().Get("X-Request-Id")
	assert.NotEmpty(t, generated, "没有客户端传入时应自动生成 requestId")
	assert.Equal(t, generated, rec.Header().Get("X-Trace-Id"),
		"自动生成时 traceId 应等于 requestId")
}

// TestRequestIdMiddleware_ToJavaProxy 联合测试：
// RequestIdMiddleware → ProxyHandler，验证 requestId/traceId 能透传到上游（javaproxy）。
func TestRequestIdMiddleware_ToJavaProxy(t *testing.T) {
	const fixedRequestId = "trace-xyz-456"

	// 搭建 fake Java 后端，记录收到的请求头
	var (
		receivedRequestId string
		receivedTraceId   string
	)
	fakeJava := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		receivedRequestId = r.Header.Get("X-Request-Id")
		receivedTraceId = r.Header.Get("X-Trace-Id")
		w.WriteHeader(http.StatusOK)
	}))
	defer fakeJava.Close()

	// 构造 svcCtx，指向 fake Java
	svcCtx := &svc.ServiceContext{
		JavaApiBaseUrl: fakeJava.URL,
	}

	// 组装中间件链：RequestIdMiddleware → ProxyHandler
	m := middleware.NewRequestIdMiddleware()
	chain := m.Handle(javaproxy.ProxyHandler(svcCtx))

	req := httptest.NewRequest(http.MethodGet, "/api/user/profile", nil)
	req.Header.Set("X-Request-Id", fixedRequestId)
	rec := httptest.NewRecorder()

	chain(rec, req)

	require.Equal(t, http.StatusOK, rec.Code)
	assert.Equal(t, fixedRequestId, receivedRequestId,
		"javaproxy 上游应收到 X-Request-Id")
	assert.Equal(t, fixedRequestId, receivedTraceId,
		"javaproxy 上游应收到 X-Trace-Id")
}
