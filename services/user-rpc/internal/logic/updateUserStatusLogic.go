package logic

import (
	"context"
	"time"

	"user-rpc/internal/model"
	"user-rpc/internal/svc"
	"user-rpc/user"

	"github.com/zeromicro/go-zero/core/logx"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

type UpdateUserStatusLogic struct {
	ctx    context.Context
	svcCtx *svc.ServiceContext
	logx.Logger
}

func NewUpdateUserStatusLogic(ctx context.Context, svcCtx *svc.ServiceContext) *UpdateUserStatusLogic {
	return &UpdateUserStatusLogic{
		ctx:    ctx,
		svcCtx: svcCtx,
		Logger: logx.WithContext(ctx),
	}
}

func (l *UpdateUserStatusLogic) UpdateUserStatus(in *user.UpdateUserStatusRequest) (*user.Empty, error) {
	if in.GetUserId() <= 0 {
		return nil, status.Error(codes.InvalidArgument, "user_id required")
	}

	u, err := l.svcCtx.Users.FindOne(l.ctx, in.GetUserId())
	if err == model.ErrNotFound {
		return nil, status.Error(codes.NotFound, "user not found")
	}
	if err != nil {
		return nil, status.Error(codes.Internal, "query user failed")
	}

	u.Status = int64(in.GetStatus())
	u.UpdatedAt = time.Now()
	if err := l.svcCtx.Users.Update(l.ctx, u); err != nil {
		return nil, status.Error(codes.Internal, "update user failed")
	}

	return &user.Empty{}, nil
}
