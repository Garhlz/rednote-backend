// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package admin

import (
	"context"

	"gateway-api/internal/svc"
	"gateway-api/internal/types"

	"github.com/zeromicro/go-zero/core/logx"
)

type AdminUserDetailLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewAdminUserDetailLogic(ctx context.Context, svcCtx *svc.ServiceContext) *AdminUserDetailLogic {
	return &AdminUserDetailLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *AdminUserDetailLogic) AdminUserDetail(req *types.IdPath) (resp *types.Empty, err error) {
	// todo: add your logic here and delete this line

	return
}
