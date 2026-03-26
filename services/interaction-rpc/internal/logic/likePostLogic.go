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

// LikePost 点赞帖子。
//
// 当前实现是“Redis 实时集合 + MQ 异步落后置存储”的 write-behind 模式：
// 1. 用户点完赞，先把 Redis 里的展示状态改对，保证前端立刻读到最新结果
// 2. 再发 MQ 给 Java/Mongo/ES 做最终一致更新
//
// 这里最重要的防线是“写前预热”：
// 如果 Redis key 因重启或淘汰而丢失，直接 SADD 会把当前用户的点赞误当成全量数据。
// 所以必须先 EnsurePostLikeCache，把 Mongo 中历史点赞补齐后再写。
func (l *LikePostLogic) LikePost(in *interaction.InteractionRequest) (*interaction.Empty, error) {
	// 1. 拼接 BizRedis Key
	// 集合，存储每个PostId对应的点过赞的userId
	key := KeyPostLikeSet + in.TargetId
	userIdStr := strconv.FormatInt(in.UserId, 10)

	if err := EnsurePostLikeCache(l.ctx, l.svcCtx, in.TargetId); err != nil {
		l.Logger.Errorf("ensure post like cache error: %v", err)
		return nil, err
	}

	// 2. BizRedis SADD 操作
	// SaddCtx 会返回成功添加的数量 (1表示新加，0表示已存在)
	added, err := l.svcCtx.Redis.SaddCtx(l.ctx, key, userIdStr)
	if err != nil {
		l.Logger.Errorf("BizRedis error: %v", err)
		return nil, err
	}

	// 3. 如果添加成功，发送 MQ 消息
	if added > 0 {
		// 一旦写入了真实 userId，就把 Dummy 占位节点移除，避免它影响后续计数。
		removeDummyUser(l.ctx, l.svcCtx, key)
		// 真实互动出现后，把帖子加入 Bloom，后续读链路可以更快判断“值得预热”。
		AddBloom(l.ctx, l.svcCtx, KeyBloomPostLike, in.TargetId)

		event := &InteractionEvent{
			UserId:   in.UserId,
			TargetId: in.TargetId,
			Type:     "LIKE",
			Action:   "ADD",
			Value:    nil,
		}
		// 调用 common.go 里的通用发送方法
		// interaction.* 由 Java 侧 listener 消费，用于更新 Mongo 冗余字段、ES 索引等最终一致结果。
		if err := publishEvent(l.ctx, l.svcCtx.MqChannel, RoutingKeyCreate, event); err != nil {
			// 注意：这里发送失败是否要回滚 BizRedis？
			// 对于点赞这种非强一致业务，通常只打印 Error log，不回滚，允许短暂不一致。
			l.Logger.Errorf("Failed to send MQ: %v", err)
		}
	}

	return &interaction.Empty{}, nil
}
