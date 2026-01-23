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

type RatePostLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewRatePostLogic(ctx context.Context, svcCtx *svc.ServiceContext) *RatePostLogic {
	return &RatePostLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *RatePostLogic) RatePost(req *types.RatePostReq) (resp *types.Empty, err error) {
	client := interactionservice.NewInteractionService(l.svcCtx.InteractionRpc)
	_, err = client.RatePost(l.ctx, &interactionservice.RateRequest{
		UserId:   ctxutil.UserID(l.ctx),
		TargetId: req.TargetId,
		Score:    req.Score,
	})
	if err != nil {
		return nil, err
	}
	return &types.Empty{}, nil
}
