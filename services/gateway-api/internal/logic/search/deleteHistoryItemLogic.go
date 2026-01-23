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

type DeleteHistoryItemLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewDeleteHistoryItemLogic(ctx context.Context, svcCtx *svc.ServiceContext) *DeleteHistoryItemLogic {
	return &DeleteHistoryItemLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *DeleteHistoryItemLogic) DeleteHistoryItem(req *types.HistoryReq) (resp *types.Empty, err error) {
	client := searchservice.NewSearchService(l.svcCtx.SearchRpc)
	_, err = client.DeleteHistory(l.ctx, &searchservice.ClearHistoryRequest{
		UserId:  ctxutil.UserID(l.ctx),
		Keyword: req.Keyword,
	})
	if err != nil {
		return nil, err
	}
	return &types.Empty{}, nil
}
