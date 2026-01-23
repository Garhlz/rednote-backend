// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package user

import (
	"context"

	"gateway-api/internal/pkg/ctxutil"
	"gateway-api/internal/svc"
	"gateway-api/internal/types"

	"github.com/zeromicro/go-zero/core/logx"
	"user-rpc/userservice"
)

type UpdateProfileLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewUpdateProfileLogic(ctx context.Context, svcCtx *svc.ServiceContext) *UpdateProfileLogic {
	return &UpdateProfileLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *UpdateProfileLogic) UpdateProfile(req *types.UpdateProfileReq) (resp *types.UserProfile, err error) {
	client := userservice.NewUserService(l.svcCtx.UserRpc)
	rpcReq := &userservice.UpdateProfileRequest{
		UserId: ctxutil.UserID(l.ctx),
	}
	if req.Nickname != "" {
		rpcReq.Nickname = &req.Nickname
	}
	if req.Avatar != "" {
		rpcReq.Avatar = &req.Avatar
	}
	if req.Bio != "" {
		rpcReq.Bio = &req.Bio
	}
	if req.Gender != 0 {
		rpcReq.Gender = &req.Gender
	}
	if req.Birthday != "" {
		rpcReq.Birthday = &req.Birthday
	}
	if req.Region != "" {
		rpcReq.Region = &req.Region
	}

	result, err := client.UpdateProfile(l.ctx, rpcReq)
	if err != nil {
		return nil, err
	}
	return &types.UserProfile{
		UserId:   result.GetUserId(),
		Email:    result.GetEmail(),
		Nickname: result.GetNickname(),
		Avatar:   result.GetAvatar(),
		Bio:      result.GetBio(),
		Gender:   result.GetGender(),
		Birthday: result.GetBirthday(),
		Region:   result.GetRegion(),
		Role:     result.GetRole(),
		Status:   result.GetStatus(),
	}, nil
}
