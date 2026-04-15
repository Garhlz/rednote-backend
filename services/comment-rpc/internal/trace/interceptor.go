package trace

import (
	"context"
	"strconv"
	"time"

	"github.com/zeromicro/go-zero/core/logx"
	"go.opentelemetry.io/otel"
	oteltrace "go.opentelemetry.io/otel/trace"
	"google.golang.org/grpc"
	"google.golang.org/grpc/metadata"
)

func UnaryServerInterceptor(service string) grpc.UnaryServerInterceptor {
	return func(ctx context.Context, req any, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (any, error) {
		ctx = otel.GetTextMapPropagator().Extract(ctx, metadataCarrierFromIncoming(ctx))
		ctx, span := otel.Tracer(service).Start(ctx, info.FullMethod, oteltrace.WithSpanKind(oteltrace.SpanKindServer))
		defer span.End()
		requestID := requestIDFromMetadata(ctx)
		ctx = logx.ContextWithFields(ctx,
			logx.Field("service", service),
			logx.Field("requestId", requestID),
			logx.Field("traceId", requestID),
		)
		return handler(ctx, req)
	}
}

func requestIDFromMetadata(ctx context.Context) string {
	if md, ok := metadata.FromIncomingContext(ctx); ok {
		if values := md.Get("x-request-id"); len(values) > 0 && values[0] != "" {
			return values[0]
		}
		if values := md.Get("x-trace-id"); len(values) > 0 && values[0] != "" {
			return values[0]
		}
	}
	return strconv.FormatInt(time.Now().UnixNano(), 36)
}

type metadataCarrier metadata.MD

func metadataCarrierFromIncoming(ctx context.Context) metadataCarrier {
	md, _ := metadata.FromIncomingContext(ctx)
	return metadataCarrier(md.Copy())
}

func (c metadataCarrier) Get(key string) string {
	values := metadata.MD(c).Get(key)
	if len(values) == 0 {
		return ""
	}
	return values[0]
}

func (c metadataCarrier) Set(key, value string) {
	metadata.MD(c).Set(key, value)
}

func (c metadataCarrier) Keys() []string {
	keys := make([]string, 0, len(c))
	for k := range metadata.MD(c) {
		keys = append(keys, k)
	}
	return keys
}
