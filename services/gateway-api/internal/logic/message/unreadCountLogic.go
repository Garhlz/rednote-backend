// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package message

import (
	"context"

	"gateway-api/internal/pkg/ctxutil"
	"gateway-api/internal/svc"

	"github.com/zeromicro/go-zero/core/logx"
	"notification-rpc/notificationservice"
)

type UnreadCountLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewUnreadCountLogic(ctx context.Context, svcCtx *svc.ServiceContext) *UnreadCountLogic {
	return &UnreadCountLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *UnreadCountLogic) UnreadCount() (resp any, err error) {
	client := notificationservice.NewNotificationService(l.svcCtx.NotificationRpc)
	result, err := client.GetUnreadCount(l.ctx, &notificationservice.GetUnreadCountRequest{
		UserId: ctxutil.UserID(l.ctx),
	})
	if err != nil {
		return nil, err
	}

	return result.GetUnreadCount(), nil
}
