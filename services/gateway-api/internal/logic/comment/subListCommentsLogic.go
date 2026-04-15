package comment

import (
	"context"

	"comment-rpc/commentservice"
	"gateway-api/internal/pkg/ctxutil"
	"gateway-api/internal/svc"
	"gateway-api/internal/types"

	"github.com/zeromicro/go-zero/core/logx"
)

type SubListCommentsLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewSubListCommentsLogic(ctx context.Context, svcCtx *svc.ServiceContext) *SubListCommentsLogic {
	return &SubListCommentsLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *SubListCommentsLogic) SubListComments(req *types.CommentSubListReq) (resp *types.CommentPageResult, err error) {
	client := commentservice.NewCommentService(l.svcCtx.CommentRpc)
	result, err := client.ListSubComments(l.ctx, &commentservice.ListSubCommentsRequest{
		RootCommentId: req.RootId,
		Page:          req.Page,
		PageSize:      req.Size,
		CurrentUserId: ctxutil.UserID(l.ctx),
	})
	if err != nil {
		return nil, err
	}
	return toCommentPageResult(result), nil
}
