package logic

import (
	"context"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"

	"search-rpc/internal/svc"
	"search-rpc/search"

	elasticsearch "github.com/elastic/go-elasticsearch/v8"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// makeSearchSvcCtx 创建一个指向 fakeES 的 svc.ServiceContext，用于逻辑层测试。
func makeSearchSvcCtx(t *testing.T, esSrv *httptest.Server) *svc.ServiceContext {
	t.Helper()
	esClient, err := elasticsearch.NewClient(elasticsearch.Config{
		Addresses: []string{esSrv.URL},
		Transport: esSrv.Client().Transport,
	})
	require.NoError(t, err)
	return &svc.ServiceContext{Es: esClient}
}

// fakeESServer 启动一个带 Elasticsearch 产品检测头的假 ES 服务器。
// go-elasticsearch v8 会检查 X-Elastic-Product 头，不加则报 "not Elasticsearch"。
func startFakeES(t *testing.T, statusCode int, body string) *httptest.Server {
	t.Helper()
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("X-Elastic-Product", "Elasticsearch")
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(statusCode)
		if body != "" {
			_, _ = io.WriteString(w, body)
		}
	}))
	t.Cleanup(srv.Close)
	return srv
}

func TestSuggest_EmptyKeyword_ReturnsEmpty(t *testing.T) {
	// 空关键词应立即返回，不调用 ES
	called := false
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		called = true
		w.WriteHeader(http.StatusOK)
	}))
	t.Cleanup(srv.Close)

	logic := NewSuggestLogic(context.Background(), makeSearchSvcCtx(t, srv))
	resp, err := logic.Suggest(&search.SuggestRequest{Keyword: ""})

	require.NoError(t, err)
	assert.Empty(t, resp.GetSuggestions(), "空关键词应返回空建议")
	assert.False(t, called, "空关键词不应调用 ES")
}

func TestSuggest_ESError_ReturnsError(t *testing.T) {
	// ES 返回 5xx 错误时 Suggest 应透传错误
	// 注意：需要加 X-Elastic-Product 头才能通过 client 产品检测
	srv := startFakeES(t, http.StatusInternalServerError, `{"error":"simulated"}`)

	logic := NewSuggestLogic(context.Background(), makeSearchSvcCtx(t, srv))
	_, err := logic.Suggest(&search.SuggestRequest{Keyword: "test"})

	require.Error(t, err, "ES 返回 5xx 时应返回错误")
}

func TestSuggest_ESEmptyHits_ReturnsEmpty(t *testing.T) {
	// ES 命中为 0 时，即使有 keyword，也应返回空建议（原词不插入）
	srv := startFakeES(t, http.StatusOK, `{"hits":{"hits":[]}}`)

	logic := NewSuggestLogic(context.Background(), makeSearchSvcCtx(t, srv))
	resp, err := logic.Suggest(&search.SuggestRequest{Keyword: "go"})

	require.NoError(t, err)
	assert.Empty(t, resp.GetSuggestions(), "ES 无命中时原词不应被插入")
}

func TestSuggest_ESHitsWithHighlight_ReturnsOriginalFirst(t *testing.T) {
	// ES 有命中且有高亮时，结果顺序应为：原词 → 高亮片段
	hitResp := `{
		"hits": {
			"hits": [{
				"_source": {"title": "golang 教程", "tags": ["golang"]},
				"highlight": {"title": ["<em>go</em>lang 教程"]}
			}]
		}
	}`
	srv := startFakeES(t, http.StatusOK, hitResp)

	logic := NewSuggestLogic(context.Background(), makeSearchSvcCtx(t, srv))
	resp, err := logic.Suggest(&search.SuggestRequest{Keyword: "go"})

	require.NoError(t, err)
	suggestions := resp.GetSuggestions()
	require.NotEmpty(t, suggestions, "有命中时应返回建议词")
	assert.Equal(t, "go", suggestions[0], "原词应排在最前")
	assert.Contains(t, suggestions, "<em>go</em>lang 教程", "高亮片段应包含在结果中")
}

func TestSuggest_DecodeError_ReturnsError(t *testing.T) {
	// ES 返回非法 JSON 时应返回 decode 错误
	srv := startFakeES(t, http.StatusOK, `not valid json`)

	logic := NewSuggestLogic(context.Background(), makeSearchSvcCtx(t, srv))
	_, err := logic.Suggest(&search.SuggestRequest{Keyword: "go"})

	require.Error(t, err, "ES 返回非法 JSON 时应返回解码错误")
}

func TestSuggest_OnlyOriginalWord_ClearedToEmpty(t *testing.T) {
	// 有命中但高亮和 fallback 都不匹配关键词时，仅剩原词 → 结果应清空
	hitResp := `{
		"hits": {
			"hits": [{
				"_source": {"title": "完全不相关标题", "tags": ["其他标签"]},
				"highlight": {}
			}]
		}
	}`
	srv := startFakeES(t, http.StatusOK, hitResp)

	logic := NewSuggestLogic(context.Background(), makeSearchSvcCtx(t, srv))
	resp, err := logic.Suggest(&search.SuggestRequest{Keyword: "xyz"})

	require.NoError(t, err)
	assert.Empty(t, resp.GetSuggestions(), "仅有原词时结果应清空，避免假联想")
}
