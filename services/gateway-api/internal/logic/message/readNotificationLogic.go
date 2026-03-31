// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package message

import (
	"context"

	"gateway-api/internal/pkg/ctxutil"
	"gateway-api/internal/svc"
	"gateway-api/internal/types"

	"github.com/zeromicro/go-zero/core/logx"
	"notification-rpc/notificationservice"
)

type ReadNotificationLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewReadNotificationLogic(ctx context.Context, svcCtx *svc.ServiceContext) *ReadNotificationLogic {
	return &ReadNotificationLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *ReadNotificationLogic) ReadNotification(req *types.Empty) (resp *types.Empty, err error) {
	client := notificationservice.NewNotificationService(l.svcCtx.NotificationRpc)
	_, err = client.MarkAllRead(l.ctx, &notificationservice.MarkAllReadRequest{
		UserId: ctxutil.UserID(l.ctx),
	})
	if err != nil {
		return nil, err
	}

	return &types.Empty{}, nil
}
