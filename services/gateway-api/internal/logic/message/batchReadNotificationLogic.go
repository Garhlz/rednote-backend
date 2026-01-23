// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package message

import (
	"context"

	"gateway-api/internal/svc"
	"gateway-api/internal/types"

	"github.com/zeromicro/go-zero/core/logx"
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

func (l *BatchReadNotificationLogic) BatchReadNotification(req *types.Empty) (resp *types.Empty, err error) {
	// todo: add your logic here and delete this line

	return
}
