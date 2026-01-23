// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package user

import (
	"context"

	"gateway-api/internal/pkg/ctxutil"
	"gateway-api/internal/svc"
	"gateway-api/internal/types"

	"github.com/zeromicro/go-zero/core/logx"
	"user-rpc/userservice"
)

type BindEmailLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewBindEmailLogic(ctx context.Context, svcCtx *svc.ServiceContext) *BindEmailLogic {
	return &BindEmailLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *BindEmailLogic) BindEmail(req *types.BindEmailReq) (resp *types.Empty, err error) {
	client := userservice.NewUserService(l.svcCtx.UserRpc)
	_, err = client.BindEmail(l.ctx, &userservice.BindEmailRequest{
		UserId: ctxutil.UserID(l.ctx),
		Email:  req.Email,
		Code:   req.Code,
	})
	if err != nil {
		return nil, err
	}
	return &types.Empty{}, nil
}
