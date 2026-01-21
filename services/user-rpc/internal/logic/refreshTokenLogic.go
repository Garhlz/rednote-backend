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

	tokens, err := buildTokenPair(l.svcCtx.Config, u)
	if err != nil {
		return nil, status.Error(codes.Internal, "create token failed")
	}

	return &user.AuthResponse{
		Tokens: tokens,
		User:   buildUserSummary(u),
	}, nil
}
