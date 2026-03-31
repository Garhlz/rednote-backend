package logic

import (
	"context"
	"database/sql"
	"strings"
	"time"

	appmetrics "user-rpc/internal/metrics"
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
	start := time.Now()
	email := strings.TrimSpace(in.GetEmail())
	code := strings.TrimSpace(in.GetCode())
	password := in.GetPassword()
	nickname := strings.TrimSpace(in.GetNickname())
	if email == "" || code == "" || password == "" {
		appmetrics.ObserveAuth("register", "invalid_argument", time.Since(start))
		return nil, status.Error(codes.InvalidArgument, "email, code and password are required")
	}

	cached, err := l.svcCtx.Redis.GetCtx(l.ctx, emailCodeKey(emailSceneRegister, email))
	if err != nil && err != redis.Nil {
		appmetrics.ObserveAuth("register", "verify_code_error", time.Since(start))
		return nil, status.Error(codes.Internal, "verify code error")
	}
	if cached == "" || cached != code {
		appmetrics.ObserveAuth("register", "invalid_verify_code", time.Since(start))
		return nil, status.Error(codes.InvalidArgument, "invalid verify code")
	}

	exist, err := l.svcCtx.Users.FindOneByEmail(l.ctx, email)
	if err == nil && exist != nil {
		appmetrics.ObserveAuth("register", "email_exists", time.Since(start))
		return nil, status.Error(codes.AlreadyExists, "email already exists")
	}
	if err != nil && err != model.ErrNotFound {
		appmetrics.ObserveAuth("register", "query_error", time.Since(start))
		return nil, status.Error(codes.Internal, "query user failed")
	}

	if nickname == "" {
		nickname = "用户"
	}

	hashed, err := hashPassword(password)
	if err != nil {
		appmetrics.ObserveAuth("register", "hash_error", time.Since(start))
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

	id, err := l.svcCtx.Users.InsertAndReturnID(l.ctx, userData)
	if err != nil {
		appmetrics.ObserveAuth("register", "create_user_error", time.Since(start))
		return nil, status.Error(codes.Internal, "create user failed")
	}
	userData.Id = id

	tokens, refreshJti, err := buildTokenPair(l.svcCtx.Config, userData)
	if err != nil {
		appmetrics.ObserveAuth("register", "token_error", time.Since(start))
		return nil, status.Error(codes.Internal, "create token failed")
	}
	if err := l.svcCtx.Redis.Setex(
		refreshTokenKey(userData.Id, refreshJti),
		"1",
		int(l.svcCtx.Config.Jwt.RefreshExpireSeconds),
	); err != nil {
		appmetrics.ObserveAuth("register", "store_refresh_error", time.Since(start))
		return nil, status.Error(codes.Internal, "store refresh token failed")
	}
	if err := setTokenVersion(l.ctx, l.svcCtx, userData.Id, userData.TokenVersion); err != nil {
		appmetrics.ObserveAuth("register", "store_version_error", time.Since(start))
		return nil, status.Error(codes.Internal, "store token version failed")
	}

	_, _ = l.svcCtx.Redis.Del(emailCodeKey(emailSceneRegister, email))
	appmetrics.ObserveAuth("register", "success", time.Since(start))

	return &user.AuthResponse{
		Tokens: tokens,
		User:   buildUserSummary(userData),
	}, nil
}
