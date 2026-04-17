package middleware

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"gateway-api/internal/config"
	"gateway-api/internal/response"

	"github.com/golang-jwt/jwt/v5"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/zeromicro/go-zero/rest/httpx"
)

func init() {
	// 注册和生产一致的 error handler，使 401/403 等状态码能被正确写回。
	httpx.SetErrorHandlerCtx(response.ErrorHandler)
}

const testSecret = "test-secret-key"

// buildToken 生成指定 claims 的 JWT，用于中间件测试。
func buildToken(t *testing.T, claims jwt.MapClaims) string {
	t.Helper()
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	signed, err := token.SignedString([]byte(testSecret))
	require.NoError(t, err)
	return signed
}

// defaultAuthConfig 返回测试用的最小 Config（不开启 CheckTokenVersion）。
func defaultAuthConfig() config.Config {
	return config.Config{
		Jwt: struct {
			Secret            string
			IgnorePatterns    []string
			CheckTokenVersion bool
		}{
			Secret:            testSecret,
			IgnorePatterns:    []string{},
			CheckTokenVersion: false,
		},
	}
}

// applyMiddleware 用鉴权中间件包装一个简单 handler，执行请求并返回 recorder。
func applyMiddleware(t *testing.T, req *http.Request) *httptest.ResponseRecorder {
	t.Helper()
	cfg := defaultAuthConfig()
	middleware := NewAuthMiddleware(cfg, nil)

	var capturedCtx context.Context
	inner := func(w http.ResponseWriter, r *http.Request) {
		capturedCtx = r.Context()
		w.WriteHeader(http.StatusOK)
	}

	rec := httptest.NewRecorder()
	middleware(inner)(rec, req)
	_ = capturedCtx
	return rec
}

// applyMiddlewareCapturingCtx 与 applyMiddleware 类似，但额外返回 inner handler 收到的 context。
func applyMiddlewareCapturingCtx(t *testing.T, req *http.Request) (*httptest.ResponseRecorder, context.Context) {
	t.Helper()
	cfg := defaultAuthConfig()
	middleware := NewAuthMiddleware(cfg, nil)

	var capturedCtx context.Context
	inner := func(w http.ResponseWriter, r *http.Request) {
		capturedCtx = r.Context()
		w.WriteHeader(http.StatusOK)
	}

	rec := httptest.NewRecorder()
	middleware(inner)(rec, req)
	return rec, capturedCtx
}

// ---------- 强鉴权路径测试 ----------

func TestAuthMiddleware_NoToken_Returns401(t *testing.T) {
	req := httptest.NewRequest(http.MethodGet, "/api/user/profile", nil)
	rec := applyMiddleware(t, req)
	assert.Equal(t, http.StatusUnauthorized, rec.Code, "无 token 的强鉴权路径应返回 401")
}

func TestAuthMiddleware_InvalidToken_Returns401(t *testing.T) {
	req := httptest.NewRequest(http.MethodGet, "/api/user/profile", nil)
	req.Header.Set("Authorization", "Bearer this.is.not.valid")
	rec := applyMiddleware(t, req)
	assert.Equal(t, http.StatusUnauthorized, rec.Code, "无效 token 应返回 401")
}

func TestAuthMiddleware_ExpiredToken_Returns401(t *testing.T) {
	expired := buildToken(t, jwt.MapClaims{
		"userId": float64(1),
		"type":   "access",
		"exp":    time.Now().Add(-time.Hour).Unix(),
	})
	req := httptest.NewRequest(http.MethodGet, "/api/user/profile", nil)
	req.Header.Set("Authorization", "Bearer "+expired)
	rec := applyMiddleware(t, req)
	assert.Equal(t, http.StatusUnauthorized, rec.Code, "过期 token 应返回 401")
}

func TestAuthMiddleware_ValidToken_InjectsUserContext(t *testing.T) {
	token := buildToken(t, jwt.MapClaims{
		"userId":       float64(99),
		"role":         "user",
		"nickname":     "小红",
		"type":         "access",
		"tokenVersion": float64(1),
		"exp":          time.Now().Add(time.Hour).Unix(),
	})
	req := httptest.NewRequest(http.MethodGet, "/api/user/profile", nil)
	req.Header.Set("Authorization", "Bearer "+token)

	_, ctx := applyMiddlewareCapturingCtx(t, req)

	assert.Equal(t, int64(99), ctx.Value("userId"), "有效 token 应注入 userId")
	assert.Equal(t, "user", ctx.Value("role"), "有效 token 应注入 role")
	assert.Equal(t, "小红", ctx.Value("nickname"), "有效 token 应注入 nickname")
}

func TestAuthMiddleware_CookieToken_InjectsContext(t *testing.T) {
	// 验证 Cookie 模式兼容（accessToken Cookie）
	token := buildToken(t, jwt.MapClaims{
		"userId":       float64(7),
		"type":         "access",
		"tokenVersion": float64(1),
		"exp":          time.Now().Add(time.Hour).Unix(),
	})
	req := httptest.NewRequest(http.MethodGet, "/api/user/profile", nil)
	req.AddCookie(&http.Cookie{Name: "accessToken", Value: token})

	_, ctx := applyMiddlewareCapturingCtx(t, req)

	assert.Equal(t, int64(7), ctx.Value("userId"), "Cookie 模式应正确注入 userId")
}

// ---------- 公开路径测试 ----------

func TestAuthMiddleware_PublicPath_NoToken_Passes(t *testing.T) {
	// 公开路径不带 token 时应放行（匿名访问）
	req := httptest.NewRequest(http.MethodGet, "/api/post/list", nil)
	rec := applyMiddleware(t, req)
	assert.Equal(t, http.StatusOK, rec.Code, "公开路径无 token 应放行")
}

func TestAuthMiddleware_PublicPath_ValidToken_InjectsWeakContext(t *testing.T) {
	// 公开路径携带有效 token 时应进行弱鉴权并注入上下文
	token := buildToken(t, jwt.MapClaims{
		"userId":       float64(55),
		"type":         "access",
		"tokenVersion": float64(1),
		"exp":          time.Now().Add(time.Hour).Unix(),
	})
	req := httptest.NewRequest(http.MethodGet, "/api/post/list", nil)
	req.Header.Set("Authorization", "Bearer "+token)

	_, ctx := applyMiddlewareCapturingCtx(t, req)
	assert.Equal(t, int64(55), ctx.Value("userId"), "公开路径上有效 token 应注入弱登录态")
}

func TestAuthMiddleware_PublicPath_InvalidToken_PassesAnonymous(t *testing.T) {
	// 公开路径携带无效 token 时应忽略错误，以匿名身份放行
	req := httptest.NewRequest(http.MethodGet, "/api/post/search", nil)
	req.Header.Set("Authorization", "Bearer bad.token.here")
	_, ctx := applyMiddlewareCapturingCtx(t, req)
	assert.Nil(t, ctx.Value("userId"), "公开路径上无效 token 应以匿名放行，userId 应为 nil")
}

// ---------- token 类型校验 ----------

func TestAuthMiddleware_RefreshTokenType_Rejected(t *testing.T) {
	// type=refresh 的 token 不应被当作 access token 接受
	token := buildToken(t, jwt.MapClaims{
		"userId": float64(1),
		"type":   "refresh",
		"exp":    time.Now().Add(time.Hour).Unix(),
	})
	req := httptest.NewRequest(http.MethodGet, "/api/user/profile", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	rec := applyMiddleware(t, req)
	assert.Equal(t, http.StatusUnauthorized, rec.Code, "refresh token 不应通过强鉴权")
}
