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

type LikePostLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewLikePostLogic(ctx context.Context, svcCtx *svc.ServiceContext) *LikePostLogic {
	return &LikePostLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *LikePostLogic) LikePost(req *types.InteractionReq) (resp *types.Empty, err error) {
	client := interactionservice.NewInteractionService(l.svcCtx.InteractionRpc)
	_, err = client.LikePost(l.ctx, &interactionservice.InteractionRequest{
		UserId:   ctxutil.UserID(l.ctx),
		TargetId: req.TargetId,
	})
	if err != nil {
		return nil, err
	}
	return &types.Empty{}, nil
}
