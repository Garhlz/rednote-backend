// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package user

import (
	"context"

	"gateway-api/internal/pkg/ctxutil"
	"gateway-api/internal/svc"
	"gateway-api/internal/types"

	"github.com/zeromicro/go-zero/core/logx"
	"interaction-rpc/interactionservice"
	"user-rpc/userservice"
)

type GetPublicProfileLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewGetPublicProfileLogic(ctx context.Context, svcCtx *svc.ServiceContext) *GetPublicProfileLogic {
	return &GetPublicProfileLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *GetPublicProfileLogic) GetPublicProfile(req *types.UserIdPath) (resp *types.PublicUserProfile, err error) {
	client := userservice.NewUserService(l.svcCtx.UserRpc)
	result, err := client.GetPublicProfile(l.ctx, &userservice.GetPublicProfileRequest{
		UserId: req.UserId,
	})
	if err != nil {
		return nil, err
	}
	stats := &interactionservice.UserStatsResponse{}
	if svcCtx := l.svcCtx; svcCtx != nil {
		resp, statErr := interactionservice.NewInteractionService(svcCtx.InteractionRpc).GetUserStats(l.ctx, &interactionservice.UserStatsRequest{
		UserId:   req.UserId,
		ViewerId: ctxutil.UserID(l.ctx),
		})
		if statErr != nil {
			l.Logger.Errorf("get user stats failed: %v", statErr)
		} else if resp != nil {
			stats = resp
		}
	}
	return &types.PublicUserProfile{
		UserId:   result.GetUserId(),
		Nickname: result.GetNickname(),
		Avatar:   result.GetAvatar(),
		Bio:      result.GetBio(),
		Gender:   result.GetGender(),
		Region:   result.GetRegion(),
		FollowCount:       stats.GetFollowCount(),
		FanCount:          stats.GetFanCount(),
		ReceivedLikeCount: stats.GetReceivedLikeCount(),
		IsFollowed:        stats.GetIsFollowed(),
	}, nil
}
