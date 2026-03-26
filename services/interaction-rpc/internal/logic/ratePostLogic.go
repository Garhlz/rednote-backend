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

// RatePost 评分帖子。
//
// 评分和点赞/收藏的差异在于：
// 1. 评分是 Hash 结构，field=userId，value=score
// 2. HSET 既可能是第一次评分，也可能是修改历史评分
// 3. 因为 Redis 中保存的是“当前用户的最新分数”，所以每次 HSET 后都需要发 MQ 让下游重新计算均值/人数
func (l *RatePostLogic) RatePost(in *interaction.RateRequest) (*interaction.Empty, error) {
	key := KeyPostRateHash + in.TargetId
	userIdStr := strconv.FormatInt(in.UserId, 10)
	scoreStr := strconv.FormatFloat(in.Score, 'f', 1, 64) // 保留1位小数

	if err := EnsurePostRateCache(l.ctx, l.svcCtx, in.TargetId); err != nil {
		l.Logger.Errorf("ensure post rate cache error: %v", err)
		return nil, err
	}

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

	// 评分写入后，同样去掉空值占位并更新 Bloom。
	removeDummyRate(l.ctx, l.svcCtx, key)
	AddBloom(l.ctx, l.svcCtx, KeyBloomPostRate, in.TargetId)

	_ = publishEvent(l.ctx, l.svcCtx.MqChannel, RoutingKeyCreate, event)

	return &interaction.Empty{}, nil
}
