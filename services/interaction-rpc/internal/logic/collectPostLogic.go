package logic

import (
	"context"
	"strconv"

	"interaction-rpc/interaction"
	"interaction-rpc/internal/svc"

	"github.com/zeromicro/go-zero/core/logx"
)

type CollectPostLogic struct {
	ctx    context.Context
	svcCtx *svc.ServiceContext
	logx.Logger
}

func NewCollectPostLogic(ctx context.Context, svcCtx *svc.ServiceContext) *CollectPostLogic {
	return &CollectPostLogic{
		ctx:    ctx,
		svcCtx: svcCtx,
		Logger: logx.WithContext(ctx),
	}
}

// 收藏帖子
func (l *CollectPostLogic) CollectPost(in *interaction.InteractionRequest) (*interaction.Empty, error) {
	// 1. 拼接 BizRedis Key
	key := KeyPostCollectSet + in.TargetId
	userIdStr := strconv.FormatInt(in.UserId, 10)

	// 2. BizRedis SADD 操作
	// Saddle 会返回成功添加的数量 (1表示新加，0表示已存在)
	added, err := l.svcCtx.Redis.SaddCtx(l.ctx, key, userIdStr)
	if err != nil {
		l.Logger.Errorf("BizRedis error: %v", err)
		return nil, err
	}

	// 3. 如果添加成功，发送 MQ 消息
	if added > 0 {
		event := &InteractionEvent{
			UserId:   in.UserId,
			TargetId: in.TargetId,
			Type:     "COLLECT",
			Action:   "ADD",
			Value:    nil,
		}
		// 调用 common.go 里的通用发送方法
		if err := publishEvent(l.ctx, l.svcCtx.MqChannel, RoutingKeyCreate, event); err != nil {
			// 这里不回滚Redis
			l.Logger.Errorf("Failed to send MQ: %v", err)
		}
	}

	return &interaction.Empty{}, nil
}
