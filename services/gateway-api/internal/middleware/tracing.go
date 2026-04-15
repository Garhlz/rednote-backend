package middleware

import (
	"net/http"

	"gateway-api/internal/telemetry"

	"github.com/zeromicro/go-zero/core/logx"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/codes"
	oteltrace "go.opentelemetry.io/otel/trace"
)

type tracingResponseWriter struct {
	http.ResponseWriter
	status int
}

func (w *tracingResponseWriter) WriteHeader(code int) {
	w.status = code
	w.ResponseWriter.WriteHeader(code)
}

type TracingMiddleware struct {
	tracer oteltrace.Tracer
}

func NewTracingMiddleware(service string) *TracingMiddleware {
	return &TracingMiddleware{tracer: otel.Tracer(service)}
}

func (m *TracingMiddleware) Handle(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		ctx := otel.GetTextMapPropagator().Extract(r.Context(), propagationHeaderCarrier(r.Header))
		spanName := r.Method + " " + r.URL.Path
		ctx, span := m.tracer.Start(ctx, spanName, oteltrace.WithSpanKind(oteltrace.SpanKindServer))
		defer span.End()

		if traceID := telemetry.TraceID(ctx); traceID != "" {
			ctx = logx.ContextWithFields(ctx, logx.Field("traceId", traceID))
		}

		ww := &tracingResponseWriter{ResponseWriter: w, status: http.StatusOK}
		next(ww, r.WithContext(ctx))

		span.SetAttributes(
			attribute.String("http.method", r.Method),
			attribute.String("http.route", r.URL.Path),
			attribute.Int("http.status_code", ww.status),
		)
		if ww.status >= http.StatusInternalServerError {
			span.SetStatus(codes.Error, http.StatusText(ww.status))
		}
	}
}

type propagationHeaderCarrier http.Header

func (c propagationHeaderCarrier) Get(key string) string {
	return http.Header(c).Get(key)
}

func (c propagationHeaderCarrier) Set(key, value string) {
	http.Header(c).Set(key, value)
}

func (c propagationHeaderCarrier) Keys() []string {
	keys := make([]string, 0, len(c))
	for k := range c {
		keys = append(keys, k)
	}
	return keys
}
