// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package admin

import (
	"context"

	"gateway-api/internal/svc"
	"gateway-api/internal/types"

	"github.com/zeromicro/go-zero/core/logx"
)

type AdminProfilePasswordLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewAdminProfilePasswordLogic(ctx context.Context, svcCtx *svc.ServiceContext) *AdminProfilePasswordLogic {
	return &AdminProfilePasswordLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *AdminProfilePasswordLogic) AdminProfilePassword(req *types.AuthChangePwdReq) (resp *types.Empty, err error) {
	// todo: add your logic here and delete this line

	return
}
