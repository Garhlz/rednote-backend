// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package tag

import (
	"context"

	"gateway-api/internal/svc"
	"gateway-api/internal/types"

	"github.com/zeromicro/go-zero/core/logx"
)

type AiGenerateTagsLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewAiGenerateTagsLogic(ctx context.Context, svcCtx *svc.ServiceContext) *AiGenerateTagsLogic {
	return &AiGenerateTagsLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *AiGenerateTagsLogic) AiGenerateTags(req *types.Empty) (resp *types.Empty, err error) {
	// todo: add your logic here and delete this line

	return
}
