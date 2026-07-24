package logic

import (
	"context"
	"testing"

	"user-rpc/user"

	"github.com/stretchr/testify/assert"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

func assertGrpcCode(t *testing.T, err error, want codes.Code) {
	t.Helper()
	assert.Equal(t, want, status.Code(err))
}

func TestLoginRejectsMissingCredentialsBeforeDependencies(t *testing.T) {
	tests := []*user.LoginRequest{
		{},
		{Email: "user@example.com"},
		{Password: "password"},
		{Email: "   ", Password: "password"},
	}
	for _, req := range tests {
		resp, err := NewLoginLogic(context.Background(), nil).Login(req)
		assert.Nil(t, resp)
		assertGrpcCode(t, err, codes.InvalidArgument)
	}
}

func TestRegisterRejectsIncompleteRequestBeforeDependencies(t *testing.T) {
	tests := []*user.RegisterRequest{
		{},
		{Email: "user@example.com", Code: "123456"},
		{Email: "user@example.com", Password: "password"},
		{Code: "123456", Password: "password"},
	}
	for _, req := range tests {
		resp, err := NewRegisterLogic(context.Background(), nil).Register(req)
		assert.Nil(t, resp)
		assertGrpcCode(t, err, codes.InvalidArgument)
	}
}

func TestSendEmailCodeRejectsInvalidInputBeforeDependencies(t *testing.T) {
	tests := []*user.SendEmailCodeRequest{
		{},
		{Email: "user@example.com", Scene: "login"},
		{Email: " ", Scene: emailSceneRegister},
	}
	for _, req := range tests {
		resp, err := NewSendEmailCodeLogic(context.Background(), nil).SendEmailCode(req)
		assert.Nil(t, resp)
		assertGrpcCode(t, err, codes.InvalidArgument)
	}
}

func TestRefreshAndUpdateRejectMissingIdentityBeforeDependencies(t *testing.T) {
	refreshResp, err := NewRefreshTokenLogic(context.Background(), nil).
		RefreshToken(&user.RefreshTokenRequest{})
	assert.Nil(t, refreshResp)
	assertGrpcCode(t, err, codes.InvalidArgument)

	profileResp, err := NewUpdateProfileLogic(context.Background(), nil).
		UpdateProfile(&user.UpdateProfileRequest{})
	assert.Nil(t, profileResp)
	assertGrpcCode(t, err, codes.InvalidArgument)
}
