package logic

import (
	"context"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"google.golang.org/grpc/metadata"
)

func TestRequestIDFromContext(t *testing.T) {
	tests := []struct {
		name      string
		buildCtx  func() context.Context
		wantValue string // 若非空则精确匹配，否则只断言非空
	}{
		{
			name: "ctx 为 nil 时不 panic，返回非空字符串（fallback UnixNano base36）",
			buildCtx: func() context.Context {
				return nil
			},
			wantValue: "", // 只校验非空
		},
		{
			name: "context.Background() 无 gRPC metadata 时返回非空字符串（fallback UnixNano base36）",
			buildCtx: func() context.Context {
				return context.Background()
			},
			wantValue: "", // 只校验非空
		},
		{
			name: "metadata 中有 x-request-id 时返回对应值",
			buildCtx: func() context.Context {
				md := metadata.Pairs("x-request-id", "req-abc-123")
				return metadata.NewIncomingContext(context.Background(), md)
			},
			wantValue: "req-abc-123",
		},
		{
			name: "metadata 中只有 x-trace-id（无 x-request-id）时返回 trace-id 值",
			buildCtx: func() context.Context {
				md := metadata.Pairs("x-trace-id", "trace-xyz-456")
				return metadata.NewIncomingContext(context.Background(), md)
			},
			wantValue: "trace-xyz-456",
		},
		{
			name: "x-request-id 优先于 x-trace-id",
			buildCtx: func() context.Context {
				md := metadata.Pairs(
					"x-request-id", "req-priority",
					"x-trace-id", "trace-ignored",
				)
				return metadata.NewIncomingContext(context.Background(), md)
			},
			wantValue: "req-priority",
		},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			// require.NotPanics 确保函数不 panic
			var result string
			require.NotPanics(t, func() {
				result = requestIDFromContext(tc.buildCtx())
			})

			// 始终断言返回值非空
			assert.NotEmpty(t, result, "requestIDFromContext 不应返回空字符串")

			// 如果期望精确值，则精确匹配
			if tc.wantValue != "" {
				assert.Equal(t, tc.wantValue, result)
			}
		})
	}
}
