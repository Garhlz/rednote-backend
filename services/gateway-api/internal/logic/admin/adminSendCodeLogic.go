// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package admin

import (
	"context"

	"gateway-api/internal/svc"
	"gateway-api/internal/types"

	"github.com/zeromicro/go-zero/core/logx"
)

type AdminSendCodeLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewAdminSendCodeLogic(ctx context.Context, svcCtx *svc.ServiceContext) *AdminSendCodeLogic {
	return &AdminSendCodeLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *AdminSendCodeLogic) AdminSendCode(req *types.Empty) (resp *types.Empty, err error) {
	// todo: add your logic here and delete this line

	return
}
