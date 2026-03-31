package logic

import (
	"context"
	"strings"
	"time"

	appmetrics "user-rpc/internal/metrics"
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
	start := time.Now()
	email := strings.TrimSpace(in.GetEmail())
	password := in.GetPassword()
	if email == "" || password == "" {
		appmetrics.ObserveAuth("login", "invalid_argument", time.Since(start))
		return nil, status.Error(codes.InvalidArgument, "email and password are required")
	}

	u, err := l.svcCtx.Users.FindOneByEmail(l.ctx, email)
	if err == model.ErrNotFound {
		appmetrics.ObserveAuth("login", "user_not_found", time.Since(start))
		return nil, status.Error(codes.NotFound, "user not found")
	}
	if err != nil {
		appmetrics.ObserveAuth("login", "query_error", time.Since(start))
		return nil, status.Error(codes.Internal, "query user failed")
	}
	if u.Status != 1 {
		appmetrics.ObserveAuth("login", "account_disabled", time.Since(start))
		return nil, status.Error(codes.PermissionDenied, "account disabled")
	}
	if !checkPassword(u.Password, password) {
		appmetrics.ObserveAuth("login", "invalid_password", time.Since(start))
		return nil, status.Error(codes.Unauthenticated, "invalid password")
	}

	tokens, refreshJti, err := buildTokenPair(l.svcCtx.Config, u)
	if err != nil {
		appmetrics.ObserveAuth("login", "token_error", time.Since(start))
		return nil, status.Error(codes.Internal, "create token failed")
	}
	if err := l.svcCtx.Redis.Setex(
		refreshTokenKey(u.Id, refreshJti),
		"1",
		int(l.svcCtx.Config.Jwt.RefreshExpireSeconds),
	); err != nil {
		appmetrics.ObserveAuth("login", "store_refresh_error", time.Since(start))
		return nil, status.Error(codes.Internal, "store refresh token failed")
	}
	if err := setTokenVersion(l.ctx, l.svcCtx, u.Id, u.TokenVersion); err != nil {
		appmetrics.ObserveAuth("login", "store_version_error", time.Since(start))
		return nil, status.Error(codes.Internal, "store token version failed")
	}
	appmetrics.ObserveAuth("login", "success", time.Since(start))

	return &user.AuthResponse{
		Tokens: tokens,
		User:   buildUserSummary(u),
	}, nil
}
