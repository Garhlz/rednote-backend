package logic

import (
	"context"
	"strconv"

	"interaction-rpc/interaction"
	"interaction-rpc/internal/svc"

	"github.com/zeromicro/go-zero/core/logx"
)

type UnlikeCommentLogic struct {
	ctx    context.Context
	svcCtx *svc.ServiceContext
	logx.Logger
}

func NewUnlikeCommentLogic(ctx context.Context, svcCtx *svc.ServiceContext) *UnlikeCommentLogic {
	return &UnlikeCommentLogic{
		ctx:    ctx,
		svcCtx: svcCtx,
		Logger: logx.WithContext(ctx),
	}
}

// 取消点赞评论
func (l *UnlikeCommentLogic) UnlikeComment(in *interaction.InteractionRequest) (*interaction.Empty, error) {
	key := KeyCommentLikeSet + in.TargetId
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
			Type:     "COMMENT_LIKE",
			Action:   "REMOVE", // 注意这里
			Value:    nil,
		}
		// 使用 Delete 的 RoutingKey
		_ = publishEvent(l.ctx, l.svcCtx.MqChannel, RoutingKeyDelete, event)
	}

	return &interaction.Empty{}, nil
}
