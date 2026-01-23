// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package admin

import (
	"context"

	"gateway-api/internal/svc"
	"gateway-api/internal/types"

	"github.com/zeromicro/go-zero/core/logx"
)

type AdminResetPasswordLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewAdminResetPasswordLogic(ctx context.Context, svcCtx *svc.ServiceContext) *AdminResetPasswordLogic {
	return &AdminResetPasswordLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *AdminResetPasswordLogic) AdminResetPassword(req *types.Empty) (resp *types.Empty, err error) {
	// todo: add your logic here and delete this line

	return
}
