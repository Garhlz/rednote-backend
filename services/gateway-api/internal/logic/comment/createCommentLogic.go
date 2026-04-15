package comment

import (
	"context"

	"comment-rpc/commentservice"
	"gateway-api/internal/pkg/ctxutil"
	"gateway-api/internal/svc"
	"gateway-api/internal/types"
	"user-rpc/userservice"

	"github.com/zeromicro/go-zero/core/logx"
)

type CreateCommentLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewCreateCommentLogic(ctx context.Context, svcCtx *svc.ServiceContext) *CreateCommentLogic {
	return &CreateCommentLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *CreateCommentLogic) CreateComment(req *types.CommentCreateReq) (resp *types.CommentVO, err error) {
	userClient := userservice.NewUserService(l.svcCtx.UserRpc)
	profile, err := userClient.GetMyProfile(l.ctx, &userservice.GetMyProfileRequest{
		UserId: ctxutil.UserID(l.ctx),
	})
	if err != nil {
		return nil, err
	}

	client := commentservice.NewCommentService(l.svcCtx.CommentRpc)
	result, err := client.CreateComment(l.ctx, &commentservice.CreateCommentRequest{
		PostId:        req.PostId,
		CurrentUserId: profile.GetUserId(),
		UserNickname:  profile.GetNickname(),
		UserAvatar:    profile.GetAvatar(),
		Content:       req.Content,
		ParentId:      req.ParentId,
	})
	if err != nil {
		return nil, err
	}

	return toCommentVO(result), nil
}
