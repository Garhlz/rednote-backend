package logic

import (
	"context"
	"database/sql"
	"strings"
	"time"

	"user-rpc/internal/model"
	"user-rpc/internal/svc"
	"user-rpc/user"

	"github.com/zeromicro/go-zero/core/logx"
	"github.com/zeromicro/go-zero/core/stores/redis"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

type RegisterLogic struct {
	ctx    context.Context
	svcCtx *svc.ServiceContext
	logx.Logger
}

func NewRegisterLogic(ctx context.Context, svcCtx *svc.ServiceContext) *RegisterLogic {
	return &RegisterLogic{
		ctx:    ctx,
		svcCtx: svcCtx,
		Logger: logx.WithContext(ctx),
	}
}

func (l *RegisterLogic) Register(in *user.RegisterRequest) (*user.AuthResponse, error) {
	email := strings.TrimSpace(in.GetEmail())
	code := strings.TrimSpace(in.GetCode())
	password := in.GetPassword()
	nickname := strings.TrimSpace(in.GetNickname())
	if email == "" || code == "" || password == "" {
		return nil, status.Error(codes.InvalidArgument, "email, code and password are required")
	}

	cached, err := l.svcCtx.Redis.GetCtx(l.ctx, emailCodeKeyPrefix+email)
	if err != nil && err != redis.Nil {
		return nil, status.Error(codes.Internal, "verify code error")
	}
	if cached == "" || cached != code {
		return nil, status.Error(codes.InvalidArgument, "invalid verify code")
	}

	exist, err := l.svcCtx.Users.FindOneByEmail(l.ctx, email)
	if err == nil && exist != nil {
		return nil, status.Error(codes.AlreadyExists, "email already exists")
	}
	if err != nil && err != model.ErrNotFound {
		return nil, status.Error(codes.Internal, "query user failed")
	}

	if nickname == "" {
		nickname = "用户"
	}

	hashed, err := hashPassword(password)
	if err != nil {
		return nil, status.Error(codes.Internal, "hash password failed")
	}

	now := time.Now()
	userData := &model.Users{
		Email:             email,
		Password:          hashed,
		Nickname:          nickname,
		Avatar:            l.svcCtx.Config.User.DefaultAvatar,
		Gender:            0,
		Birthday:          sql.NullTime{Valid: false},
		Region:            sql.NullString{Valid: false},
		Bio:               "",
		Role:              "USER",
		Status:            1,
		IsDeleted:         0,
		TokenVersion:      0,
		PasswordChangedAt: now,
		UpdatedAt:         now,
		CreatedAt:         now,
	}

	result, err := l.svcCtx.Users.Insert(l.ctx, userData)
	if err != nil {
		return nil, status.Error(codes.Internal, "create user failed")
	}
	if id, err := result.LastInsertId(); err == nil {
		userData.Id = id
	}

	tokens, err := buildTokenPair(l.svcCtx.Config, userData)
	if err != nil {
		return nil, status.Error(codes.Internal, "create token failed")
	}

	_, _ = l.svcCtx.Redis.Del(emailCodeKeyPrefix + email)

	return &user.AuthResponse{
		Tokens:      tokens,
		User:        buildUserSummary(userData),
		IsNewUser:   true,
		HasPassword: true,
	}, nil
}
