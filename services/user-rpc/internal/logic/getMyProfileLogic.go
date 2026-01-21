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

type GetMyProfileLogic struct {
	ctx    context.Context
	svcCtx *svc.ServiceContext
	logx.Logger
}

func NewGetMyProfileLogic(ctx context.Context, svcCtx *svc.ServiceContext) *GetMyProfileLogic {
	return &GetMyProfileLogic{
		ctx:    ctx,
		svcCtx: svcCtx,
		Logger: logx.WithContext(ctx),
	}
}

// Profile
func (l *GetMyProfileLogic) GetMyProfile(in *user.GetMyProfileRequest) (*user.UserProfile, error) {
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

	return buildUserProfile(u), nil
}
