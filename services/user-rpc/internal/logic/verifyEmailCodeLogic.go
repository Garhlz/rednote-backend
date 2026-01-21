package logic

import (
	"context"
	"strings"

	"user-rpc/internal/svc"
	"user-rpc/user"

	"github.com/zeromicro/go-zero/core/logx"
	"github.com/zeromicro/go-zero/core/stores/redis"
)

type VerifyEmailCodeLogic struct {
	ctx    context.Context
	svcCtx *svc.ServiceContext
	logx.Logger
}

func NewVerifyEmailCodeLogic(ctx context.Context, svcCtx *svc.ServiceContext) *VerifyEmailCodeLogic {
	return &VerifyEmailCodeLogic{
		ctx:    ctx,
		svcCtx: svcCtx,
		Logger: logx.WithContext(ctx),
	}
}

func (l *VerifyEmailCodeLogic) VerifyEmailCode(in *user.VerifyEmailCodeRequest) (*user.VerifyEmailCodeResponse, error) {
	email := strings.TrimSpace(in.GetEmail())
	code := strings.TrimSpace(in.GetCode())
	if email == "" || code == "" {
		return &user.VerifyEmailCodeResponse{Valid: false}, nil
	}

	val, err := l.svcCtx.Redis.GetCtx(l.ctx, emailCodeKeyPrefix+email)
	if err != nil {
		if err == redis.Nil {
			return &user.VerifyEmailCodeResponse{Valid: false}, nil
		}
		return &user.VerifyEmailCodeResponse{Valid: false}, nil
	}

	return &user.VerifyEmailCodeResponse{Valid: val == code}, nil
}
