package ctxutil

import "context"

// ctxutil 是网关里“请求上下文读写约定”的薄封装。
// 鉴权中间件会把用户身份写入 context，后续 logic / proxy 再通过这些辅助函数统一读取，
// 避免到处直接写裸字符串 key 的类型断言。
func UserID(ctx context.Context) int64 {
	if v := ctx.Value("userId"); v != nil {
		if id, ok := v.(int64); ok {
			return id
		}
	}
	return 0
}

func Role(ctx context.Context) string {
	if v := ctx.Value("role"); v != nil {
		if role, ok := v.(string); ok {
			return role
		}
	}
	return ""
}

func Nickname(ctx context.Context) string {
	if v := ctx.Value("nickname"); v != nil {
		if nickname, ok := v.(string); ok {
			return nickname
		}
	}
	return ""
}

func AuthToken(ctx context.Context) string {
	if v := ctx.Value("accessToken"); v != nil {
		if token, ok := v.(string); ok {
			return token
		}
	}
	return ""
}

func RefreshToken(ctx context.Context) string {
	if v := ctx.Value("refreshToken"); v != nil {
		if token, ok := v.(string); ok {
			return token
		}
	}
	return ""
}

func RequestID(ctx context.Context) string {
	if v := ctx.Value("requestId"); v != nil {
		if requestID, ok := v.(string); ok {
			return requestID
		}
	}
	return ""
}

func TraceID(ctx context.Context) string {
	if v := ctx.Value("traceId"); v != nil {
		if traceID, ok := v.(string); ok {
			return traceID
		}
	}
	return ""
}
