package logic

import (
	"context"
	"database/sql"
	"time"

	"user-rpc/internal/event"
	"user-rpc/internal/model"
	"user-rpc/internal/svc"
	"user-rpc/user"

	"github.com/zeromicro/go-zero/core/logx"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

type UpdateProfileLogic struct {
	ctx    context.Context
	svcCtx *svc.ServiceContext
	logx.Logger
}

func NewUpdateProfileLogic(ctx context.Context, svcCtx *svc.ServiceContext) *UpdateProfileLogic {
	return &UpdateProfileLogic{
		ctx:    ctx,
		svcCtx: svcCtx,
		Logger: logx.WithContext(ctx),
	}
}

func (l *UpdateProfileLogic) UpdateProfile(in *user.UpdateProfileRequest) (*user.UserProfile, error) {
	if in.GetUserId() <= 0 {
		return nil, status.Error(codes.InvalidArgument, "user_id required")
	}

	u, err := l.svcCtx.Users.FindOne(l.ctx, in.GetUserId())
	if err == model.ErrNotFound {
		return nil, status.Error(codes.NotFound, "user not found")
	}
	if err != nil {
		return nil, status.Error(codes.Internal, "query user failed")
	}

	changed := false
	needSync := false
	if in.Nickname != nil {
		u.Nickname = in.GetNickname()
		changed = true
		needSync = true
	}
	if in.Avatar != nil {
		u.Avatar = in.GetAvatar()
		changed = true
		needSync = true
	}
	if in.Bio != nil {
		u.Bio = in.GetBio()
		changed = true
	}
	if in.Gender != nil {
		u.Gender = int64(in.GetGender())
		changed = true
	}
	if in.Region != nil {
		u.Region = sql.NullString{String: in.GetRegion(), Valid: true}
		changed = true
	}
	if in.Birthday != nil {
		birthday, err := parseBirthday(in.GetBirthday())
		if err != nil {
			return nil, status.Error(codes.InvalidArgument, "invalid birthday")
		}
		u.Birthday = birthday
		changed = true
	}

	if changed {
		u.UpdatedAt = time.Now()
		if err := l.svcCtx.Users.Update(l.ctx, u); err != nil {
			return nil, status.Error(codes.Internal, "update user failed")
		}
	}

	if needSync && l.svcCtx.Publisher != nil {
		payload := event.UserUpdateEvent{
			UserId:      u.Id,
			NewNickname: u.Nickname,
			NewAvatar:   u.Avatar,
		}
		if err := l.svcCtx.Publisher.Publish(
			l.ctx,
			l.svcCtx.Config.RabbitMQ.UserUpdateRoutingKey,
			"com.szu.afternoon3.platform.event.UserUpdateEvent",
			payload,
		); err != nil {
			l.Logger.Errorf("publish user update event failed: %v", err)
		}
	}

	return buildUserProfile(u), nil
}
