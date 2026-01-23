// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package sync

import (
	"context"

	"gateway-api/internal/svc"
	"gateway-api/internal/types"

	"github.com/zeromicro/go-zero/core/logx"
)

type SyncMongoToEsLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewSyncMongoToEsLogic(ctx context.Context, svcCtx *svc.ServiceContext) *SyncMongoToEsLogic {
	return &SyncMongoToEsLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *SyncMongoToEsLogic) SyncMongoToEs() (resp *types.Empty, err error) {
	// todo: add your logic here and delete this line

	return
}
