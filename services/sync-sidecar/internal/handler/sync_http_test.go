package handler

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"sync-sidecar/internal/config"
	"sync-sidecar/internal/service"

	"github.com/stretchr/testify/assert"
)

func TestHandleReindexPosts_Validation(t *testing.T) {
	const correctToken = "test-token"

	// 构造最小化的 SyncHandler，只注入 Cfg，不依赖 Mongo/ES
	newHandler := func() *SyncHandler {
		return &SyncHandler{
			Infra: &service.Infra{
				Cfg: &config.AppConfig{AdminToken: correctToken},
			},
		}
	}

	tests := []struct {
		name           string
		method         string
		token          string
		reindexRunning bool
		wantCode       int
	}{
		{
			name:     "非 POST 方法返回 405",
			method:   http.MethodGet,
			token:    correctToken,
			wantCode: http.StatusMethodNotAllowed,
		},
		{
			name:     "Token 错误返回 403",
			method:   http.MethodPost,
			token:    "wrong-token",
			wantCode: http.StatusForbidden,
		},
		{
			name:           "已有任务运行时返回 409",
			method:         http.MethodPost,
			token:          correctToken,
			reindexRunning: true,
			wantCode:       http.StatusConflict,
		},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			h := newHandler()

			// 第 3 个用例：先占用 running 状态
			if tc.reindexRunning {
				ok := h.beginReindex()
				assert.True(t, ok, "beginReindex 应成功设置 running 状态")
			}

			req := httptest.NewRequest(tc.method, "/admin/reindex", nil)
			req.Header.Set("X-Admin-Token", tc.token)
			rec := httptest.NewRecorder()

			h.HandleReindexPosts(rec, req)

			assert.Equal(t, tc.wantCode, rec.Code)
		})
	}
}

func TestHandleReindexPosts_EmptyAdminToken_AllowedByCurrentLogic(t *testing.T) {
	// AdminToken 配置为空字符串时，不携带 X-Admin-Token 头的请求
	// 当前行为：空 header == 空配置，通过 Token 校验后进入 beginReindex()
	// 此测试锁定"空 Token 配置下空 header 会被放行"的当前行为，
	// 若未来希望拒绝此类请求，需在 handler 中补充 AdminToken != "" 的前置判断。
	h := &SyncHandler{
		Infra: &service.Infra{
			Cfg: &config.AppConfig{AdminToken: ""}, // 配置为空字符串
		},
	}
	// 预先占用 reindexRunning，让请求在 409 处提前返回，避免触发真正的 Mongo/ES 操作
	ok := h.beginReindex()
	assert.True(t, ok)

	req := httptest.NewRequest(http.MethodPost, "/reindex/posts", nil)
	// 不设置 X-Admin-Token 头（等价于空字符串）
	w := httptest.NewRecorder()
	h.HandleReindexPosts(w, req)

	// Token 校验通过（空==空），但 reindex 已在运行，返回 409
	assert.Equal(t, http.StatusConflict, w.Code,
		"AdminToken 为空时空 header 通过校验，但 reindex 已运行应返回 409")
}
