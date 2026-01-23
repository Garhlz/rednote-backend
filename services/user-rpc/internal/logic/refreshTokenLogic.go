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
	if in.GetRefreshToken() == "" {
		return nil, status.Error(codes.InvalidArgument, "refresh token required")
	}

	claims, err := parseToken(l.svcCtx.Config, in.GetRefreshToken())
	if err != nil {
		return nil, status.Error(codes.Unauthenticated, "invalid refresh token")
	}
	if claims.Type != "refresh" {
		return nil, status.Error(codes.InvalidArgument, "invalid token type")
	}
	if claims.ID == "" {
		return nil, status.Error(codes.Unauthenticated, "invalid refresh token")
	}

	blocked, err := l.svcCtx.Redis.ExistsCtx(l.ctx, tokenBlockPrefix+in.GetRefreshToken())
	if err != nil {
		return nil, status.Error(codes.Internal, "refresh token check failed")
	}
	if blocked {
		return nil, status.Error(codes.Unauthenticated, "refresh token revoked")
	}

	u, err := l.svcCtx.Users.FindOne(l.ctx, claims.UserId)
	if err == model.ErrNotFound {
		return nil, status.Error(codes.NotFound, "user not found")
	}
	if err != nil {
		return nil, status.Error(codes.Internal, "query user failed")
	}
	if u.Status != 1 {
		return nil, status.Error(codes.PermissionDenied, "account disabled")
	}
	if u.TokenVersion != claims.TokenVersion {
		return nil, status.Error(codes.Unauthenticated, "token revoked")
	}

	ok, err := l.svcCtx.Redis.ExistsCtx(l.ctx, refreshTokenKey(claims.UserId, claims.ID))
	if err != nil {
		return nil, status.Error(codes.Internal, "refresh token check failed")
	}
	if !ok {
		return nil, status.Error(codes.Unauthenticated, "refresh token revoked")
	}

	tokens, refreshJti, err := buildTokenPair(l.svcCtx.Config, u)
	if err != nil {
		return nil, status.Error(codes.Internal, "create token failed")
	}
	if err := l.svcCtx.Redis.Setex(
		refreshTokenKey(u.Id, refreshJti),
		"1",
		int(l.svcCtx.Config.Jwt.RefreshExpireSeconds),
	); err != nil {
		return nil, status.Error(codes.Internal, "store refresh token failed")
	}
	if err := setTokenVersion(l.ctx, l.svcCtx, u.Id, u.TokenVersion); err != nil {
		return nil, status.Error(codes.Internal, "store token version failed")
	}
	_, _ = l.svcCtx.Redis.DelCtx(l.ctx, refreshTokenKey(claims.UserId, claims.ID))

	return &user.AuthResponse{
		Tokens: tokens,
		User:   buildUserSummary(u),
	}, nil
}
