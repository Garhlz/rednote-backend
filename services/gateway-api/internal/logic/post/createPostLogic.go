// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package post

import (
	"context"
	"encoding/json"
	"net/http"

	"gateway-api/internal/response"
	"gateway-api/internal/svc"
	"gateway-api/internal/types"

	"github.com/zeromicro/go-zero/core/logx"
)

type CreatePostLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewCreatePostLogic(ctx context.Context, svcCtx *svc.ServiceContext) *CreatePostLogic {
	return &CreatePostLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *CreatePostLogic) CreatePost(req *types.PostCreateReq) (resp *types.IdVO, err error) {
	body, err := json.Marshal(req)
	if err != nil {
		return nil, err
	}

	headers := buildJavaHeaders(l.ctx)
	headers["Content-Type"] = "application/json"

	javaResp, err := l.svcCtx.ProxyToJava(l.ctx, svc.JavaProxyRequest{
		Method:  http.MethodPost,
		Path:    "/api/post",
		Headers: headers,
		Body:    body,
	})
	if err != nil {
		return nil, err
	}

	var envelope javaEnvelope[types.IdVO]
	if err := json.Unmarshal(javaResp.Body, &envelope); err != nil {
		return nil, err
	}
	if javaResp.StatusCode != http.StatusOK || envelope.Code != 200 {
		return nil, response.NewError(envelope.Code, envelope.Message, javaResp.StatusCode)
	}

	return &envelope.Data, nil
}
