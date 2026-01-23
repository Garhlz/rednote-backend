// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package user

import (
	"context"

	"gateway-api/internal/svc"
	"gateway-api/internal/types"

	"github.com/zeromicro/go-zero/core/logx"
)

type GetMyPostsLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewGetMyPostsLogic(ctx context.Context, svcCtx *svc.ServiceContext) *GetMyPostsLogic {
	return &GetMyPostsLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *GetMyPostsLogic) GetMyPosts() (resp *types.Empty, err error) {
	// todo: add your logic here and delete this line

	return
}
