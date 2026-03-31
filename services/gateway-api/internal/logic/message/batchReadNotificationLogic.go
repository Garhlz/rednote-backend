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

type BatchReadNotificationLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewBatchReadNotificationLogic(ctx context.Context, svcCtx *svc.ServiceContext) *BatchReadNotificationLogic {
	return &BatchReadNotificationLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *BatchReadNotificationLogic) BatchReadNotification(req *types.NotificationBatchReadReq) (resp *types.Empty, err error) {
	client := notificationservice.NewNotificationService(l.svcCtx.NotificationRpc)
	_, err = client.MarkBatchRead(l.ctx, &notificationservice.MarkBatchReadRequest{
		UserId: ctxutil.UserID(l.ctx),
		Ids:    req.Ids,
	})
	if err != nil {
		return nil, err
	}

	return &types.Empty{}, nil
}
