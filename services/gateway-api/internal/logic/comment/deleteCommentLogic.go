package comment

import (
	"context"

	"comment-rpc/commentservice"
	"gateway-api/internal/pkg/ctxutil"
	"gateway-api/internal/svc"
	"gateway-api/internal/types"

	"github.com/zeromicro/go-zero/core/logx"
)

type DeleteCommentLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewDeleteCommentLogic(ctx context.Context, svcCtx *svc.ServiceContext) *DeleteCommentLogic {
	return &DeleteCommentLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *DeleteCommentLogic) DeleteComment(req *types.CommentIdPath) (resp *types.Empty, err error) {
	client := commentservice.NewCommentService(l.svcCtx.CommentRpc)
	_, err = client.DeleteComment(l.ctx, &commentservice.DeleteCommentRequest{
		CommentId:       req.Id,
		CurrentUserId:   ctxutil.UserID(l.ctx),
		CurrentUserRole: ctxutil.Role(l.ctx),
	})
	if err != nil {
		return nil, err
	}
	return &types.Empty{}, nil
}
