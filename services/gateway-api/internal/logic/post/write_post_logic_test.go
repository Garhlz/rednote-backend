package post

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"net/http"
	"testing"

	"gateway-api/internal/svc"
	"gateway-api/internal/types"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

type roundTripFunc func(*http.Request) (*http.Response, error)

func (f roundTripFunc) RoundTrip(req *http.Request) (*http.Response, error) {
	return f(req)
}

func newTestPostSvcCtx(fn roundTripFunc) *svc.ServiceContext {
	return &svc.ServiceContext{
		JavaApiBaseUrl: "http://java.test",
		JavaHttpClient: &http.Client{Transport: fn},
	}
}

func jsonResponse(status int, body string) *http.Response {
	return &http.Response{
		StatusCode: status,
		Header:     http.Header{"Content-Type": []string{"application/json"}},
		Body:       io.NopCloser(bytes.NewBufferString(body)),
	}
}

func TestCreatePost_ProxyToJava(t *testing.T) {
	var (
		gotMethod      string
		gotURL         string
		gotContentType string
		gotAuth        string
		gotRequestID   string
		gotTraceID     string
		gotBody        map[string]any
	)

	svcCtx := newTestPostSvcCtx(func(r *http.Request) (*http.Response, error) {
		gotMethod = r.Method
		gotURL = r.URL.String()
		gotContentType = r.Header.Get("Content-Type")
		gotAuth = r.Header.Get("Authorization")
		gotRequestID = r.Header.Get("X-Request-Id")
		gotTraceID = r.Header.Get("X-Trace-Id")
		require.NoError(t, json.NewDecoder(r.Body).Decode(&gotBody))
		return jsonResponse(http.StatusOK, `{"code":200,"message":"操作成功","data":{"id":"post123"}}`), nil
	})

	ctx := context.WithValue(context.Background(), "accessToken", "jwt-token")
	ctx = context.WithValue(ctx, "requestId", "req-1")
	ctx = context.WithValue(ctx, "traceId", "trace-1")

	logic := NewCreatePostLogic(ctx, svcCtx)
	resp, err := logic.CreatePost(&types.PostCreateReq{
		Title:       "测试标题",
		Content:     "测试内容",
		Type:        2,
		Images:      []string{"https://example.com/a.png"},
		Tags:        []string{"go", "test"},
		CoverWidth:  1080,
		CoverHeight: 1350,
	})

	require.NoError(t, err)
	require.NotNil(t, resp)
	assert.Equal(t, "post123", resp.Id)
	assert.Equal(t, http.MethodPost, gotMethod)
	assert.Equal(t, "http://java.test/api/post", gotURL)
	assert.Equal(t, "application/json", gotContentType)
	assert.Equal(t, "Bearer jwt-token", gotAuth)
	assert.Equal(t, "req-1", gotRequestID)
	assert.Equal(t, "trace-1", gotTraceID)
	assert.Equal(t, "测试标题", gotBody["title"])
	assert.Equal(t, "测试内容", gotBody["content"])
	assert.EqualValues(t, 2, gotBody["type"])
	assert.ElementsMatch(t, []any{"go", "test"}, gotBody["tags"].([]any))
}

func TestUpdatePost_ProxyToJava(t *testing.T) {
	var (
		gotMethod string
		gotURL    string
		gotBody   map[string]any
	)

	svcCtx := newTestPostSvcCtx(func(r *http.Request) (*http.Response, error) {
		gotMethod = r.Method
		gotURL = r.URL.String()
		require.Equal(t, "application/json", r.Header.Get("Content-Type"))
		require.NoError(t, json.NewDecoder(r.Body).Decode(&gotBody))
		return jsonResponse(http.StatusOK, `{"code":200,"message":"操作成功","data":null}`), nil
	})

	logic := NewUpdatePostLogic(context.Background(), svcCtx)
	resp, err := logic.UpdatePost(&types.PostUpdateReq{
		Id:          "abc123",
		Title:       "新标题",
		Content:     "新内容",
		Images:      []string{"https://example.com/b.png"},
		Tags:        []string{"updated"},
		CoverWidth:  720,
		CoverHeight: 960,
	})

	require.NoError(t, err)
	require.NotNil(t, resp)
	assert.Equal(t, http.MethodPut, gotMethod)
	assert.Equal(t, "http://java.test/api/post/abc123", gotURL)
	assert.Equal(t, "新标题", gotBody["title"])
	assert.Equal(t, "新内容", gotBody["content"])
	assert.EqualValues(t, 720, gotBody["coverWidth"])
	assert.EqualValues(t, 960, gotBody["coverHeight"])
}

func TestDeletePost_ProxyToJava(t *testing.T) {
	var (
		gotMethod string
		gotURL    string
	)

	svcCtx := newTestPostSvcCtx(func(r *http.Request) (*http.Response, error) {
		gotMethod = r.Method
		gotURL = r.URL.String()
		return jsonResponse(http.StatusOK, `{"code":200,"message":"操作成功","data":null}`), nil
	})

	logic := NewDeletePostLogic(context.Background(), svcCtx)
	resp, err := logic.DeletePost(&types.PostIdReq{Id: "deadbeef"})

	require.NoError(t, err)
	require.NotNil(t, resp)
	assert.Equal(t, http.MethodDelete, gotMethod)
	assert.Equal(t, "http://java.test/api/post/deadbeef", gotURL)
}

func TestWritePostLogic_UpstreamBusinessError(t *testing.T) {
	svcCtx := newTestPostSvcCtx(func(r *http.Request) (*http.Response, error) {
		return jsonResponse(http.StatusBadRequest, `{"code":40001,"message":"标题不能为空","data":null}`), nil
	})

	logic := NewCreatePostLogic(context.Background(), svcCtx)
	resp, err := logic.CreatePost(&types.PostCreateReq{})

	require.Nil(t, resp)
	require.Error(t, err)
	assert.Contains(t, err.Error(), "标题不能为空")
}
