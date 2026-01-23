// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package user

import (
	"context"

	"gateway-api/internal/svc"
	"gateway-api/internal/types"

	"github.com/zeromicro/go-zero/core/logx"
)

type GetFollowsLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewGetFollowsLogic(ctx context.Context, svcCtx *svc.ServiceContext) *GetFollowsLogic {
	return &GetFollowsLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *GetFollowsLogic) GetFollows(req *types.UserIdPath) (resp *types.Empty, err error) {
	// todo: add your logic here and delete this line

	return
}
