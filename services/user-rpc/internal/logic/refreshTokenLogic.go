package logic

import (
	"context"
	"time"

	appmetrics "user-rpc/internal/metrics"
	"user-rpc/internal/model"
	"user-rpc/internal/svc"
	"user-rpc/user"

	"github.com/zeromicro/go-zero/core/logx"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

type RefreshTokenLogic struct {
	ctx    context.Context
	svcCtx *svc.ServiceContext
	logx.Logger
}

func NewRefreshTokenLogic(ctx context.Context, svcCtx *svc.ServiceContext) *RefreshTokenLogic {
	return &RefreshTokenLogic{
		ctx:    ctx,
		svcCtx: svcCtx,
		Logger: logx.WithContext(ctx),
	}
}

func (l *RefreshTokenLogic) RefreshToken(in *user.RefreshTokenRequest) (*user.AuthResponse, error) {
	start := time.Now()
	if in.GetRefreshToken() == "" {
		appmetrics.ObserveAuth("refresh", "invalid_argument", time.Since(start))
		return nil, status.Error(codes.InvalidArgument, "refresh token required")
	}

	claims, err := parseToken(l.svcCtx.Config, in.GetRefreshToken())
	if err != nil {
		appmetrics.ObserveAuth("refresh", "invalid_token", time.Since(start))
		return nil, status.Error(codes.Unauthenticated, "invalid refresh token")
	}
	if claims.Type != "refresh" {
		appmetrics.ObserveAuth("refresh", "invalid_type", time.Since(start))
		return nil, status.Error(codes.InvalidArgument, "invalid token type")
	}
	if claims.ID == "" {
		appmetrics.ObserveAuth("refresh", "missing_jti", time.Since(start))
		return nil, status.Error(codes.Unauthenticated, "invalid refresh token")
	}

	blocked, err := l.svcCtx.Redis.ExistsCtx(l.ctx, blockedTokenKey(in.GetRefreshToken()))
	if err != nil {
		appmetrics.ObserveAuth("refresh", "blocked_check_error", time.Since(start))
		return nil, status.Error(codes.Internal, "refresh token check failed")
	}
	if blocked {
		appmetrics.ObserveAuth("refresh", "blocked", time.Since(start))
		return nil, status.Error(codes.Unauthenticated, "refresh token revoked")
	}

	u, err := l.svcCtx.Users.FindOne(l.ctx, claims.UserId)
	if err == model.ErrNotFound {
		appmetrics.ObserveAuth("refresh", "user_not_found", time.Since(start))
		return nil, status.Error(codes.NotFound, "user not found")
	}
	if err != nil {
		appmetrics.ObserveAuth("refresh", "query_error", time.Since(start))
		return nil, status.Error(codes.Internal, "query user failed")
	}
	if u.Status != 1 {
		appmetrics.ObserveAuth("refresh", "account_disabled", time.Since(start))
		return nil, status.Error(codes.PermissionDenied, "account disabled")
	}
	if u.TokenVersion != claims.TokenVersion {
		appmetrics.ObserveAuth("refresh", "token_revoked", time.Since(start))
		return nil, status.Error(codes.Unauthenticated, "token revoked")
	}

	ok, err := l.svcCtx.Redis.ExistsCtx(l.ctx, refreshTokenKey(claims.UserId, claims.ID))
	if err != nil {
		appmetrics.ObserveAuth("refresh", "refresh_check_error", time.Since(start))
		return nil, status.Error(codes.Internal, "refresh token check failed")
	}
	if !ok {
		appmetrics.ObserveAuth("refresh", "refresh_revoked", time.Since(start))
		return nil, status.Error(codes.Unauthenticated, "refresh token revoked")
	}

	tokens, refreshJti, err := buildTokenPair(l.svcCtx.Config, u)
	if err != nil {
		appmetrics.ObserveAuth("refresh", "token_error", time.Since(start))
		return nil, status.Error(codes.Internal, "create token failed")
	}
	if err := l.svcCtx.Redis.Setex(
		refreshTokenKey(u.Id, refreshJti),
		"1",
		int(l.svcCtx.Config.Jwt.RefreshExpireSeconds),
	); err != nil {
		appmetrics.ObserveAuth("refresh", "store_refresh_error", time.Since(start))
		return nil, status.Error(codes.Internal, "store refresh token failed")
	}
	if err := setTokenVersion(l.ctx, l.svcCtx, u.Id, u.TokenVersion); err != nil {
		appmetrics.ObserveAuth("refresh", "store_version_error", time.Since(start))
		return nil, status.Error(codes.Internal, "store token version failed")
	}
	_, _ = l.svcCtx.Redis.DelCtx(l.ctx, refreshTokenKey(claims.UserId, claims.ID))
	appmetrics.ObserveAuth("refresh", "success", time.Since(start))

	return &user.AuthResponse{
		Tokens: tokens,
		User:   buildUserSummary(u),
	}, nil
}
