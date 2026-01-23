// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package auth

import (
	"context"

	"gateway-api/internal/svc"
	"gateway-api/internal/types"

	"github.com/zeromicro/go-zero/core/logx"
	"user-rpc/userservice"
)

type SendEmailCodeLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewSendEmailCodeLogic(ctx context.Context, svcCtx *svc.ServiceContext) *SendEmailCodeLogic {
	return &SendEmailCodeLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *SendEmailCodeLogic) SendEmailCode(req *types.AuthSendCodeReq) (resp *types.SendEmailCodeResp, err error) {
	client := userservice.NewUserService(l.svcCtx.UserRpc)
	result, err := client.SendEmailCode(l.ctx, &userservice.SendEmailCodeRequest{
		Email: req.Email,
	})
	if err != nil {
		return nil, err
	}
	return &types.SendEmailCodeResp{NextRetrySeconds: result.NextRetrySeconds}, nil
}
