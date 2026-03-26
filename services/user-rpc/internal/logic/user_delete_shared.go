package logic

import (
	"context"
	"fmt"
	"time"

	"user-rpc/internal/event"
	"user-rpc/internal/svc"
)

const deletedUserAvatar = "https://afternoon3-rednote.oss-cn-shenzhen.aliyuncs.com/deleted_user.png"

func deleteUserAccount(ctx context.Context, svcCtx *svc.ServiceContext, userId int64, reason string) error {
	u, err := svcCtx.Users.FindOne(ctx, userId)
	if err != nil {
		return err
	}

	suffix := fmt.Sprintf("_del_%d", time.Now().Unix())
	if u.Email != "" {
		newEmail := u.Email + suffix
		if len(newEmail) > 120 {
			newEmail = newEmail[:100] + suffix
		}
		u.Email = newEmail
	}

	u.Nickname = "用户已注销"
	u.Avatar = deletedUserAvatar
	u.Status = 0
	u.IsDeleted = 1
	u.TokenVersion++
	u.UpdatedAt = time.Now()

	if err := svcCtx.Users.Update(ctx, u); err != nil {
		return err
	}
	if err := setTokenVersion(ctx, svcCtx, u.Id, u.TokenVersion); err != nil {
		return err
	}

	if svcCtx.Publisher != nil {
		_ = svcCtx.Publisher.Publish(
			ctx,
			svcCtx.Config.RabbitMQ.UserDeleteRoutingKey,
			"com.szu.afternoon3.platform.event.UserDeleteEvent",
			event.UserDeleteEvent{UserId: u.Id},
		)
	}

	return nil
}
