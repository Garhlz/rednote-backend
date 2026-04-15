package middleware

import (
	"context"
	"net/http"
	"strconv"
	"time"

	"gateway-api/internal/pkg/ctxutil"
	"gateway-api/internal/telemetry"

	"github.com/zeromicro/go-zero/core/logx"
)

const (
	requestIDHeader = "X-Request-Id"
	traceIDHeader   = "X-Trace-Id"
)

// RequestIdMiddleware 统一生成/透传请求标识。
// 当前系统先把 traceId 和 requestId 统一成同一个值，方便 gateway、Go RPC、Java 和 MQ 日志串起来。
type RequestIdMiddleware struct{}

func NewRequestIdMiddleware() *RequestIdMiddleware {
	return &RequestIdMiddleware{}
}

func (m *RequestIdMiddleware) Handle(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		requestID := accessRequestID(r)
		w.Header().Set(requestIDHeader, requestID)
		w.Header().Set(traceIDHeader, requestID)

		ctx := context.WithValue(r.Context(), "requestId", requestID)
		ctx = context.WithValue(ctx, "traceId", requestID)
		ctx = logx.ContextWithFields(ctx,
			logx.Field("requestId", requestID),
			logx.Field("traceId", requestID),
		)

		next(w, r.WithContext(ctx))
	}
}

func accessRequestID(r *http.Request) string {
	if requestID := r.Header.Get(requestIDHeader); requestID != "" {
		return requestID
	}
	if traceID := r.Header.Get(traceIDHeader); traceID != "" {
		return traceID
	}
	if traceID := telemetry.TraceID(r.Context()); traceID != "" {
		return traceID
	}
	if requestID := ctxutil.RequestID(r.Context()); requestID != "" {
		return requestID
	}

	return strconv.FormatInt(time.Now().UnixNano(), 36)
}
