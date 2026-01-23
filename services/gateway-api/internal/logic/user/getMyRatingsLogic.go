// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package user

import (
	"context"

	"gateway-api/internal/svc"
	"gateway-api/internal/types"

	"github.com/zeromicro/go-zero/core/logx"
)

type GetMyRatingsLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewGetMyRatingsLogic(ctx context.Context, svcCtx *svc.ServiceContext) *GetMyRatingsLogic {
	return &GetMyRatingsLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *GetMyRatingsLogic) GetMyRatings() (resp *types.Empty, err error) {
	// todo: add your logic here and delete this line

	return
}
