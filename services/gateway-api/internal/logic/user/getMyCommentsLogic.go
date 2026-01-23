// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package user

import (
	"context"

	"gateway-api/internal/svc"
	"gateway-api/internal/types"

	"github.com/zeromicro/go-zero/core/logx"
)

type GetMyCommentsLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewGetMyCommentsLogic(ctx context.Context, svcCtx *svc.ServiceContext) *GetMyCommentsLogic {
	return &GetMyCommentsLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *GetMyCommentsLogic) GetMyComments() (resp *types.Empty, err error) {
	// todo: add your logic here and delete this line

	return
}
