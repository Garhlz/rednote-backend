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

type GetProfileLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewGetProfileLogic(ctx context.Context, svcCtx *svc.ServiceContext) *GetProfileLogic {
	return &GetProfileLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *GetProfileLogic) GetProfile() (resp *types.UserProfile, err error) {
	client := userservice.NewUserService(l.svcCtx.UserRpc)
	result, err := client.GetMyProfile(l.ctx, &userservice.GetMyProfileRequest{
		UserId: ctxutil.UserID(l.ctx),
	})
	if err != nil {
		return nil, err
	}
	stats := &interactionservice.UserStatsResponse{}
	statResp, statErr := interactionservice.NewInteractionService(l.svcCtx.InteractionRpc).GetUserStats(l.ctx, &interactionservice.UserStatsRequest{
		UserId:   result.GetUserId(),
		ViewerId: result.GetUserId(),
	})
	if statErr != nil {
		l.Logger.Errorf("get user stats failed: %v", statErr)
	} else if statResp != nil {
		stats = statResp
	}
	return &types.UserProfile{
		UserId:            result.GetUserId(),
		Email:             result.GetEmail(),
		Nickname:          result.GetNickname(),
		Avatar:            result.GetAvatar(),
		Bio:               result.GetBio(),
		Gender:            result.GetGender(),
		Birthday:          result.GetBirthday(),
		Region:            result.GetRegion(),
		Role:              result.GetRole(),
		Status:            result.GetStatus(),
		FollowCount:       stats.GetFollowCount(),
		FanCount:          stats.GetFanCount(),
		ReceivedLikeCount: stats.GetReceivedLikeCount(),
	}, nil
}
