package logic

import (
	"context"
	"strings"

	"user-rpc/internal/model"
	"user-rpc/internal/svc"
	"user-rpc/user"

	"github.com/zeromicro/go-zero/core/logx"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

type LoginLogic struct {
	ctx    context.Context
	svcCtx *svc.ServiceContext
	logx.Logger
}

func NewLoginLogic(ctx context.Context, svcCtx *svc.ServiceContext) *LoginLogic {
	return &LoginLogic{
		ctx:    ctx,
		svcCtx: svcCtx,
		Logger: logx.WithContext(ctx),
	}
}

func (l *LoginLogic) Login(in *user.LoginRequest) (*user.AuthResponse, error) {
	email := strings.TrimSpace(in.GetEmail())
	password := in.GetPassword()
	if email == "" || password == "" {
		return nil, status.Error(codes.InvalidArgument, "email and password are required")
	}

	u, err := l.svcCtx.Users.FindOneByEmail(l.ctx, email)
	if err == model.ErrNotFound {
		return nil, status.Error(codes.NotFound, "user not found")
	}
	if err != nil {
		return nil, status.Error(codes.Internal, "query user failed")
	}
	if u.Status != 1 {
		return nil, status.Error(codes.PermissionDenied, "account disabled")
	}
	if !checkPassword(u.Password, password) {
		return nil, status.Error(codes.Unauthenticated, "invalid password")
	}

	tokens, err := buildTokenPair(l.svcCtx.Config, u)
	if err != nil {
		return nil, status.Error(codes.Internal, "create token failed")
	}

	return &user.AuthResponse{
		Tokens:      tokens,
		User:        buildUserSummary(u),
		IsNewUser:   false,
		HasPassword: u.Password != "",
	}, nil
}
