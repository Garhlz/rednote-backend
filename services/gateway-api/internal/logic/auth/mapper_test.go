package auth

import (
	"testing"

	"user-rpc/userservice"

	"github.com/stretchr/testify/assert"
)

func TestMapAuthResponse(t *testing.T) {
	t.Run("nil response", func(t *testing.T) {
		got := mapAuthResponse(nil)
		assert.Empty(t, got.Tokens.AccessToken)
		assert.Zero(t, got.User.UserId)
	})

	t.Run("complete response", func(t *testing.T) {
		got := mapAuthResponse(&userservice.AuthResponse{
			Tokens: &userservice.TokenPair{
				AccessToken:   "access",
				RefreshToken:  "refresh",
				TokenType:     "Bearer",
				AccessExpire:  100,
				RefreshExpire: 200,
			},
			User: &userservice.UserSummary{
				UserId:   42,
				Nickname: "Elaine",
				Avatar:   "avatar.png",
				Role:     "USER",
				Status:   1,
			},
		})

		assert.Equal(t, "access", got.Tokens.AccessToken)
		assert.Equal(t, "refresh", got.Tokens.RefreshToken)
		assert.Equal(t, int64(100), got.Tokens.AccessExpire)
		assert.Equal(t, int64(42), got.User.UserId)
		assert.Equal(t, "Elaine", got.User.Nickname)
		assert.Equal(t, int32(1), got.User.Status)
	})

	t.Run("partial response", func(t *testing.T) {
		got := mapAuthResponse(&userservice.AuthResponse{
			User: &userservice.UserSummary{UserId: 7},
		})
		assert.Zero(t, got.Tokens.AccessExpire)
		assert.Equal(t, int64(7), got.User.UserId)
	})
}
