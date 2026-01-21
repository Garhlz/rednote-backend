package logic

import (
	"context"

	"user-rpc/internal/svc"
	"user-rpc/user"

	"github.com/zeromicro/go-zero/core/logx"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

type BatchGetUsersLogic struct {
	ctx    context.Context
	svcCtx *svc.ServiceContext
	logx.Logger
}

func NewBatchGetUsersLogic(ctx context.Context, svcCtx *svc.ServiceContext) *BatchGetUsersLogic {
	return &BatchGetUsersLogic{
		ctx:    ctx,
		svcCtx: svcCtx,
		Logger: logx.WithContext(ctx),
	}
}

func (l *BatchGetUsersLogic) BatchGetUsers(in *user.BatchGetUsersRequest) (*user.BatchGetUsersResponse, error) {
	if len(in.GetUserIds()) == 0 {
		return &user.BatchGetUsersResponse{UserMap: map[int64]*user.UserSummary{}}, nil
	}

	users, err := l.svcCtx.Users.FindByIds(l.ctx, in.GetUserIds())
	if err != nil {
		return nil, status.Error(codes.Internal, "query users failed")
	}

	resp := &user.BatchGetUsersResponse{
		UserMap: make(map[int64]*user.UserSummary, len(users)),
	}
	for _, u := range users {
		resp.UserMap[u.Id] = buildUserSummary(u)
	}

	return resp, nil
}
