package comment

import (
	"context"

	"comment-rpc/commentservice"
	"gateway-api/internal/pkg/ctxutil"
	"gateway-api/internal/svc"
	"gateway-api/internal/types"

	"github.com/zeromicro/go-zero/core/logx"
)

type ListCommentsLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewListCommentsLogic(ctx context.Context, svcCtx *svc.ServiceContext) *ListCommentsLogic {
	return &ListCommentsLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *ListCommentsLogic) ListComments(req *types.CommentListReq) (resp *types.CommentPageResult, err error) {
	client := commentservice.NewCommentService(l.svcCtx.CommentRpc)
	result, err := client.ListRootComments(l.ctx, &commentservice.ListRootCommentsRequest{
		PostId:        req.PostId,
		Page:          req.Page,
		PageSize:      req.Size,
		CurrentUserId: ctxutil.UserID(l.ctx),
	})
	if err != nil {
		return nil, err
	}
	return toCommentPageResult(result), nil
}
