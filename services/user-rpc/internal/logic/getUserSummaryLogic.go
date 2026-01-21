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

type GetUserSummaryLogic struct {
	ctx    context.Context
	svcCtx *svc.ServiceContext
	logx.Logger
}

func NewGetUserSummaryLogic(ctx context.Context, svcCtx *svc.ServiceContext) *GetUserSummaryLogic {
	return &GetUserSummaryLogic{
		ctx:    ctx,
		svcCtx: svcCtx,
		Logger: logx.WithContext(ctx),
	}
}

// Cross-service
func (l *GetUserSummaryLogic) GetUserSummary(in *user.GetUserSummaryRequest) (*user.UserSummary, error) {
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

	return buildUserSummary(u), nil
}
