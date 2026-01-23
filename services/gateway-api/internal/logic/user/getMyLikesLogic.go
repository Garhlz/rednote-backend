// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package user

import (
	"context"

	"gateway-api/internal/svc"
	"gateway-api/internal/types"

	"github.com/zeromicro/go-zero/core/logx"
)

type GetMyLikesLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewGetMyLikesLogic(ctx context.Context, svcCtx *svc.ServiceContext) *GetMyLikesLogic {
	return &GetMyLikesLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *GetMyLikesLogic) GetMyLikes() (resp *types.Empty, err error) {
	// todo: add your logic here and delete this line

	return
}
