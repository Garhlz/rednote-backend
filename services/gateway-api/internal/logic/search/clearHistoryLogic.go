// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package search

import (
	"context"

	"gateway-api/internal/pkg/ctxutil"
	"gateway-api/internal/svc"
	"gateway-api/internal/types"

	"github.com/zeromicro/go-zero/core/logx"
	"search-rpc/searchservice"
)

type ClearHistoryLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewClearHistoryLogic(ctx context.Context, svcCtx *svc.ServiceContext) *ClearHistoryLogic {
	return &ClearHistoryLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *ClearHistoryLogic) ClearHistory() (resp *types.Empty, err error) {
	client := searchservice.NewSearchService(l.svcCtx.SearchRpc)
	_, err = client.DeleteHistory(l.ctx, &searchservice.ClearHistoryRequest{
		UserId: ctxutil.UserID(l.ctx),
	})
	if err != nil {
		return nil, err
	}
	return &types.Empty{}, nil
}
