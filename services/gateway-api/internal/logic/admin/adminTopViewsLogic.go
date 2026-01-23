// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package admin

import (
	"context"

	"gateway-api/internal/svc"
	"gateway-api/internal/types"

	"github.com/zeromicro/go-zero/core/logx"
)

type AdminTopViewsLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewAdminTopViewsLogic(ctx context.Context, svcCtx *svc.ServiceContext) *AdminTopViewsLogic {
	return &AdminTopViewsLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *AdminTopViewsLogic) AdminTopViews() (resp *types.Empty, err error) {
	// todo: add your logic here and delete this line

	return
}
