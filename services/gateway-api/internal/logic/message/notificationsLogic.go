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

type NotificationsLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewNotificationsLogic(ctx context.Context, svcCtx *svc.ServiceContext) *NotificationsLogic {
	return &NotificationsLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *NotificationsLogic) Notifications(req *types.NotificationListReq) (resp *types.NotificationPageResult, err error) {
	client := notificationservice.NewNotificationService(l.svcCtx.NotificationRpc)
	result, err := client.ListNotifications(l.ctx, &notificationservice.ListNotificationsRequest{
		UserId:   ctxutil.UserID(l.ctx),
		Page:     req.Page,
		PageSize: req.Size,
	})
	if err != nil {
		return nil, err
	}

	records := make([]types.NotificationVO, 0, len(result.GetItems()))
	for _, item := range result.GetItems() {
		records = append(records, types.NotificationVO{
			Id:             item.GetId(),
			ReceiverId:     item.GetReceiverId(),
			SenderId:       item.GetSenderId(),
			SenderNickname: item.GetSenderNickname(),
			SenderAvatar:   item.GetSenderAvatar(),
			Type:           item.GetType().String(),
			TargetId:       item.GetTargetId(),
			TargetPreview:  item.GetTargetPreview(),
			IsRead:         item.GetIsRead(),
			CreatedAt:      item.GetCreatedAt(),
		})
	}

	return &types.NotificationPageResult{
		Records: records,
		Total:   result.GetTotal(),
		Current: result.GetPage(),
		Size:    result.GetPageSize(),
	}, nil
}
