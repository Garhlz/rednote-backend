// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package comment

import (
	"context"

	"gateway-api/internal/svc"
	"gateway-api/internal/types"

	"github.com/zeromicro/go-zero/core/logx"
)

type SubListCommentsLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewSubListCommentsLogic(ctx context.Context, svcCtx *svc.ServiceContext) *SubListCommentsLogic {
	return &SubListCommentsLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *SubListCommentsLogic) SubListComments() (resp *types.Empty, err error) {
	// todo: add your logic here and delete this line

	return
}
