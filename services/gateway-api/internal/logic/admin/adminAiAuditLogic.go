// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package admin

import (
	"context"

	"gateway-api/internal/svc"
	"gateway-api/internal/types"

	"github.com/zeromicro/go-zero/core/logx"
)

type AdminAiAuditLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewAdminAiAuditLogic(ctx context.Context, svcCtx *svc.ServiceContext) *AdminAiAuditLogic {
	return &AdminAiAuditLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *AdminAiAuditLogic) AdminAiAudit(req *types.PostIdPath) (resp *types.Empty, err error) {
	// todo: add your logic here and delete this line

	return
}
