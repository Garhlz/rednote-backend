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

type DeletePostLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewDeletePostLogic(ctx context.Context, svcCtx *svc.ServiceContext) *DeletePostLogic {
	return &DeletePostLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *DeletePostLogic) DeletePost(req *types.PostIdReq) (resp *types.Empty, err error) {
	javaResp, err := l.svcCtx.ProxyToJava(l.ctx, svc.JavaProxyRequest{
		Method:  http.MethodDelete,
		Path:    "/api/post/" + req.Id,
		Headers: buildJavaHeaders(l.ctx),
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
