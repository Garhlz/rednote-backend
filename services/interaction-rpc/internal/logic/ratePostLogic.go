package logic

import (
	"context"
	"strconv"

	"interaction-rpc/interaction"
	"interaction-rpc/internal/svc"

	"github.com/zeromicro/go-zero/core/logx"
)

type RatePostLogic struct {
	ctx    context.Context
	svcCtx *svc.ServiceContext
	logx.Logger
}

func NewRatePostLogic(ctx context.Context, svcCtx *svc.ServiceContext) *RatePostLogic {
	return &RatePostLogic{
		ctx:    ctx,
		svcCtx: svcCtx,
		Logger: logx.WithContext(ctx),
	}
}

// RatePost 评分帖子
func (l *RatePostLogic) RatePost(in *interaction.RateRequest) (*interaction.Empty, error) {
	key := KeyPostRateHash + in.TargetId
	userIdStr := strconv.FormatInt(in.UserId, 10)
	scoreStr := strconv.FormatFloat(in.Score, 'f', 1, 64) // 保留1位小数

	// 1. BizRedis HSET (Hash Set)
	// 只要执行了 HSET，无论是否是新值，我们都视为一次评分行为，需要触发计算
	err := l.svcCtx.Redis.HsetCtx(l.ctx, key, userIdStr, scoreStr)
	if err != nil {
		l.Logger.Errorf("BizRedis error: %v", err)
		return nil, err
	}

	// 2. 发送 MQ (Java注释提到：无论是新增还是修改，统一用 Rate/ADD)
	event := &InteractionEvent{
		UserId:   in.UserId,
		TargetId: in.TargetId,
		Type:     "RATE",
		Action:   "ADD",
		Value:    in.Score, // 这里要把分数传过去
	}

	_ = publishEvent(l.ctx, l.svcCtx.MqChannel, RoutingKeyCreate, event)

	return &interaction.Empty{}, nil
}
