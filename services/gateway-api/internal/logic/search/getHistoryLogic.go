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

type GetHistoryLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewGetHistoryLogic(ctx context.Context, svcCtx *svc.ServiceContext) *GetHistoryLogic {
	return &GetHistoryLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *GetHistoryLogic) GetHistory() (resp *types.SearchHistoryResp, err error) {
	client := searchservice.NewSearchService(l.svcCtx.SearchRpc)
	result, err := client.GetHistory(l.ctx, &searchservice.HistoryRequest{
		UserId: ctxutil.UserID(l.ctx),
	})
	if err != nil {
		return nil, err
	}
	return &types.SearchHistoryResp{
		Keywords: result.GetKeywords(),
	}, nil
}
