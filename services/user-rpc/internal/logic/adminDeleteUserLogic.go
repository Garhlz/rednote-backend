package logic

import (
	"context"

	"user-rpc/internal/model"
	"user-rpc/internal/svc"
	"user-rpc/user"

	"github.com/zeromicro/go-zero/core/logx"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

type AdminDeleteUserLogic struct {
	ctx    context.Context
	svcCtx *svc.ServiceContext
	logx.Logger
}

func NewAdminDeleteUserLogic(ctx context.Context, svcCtx *svc.ServiceContext) *AdminDeleteUserLogic {
	return &AdminDeleteUserLogic{
		ctx:    ctx,
		svcCtx: svcCtx,
		Logger: logx.WithContext(ctx),
	}
}

func (l *AdminDeleteUserLogic) AdminDeleteUser(in *user.AdminDeleteUserRequest) (*user.Empty, error) {
	if in.GetUserId() <= 0 {
		return nil, status.Error(codes.InvalidArgument, "user_id required")
	}

	if err := deleteUserAccount(l.ctx, l.svcCtx, in.GetUserId(), in.GetReason()); err != nil {
		if err == model.ErrNotFound {
			return nil, status.Error(codes.NotFound, "user not found")
		}
		return nil, status.Error(codes.Internal, "delete user failed")
	}

	return &user.Empty{}, nil
}
