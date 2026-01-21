package logic

import (
	"context"
	"strconv"

	"interaction-rpc/interaction"
	"interaction-rpc/internal/svc"

	"github.com/zeromicro/go-zero/core/logx"
)

type UncollectPostLogic struct {
	ctx    context.Context
	svcCtx *svc.ServiceContext
	logx.Logger
}

func NewUncollectPostLogic(ctx context.Context, svcCtx *svc.ServiceContext) *UncollectPostLogic {
	return &UncollectPostLogic{
		ctx:    ctx,
		svcCtx: svcCtx,
		Logger: logx.WithContext(ctx),
	}
}

// 取消收藏帖子
func (l *UncollectPostLogic) UncollectPost(in *interaction.InteractionRequest) (*interaction.Empty, error) {
	key := KeyPostCollectSet + in.TargetId
	userIdStr := strconv.FormatInt(in.UserId, 10)

	// BizRedis SREM
	removed, err := l.svcCtx.Redis.SremCtx(l.ctx, key, userIdStr)
	if err != nil {
		l.Logger.Errorf("BizRedis error: %v", err)
		return nil, err
	}

	if removed > 0 {
		event := &InteractionEvent{
			UserId:   in.UserId,
			TargetId: in.TargetId,
			Type:     "COLLECT",
			Action:   "REMOVE", // 注意这里
			Value:    nil,
		}
		// 使用 Delete 的 RoutingKey
		_ = publishEvent(l.ctx, l.svcCtx.MqChannel, RoutingKeyDelete, event)
	}

	return &interaction.Empty{}, nil
}
