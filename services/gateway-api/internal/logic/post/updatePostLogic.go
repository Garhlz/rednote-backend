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

type UpdatePostLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewUpdatePostLogic(ctx context.Context, svcCtx *svc.ServiceContext) *UpdatePostLogic {
	return &UpdatePostLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *UpdatePostLogic) UpdatePost(req *types.PostUpdateReq) (resp *types.Empty, err error) {
	body, err := json.Marshal(map[string]any{
		"title":       req.Title,
		"content":     req.Content,
		"images":      req.Images,
		"video":       req.Video,
		"tags":        req.Tags,
		"coverWidth":  req.CoverWidth,
		"coverHeight": req.CoverHeight,
	})
	if err != nil {
		return nil, err
	}

	headers := buildJavaHeaders(l.ctx)
	headers["Content-Type"] = "application/json"

	javaResp, err := l.svcCtx.ProxyToJava(l.ctx, svc.JavaProxyRequest{
		Method:  http.MethodPut,
		Path:    "/api/post/" + req.Id,
		Headers: headers,
		Body:    body,
	})
	if err != nil {
		return nil, err
	}

	var envelope javaEnvelope[struct{}]
	if err := json.Unmarshal(javaResp.Body, &envelope); err != nil {
		return nil, err
	}
	if javaResp.StatusCode != http.StatusOK || envelope.Code != 200 {
		return nil, response.NewError(envelope.Code, envelope.Message, javaResp.StatusCode)
	}

	return &types.Empty{}, nil
}
