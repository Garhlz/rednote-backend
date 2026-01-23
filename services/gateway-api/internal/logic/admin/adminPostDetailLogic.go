// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package admin

import (
	"context"

	"gateway-api/internal/svc"
	"gateway-api/internal/types"

	"github.com/zeromicro/go-zero/core/logx"
)

type AdminPostDetailLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewAdminPostDetailLogic(ctx context.Context, svcCtx *svc.ServiceContext) *AdminPostDetailLogic {
	return &AdminPostDetailLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *AdminPostDetailLogic) AdminPostDetail(req *types.IdPath) (resp *types.Empty, err error) {
	// todo: add your logic here and delete this line

	return
}
