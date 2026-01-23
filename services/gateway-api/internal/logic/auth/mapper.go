package auth

import (
	"gateway-api/internal/types"
	"user-rpc/userservice"
)

func mapAuthResponse(resp *userservice.AuthResponse) *types.AuthResponse {
	if resp == nil {
		return &types.AuthResponse{}
	}
	result := &types.AuthResponse{}
	if resp.Tokens != nil {
		result.Tokens = types.TokenPair{
			AccessToken:   resp.Tokens.AccessToken,
			RefreshToken:  resp.Tokens.RefreshToken,
			TokenType:     resp.Tokens.TokenType,
			AccessExpire:  resp.Tokens.AccessExpire,
			RefreshExpire: resp.Tokens.RefreshExpire,
		}
	}
	if resp.User != nil {
		result.User = types.UserSummary{
			UserId:   resp.User.UserId,
			Nickname: resp.User.Nickname,
			Avatar:   resp.User.Avatar,
			Role:     resp.User.Role,
			Status:   resp.User.Status,
		}
	}
	return result
}
