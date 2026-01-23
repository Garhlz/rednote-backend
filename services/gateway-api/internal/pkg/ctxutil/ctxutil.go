package ctxutil

import "context"

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
