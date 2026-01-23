// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package post

import (
	"context"

	"gateway-api/internal/svc"
	"gateway-api/internal/types"

	"github.com/zeromicro/go-zero/core/logx"
	"search-rpc/searchservice"
)

type SuggestLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewSuggestLogic(ctx context.Context, svcCtx *svc.ServiceContext) *SuggestLogic {
	return &SuggestLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *SuggestLogic) Suggest(req *types.SuggestReq) (resp *types.SearchSuggestResp, err error) {
	client := searchservice.NewSearchService(l.svcCtx.SearchRpc)
	result, err := client.Suggest(l.ctx, &searchservice.SuggestRequest{
		Keyword: req.Keyword,
	})
	if err != nil {
		return nil, err
	}
	return &types.SearchSuggestResp{
		Suggestions: result.GetSuggestions(),
	}, nil
}
