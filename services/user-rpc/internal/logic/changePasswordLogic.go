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

type ChangePasswordLogic struct {
	ctx    context.Context
	svcCtx *svc.ServiceContext
	logx.Logger
}

func NewChangePasswordLogic(ctx context.Context, svcCtx *svc.ServiceContext) *ChangePasswordLogic {
	return &ChangePasswordLogic{
		ctx:    ctx,
		svcCtx: svcCtx,
		Logger: logx.WithContext(ctx),
	}
}

func (l *ChangePasswordLogic) ChangePassword(in *user.ChangePasswordRequest) (*user.Empty, error) {
	if in.GetUserId() <= 0 || in.GetOldPassword() == "" || in.GetNewPassword() == "" {
		return nil, status.Error(codes.InvalidArgument, "invalid params")
	}
	if len(in.GetNewPassword()) < 6 {
		return nil, status.Error(codes.InvalidArgument, "password too short")
	}

	u, err := l.svcCtx.Users.FindOne(l.ctx, in.GetUserId())
	if err == model.ErrNotFound {
		return nil, status.Error(codes.NotFound, "user not found")
	}
	if err != nil {
		return nil, status.Error(codes.Internal, "query user failed")
	}

	if !checkPassword(u.Password, in.GetOldPassword()) {
		return nil, status.Error(codes.Unauthenticated, "old password incorrect")
	}

	hashed, err := hashPassword(in.GetNewPassword())
	if err != nil {
		return nil, status.Error(codes.Internal, "hash password failed")
	}

	u.Password = hashed
	u.TokenVersion++
	u.PasswordChangedAt = time.Now()
	u.UpdatedAt = time.Now()
	if err := l.svcCtx.Users.Update(l.ctx, u); err != nil {
		return nil, status.Error(codes.Internal, "update user failed")
	}

	return &user.Empty{}, nil
}
