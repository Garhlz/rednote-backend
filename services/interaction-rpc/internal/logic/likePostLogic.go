package logic

import (
	"context"
	"strconv"

	"interaction-rpc/interaction"
	"interaction-rpc/internal/svc"

	"github.com/zeromicro/go-zero/core/logx"
)

type LikePostLogic struct {
	ctx    context.Context
	svcCtx *svc.ServiceContext
	logx.Logger
}

func NewLikePostLogic(ctx context.Context, svcCtx *svc.ServiceContext) *LikePostLogic {
	return &LikePostLogic{
		ctx:    ctx,
		svcCtx: svcCtx,
		Logger: logx.WithContext(ctx),
	}
}

// LikePost 点赞帖子
func (l *LikePostLogic) LikePost(in *interaction.InteractionRequest) (*interaction.Empty, error) {
	// 1. 拼接 BizRedis Key
	key := KeyPostLikeSet + in.TargetId
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
			Type:     "LIKE",
			Action:   "ADD",
			Value:    nil,
		}
		// 调用 common.go 里的通用发送方法
		if err := publishEvent(l.ctx, l.svcCtx.MqChannel, RoutingKeyCreate, event); err != nil {
			// 注意：这里发送失败是否要回滚 BizRedis？
			// 对于点赞这种非强一致业务，通常只打印 Error log，不回滚，允许短暂不一致。
			l.Logger.Errorf("Failed to send MQ: %v", err)
		}
	}

	return &interaction.Empty{}, nil
}
