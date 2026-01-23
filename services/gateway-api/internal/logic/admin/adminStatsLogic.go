// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package admin

import (
	"context"

	"gateway-api/internal/svc"
	"gateway-api/internal/types"

	"github.com/zeromicro/go-zero/core/logx"
)

type AdminStatsLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewAdminStatsLogic(ctx context.Context, svcCtx *svc.ServiceContext) *AdminStatsLogic {
	return &AdminStatsLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *AdminStatsLogic) AdminStats() (resp *types.Empty, err error) {
	// todo: add your logic here and delete this line

	return
}
