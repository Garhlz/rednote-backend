package logic

import (
	"context"
	"strings"
	"time"

	"user-rpc/internal/svc"
	"user-rpc/user"

	"github.com/zeromicro/go-zero/core/logx"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

type SendEmailCodeLogic struct {
	ctx    context.Context
	svcCtx *svc.ServiceContext
	logx.Logger
}

func NewSendEmailCodeLogic(ctx context.Context, svcCtx *svc.ServiceContext) *SendEmailCodeLogic {
	return &SendEmailCodeLogic{
		ctx:    ctx,
		svcCtx: svcCtx,
		Logger: logx.WithContext(ctx),
	}
}

// Auth / Account
func (l *SendEmailCodeLogic) SendEmailCode(in *user.SendEmailCodeRequest) (*user.SendEmailCodeResponse, error) {
	email := strings.TrimSpace(in.GetEmail())
	if email == "" {
		return nil, status.Error(codes.InvalidArgument, "email is required")
	}

	limitKey := emailLimitKeyPrefix + email
	allowed, err := l.svcCtx.Redis.SetnxEx(limitKey, "1", 60)
	if err != nil {
		return nil, status.Error(codes.Internal, "rate limit error")
	}
	if !allowed {
		return nil, status.Error(codes.ResourceExhausted, "operation too frequent")
	}

	code, err := randCode()
	if err != nil {
		return nil, status.Error(codes.Internal, "generate code failed")
	}

	subject := "【映记】验证码"
	body := "您的验证码是：" + code + "。有效期为5分钟，请勿泄露给他人。"
	if err := sendEmail(l.svcCtx.Config, email, subject, body); err != nil {
		l.Logger.Errorf("send email failed: %s", err)
		_, _ = l.svcCtx.Redis.Del(limitKey)
		return nil, status.Errorf(codes.Internal, "mail send failed: %s", err)
	}

	codeKey := emailCodeKeyPrefix + email
	if err := l.svcCtx.Redis.Setex(codeKey, code, int(time.Minute.Seconds()*5)); err != nil {
		return nil, status.Error(codes.Internal, "store code failed")
	}

	return &user.SendEmailCodeResponse{
		NextRetrySeconds: 60,
	}, nil
}
