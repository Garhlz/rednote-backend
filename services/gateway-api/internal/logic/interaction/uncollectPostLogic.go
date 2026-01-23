// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package interaction

import (
	"context"

	"gateway-api/internal/pkg/ctxutil"
	"gateway-api/internal/svc"
	"gateway-api/internal/types"

	"github.com/zeromicro/go-zero/core/logx"
	"interaction-rpc/interactionservice"
)

type UncollectPostLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewUncollectPostLogic(ctx context.Context, svcCtx *svc.ServiceContext) *UncollectPostLogic {
	return &UncollectPostLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *UncollectPostLogic) UncollectPost(req *types.InteractionReq) (resp *types.Empty, err error) {
	client := interactionservice.NewInteractionService(l.svcCtx.InteractionRpc)
	_, err = client.UncollectPost(l.ctx, &interactionservice.InteractionRequest{
		UserId:   ctxutil.UserID(l.ctx),
		TargetId: req.TargetId,
	})
	if err != nil {
		return nil, err
	}
	return &types.Empty{}, nil
}
