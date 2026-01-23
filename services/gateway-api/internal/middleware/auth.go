package middleware

import (
	"context"
	"fmt"
	"net/http"
	"strings"
	"time"

	"gateway-api/internal/config"
	"gateway-api/internal/response"

	"github.com/golang-jwt/jwt/v5"
	"github.com/zeromicro/go-zero/core/stores/redis"
	"github.com/zeromicro/go-zero/rest"
	"github.com/zeromicro/go-zero/rest/httpx"
)

type authClaims struct {
	UserId       int64  `json:"userId"`
	Role         string `json:"role,omitempty"`
	Nickname     string `json:"nickname,omitempty"`
	Type         string `json:"type"`
	TokenVersion int64  `json:"tokenVersion"`
	jwt.RegisteredClaims
}

const tokenVersionKeyPrefix = "auth:token:version:"

func NewAuthMiddleware(cfg config.Config, redisClient *redis.Redis) rest.Middleware {
	secret := []byte(cfg.Jwt.Secret)
	patterns := cfg.Jwt.IgnorePatterns
	checkTokenVersion := cfg.Jwt.CheckTokenVersion && redisClient != nil
	publicPaths := map[string]struct{}{
		"/api/post/list":          {},
		"/api/post/search":        {},
		"/api/post/search/suggest": {},
		"/api/tag/hot":            {},
		"/api/comment/list":       {},
		"/api/comment/sub-list":   {},
	}

	return func(next http.HandlerFunc) http.HandlerFunc {
		return func(w http.ResponseWriter, r *http.Request) {
			if r.URL.Path != "/api/user/profile" && (shouldSkipAuth(r.URL.Path, patterns) || isPublicPath(r.URL.Path, publicPaths)) {
				if r.URL.Path == "/api/auth/logout" {
					ctx := r.Context()
					if token := extractBearer(r.Header.Get("Authorization")); token != "" {
						ctx = context.WithValue(ctx, "accessToken", token)
					}
					if rt := extractBearer(r.Header.Get("X-Refresh-Token")); rt != "" {
						ctx = context.WithValue(ctx, "refreshToken", rt)
					}
					next.ServeHTTP(w, r.WithContext(ctx))
					return
				}
				ctx := r.Context()
				token := extractBearer(r.Header.Get("Authorization"))
				if token != "" {
					claims := &authClaims{}
					_, err := jwt.ParseWithClaims(token, claims, func(t *jwt.Token) (any, error) {
						return secret, nil
					}, jwt.WithLeeway(5*time.Second))
					if err == nil && claims.UserId > 0 && claims.Type == "access" {
						if !checkTokenVersion || tokenVersionOk(r.Context(), redisClient, claims) {
							ctx = context.WithValue(ctx, "userId", claims.UserId)
							ctx = context.WithValue(ctx, "role", claims.Role)
							ctx = context.WithValue(ctx, "nickname", claims.Nickname)
							ctx = context.WithValue(ctx, "accessToken", token)
						}
					}
				}
				next.ServeHTTP(w, r.WithContext(ctx))
				return
			}

			token := extractBearer(r.Header.Get("Authorization"))
			if token == "" {
				httpx.ErrorCtx(r.Context(), w, response.Unauthorized())
				return
			}

			claims := &authClaims{}
			_, err := jwt.ParseWithClaims(token, claims, func(t *jwt.Token) (any, error) {
				return secret, nil
			}, jwt.WithLeeway(5*time.Second))
			if err != nil || claims.UserId == 0 || claims.Type != "access" {
				httpx.ErrorCtx(r.Context(), w, response.TokenExpired())
				return
			}
			if checkTokenVersion {
				key := fmt.Sprintf("%s%d", tokenVersionKeyPrefix, claims.UserId)
				value, err := redisClient.GetCtx(r.Context(), key)
				if err != nil {
					httpx.ErrorCtx(r.Context(), w, response.TokenExpired())
					return
				}
				if value != fmt.Sprintf("%d", claims.TokenVersion) {
					httpx.ErrorCtx(r.Context(), w, response.TokenExpired())
					return
				}
			}

			ctx := context.WithValue(r.Context(), "userId", claims.UserId)
			ctx = context.WithValue(ctx, "role", claims.Role)
			ctx = context.WithValue(ctx, "nickname", claims.Nickname)
			ctx = context.WithValue(ctx, "accessToken", token)
			if rt := extractBearer(r.Header.Get("X-Refresh-Token")); rt != "" {
				ctx = context.WithValue(ctx, "refreshToken", rt)
			}

			next.ServeHTTP(w, r.WithContext(ctx))
		}
	}
}

func extractBearer(value string) string {
	if value == "" {
		return ""
	}
	if strings.HasPrefix(value, "Bearer ") {
		return strings.TrimSpace(strings.TrimPrefix(value, "Bearer "))
	}
	return ""
}

func tokenVersionOk(ctx context.Context, redisClient *redis.Redis, claims *authClaims) bool {
	if redisClient == nil || claims == nil {
		return false
	}
	key := fmt.Sprintf("%s%d", tokenVersionKeyPrefix, claims.UserId)
	value, err := redisClient.GetCtx(ctx, key)
	if err != nil {
		return false
	}
	return value == fmt.Sprintf("%d", claims.TokenVersion)
}

func shouldSkipAuth(path string, patterns []string) bool {
	for _, pattern := range patterns {
		if matchPattern(path, pattern) {
			return true
		}
	}
	return false
}

func isPublicPath(path string, publicPaths map[string]struct{}) bool {
	if _, ok := publicPaths[path]; ok {
		return true
	}
	return false
}

func matchPattern(path, pattern string) bool {
	if pattern == "" {
		return false
	}
	if pattern == path {
		return true
	}
	if !strings.Contains(pattern, "*") {
		return false
	}
	parts := strings.Split(pattern, "*")
	if len(parts) == 2 {
		return strings.HasPrefix(path, parts[0]) && strings.HasSuffix(path, parts[1])
	}
	prefix := parts[0]
	return strings.HasPrefix(path, prefix)
}
