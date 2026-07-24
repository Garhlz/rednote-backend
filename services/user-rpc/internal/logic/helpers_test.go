package logic

import (
	"database/sql"
	"testing"
	"time"

	"user-rpc/internal/config"
	"user-rpc/internal/model"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestNormalizeEmailScene(t *testing.T) {
	tests := []struct {
		name  string
		input string
		want  string
		ok    bool
	}{
		{name: "register", input: "register", want: emailSceneRegister, ok: true},
		{name: "trim whitespace", input: "  bind_email  ", want: emailSceneBindEmail, ok: true},
		{name: "reset password", input: "reset_password", want: emailSceneResetPassword, ok: true},
		{name: "unknown", input: "login", ok: false},
		{name: "empty", input: "", ok: false},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, ok := normalizeEmailScene(tt.input)
			assert.Equal(t, tt.want, got)
			assert.Equal(t, tt.ok, ok)
		})
	}
}

func TestParseBirthday(t *testing.T) {
	got, err := parseBirthday("2024-02-29")
	require.NoError(t, err)
	assert.True(t, got.Valid)
	assert.Equal(t, "2024-02-29", got.Time.Format("2006-01-02"))

	empty, err := parseBirthday("  ")
	require.NoError(t, err)
	assert.False(t, empty.Valid)

	_, err = parseBirthday("2024-02-30")
	assert.Error(t, err)
}

func TestPasswordHashRoundTrip(t *testing.T) {
	hashed, err := hashPassword("correct horse battery staple")
	require.NoError(t, err)
	assert.NotEqual(t, "correct horse battery staple", hashed)
	assert.True(t, checkPassword(hashed, "correct horse battery staple"))
	assert.False(t, checkPassword(hashed, "wrong"))
	assert.False(t, checkPassword("", "anything"))
}

func TestTokenPairRoundTripAndTypes(t *testing.T) {
	cfg := config.Config{}
	cfg.Jwt.Secret = "test-secret-with-enough-entropy-only-for-tests"
	cfg.Jwt.Issuer = "sharely-test"
	cfg.Jwt.AccessExpireSeconds = 60
	cfg.Jwt.RefreshExpireSeconds = 120
	u := &model.Users{Id: 42, Role: "USER", Nickname: "tester", TokenVersion: 3}

	pair, jti, err := buildTokenPair(cfg, u)
	require.NoError(t, err)
	require.NotEmpty(t, jti)

	access, err := parseToken(cfg, pair.AccessToken)
	require.NoError(t, err)
	assert.Equal(t, int64(42), access.UserId)
	assert.Equal(t, "access", access.Type)
	assert.Equal(t, int64(3), access.TokenVersion)
	assert.Empty(t, access.ID)

	refresh, err := parseToken(cfg, pair.RefreshToken)
	require.NoError(t, err)
	assert.Equal(t, "refresh", refresh.Type)
	assert.Equal(t, jti, refresh.ID)
	assert.True(t, refresh.ExpiresAt.After(time.Now()))

	wrongCfg := cfg
	wrongCfg.Jwt.Secret = "different-secret"
	_, err = parseToken(wrongCfg, pair.AccessToken)
	assert.Error(t, err)
}

func TestProfileMappingHandlesNullableFields(t *testing.T) {
	u := &model.Users{
		Id:       7,
		Nickname: "Elaine",
		Region:   sql.NullString{String: "ignored", Valid: false},
		Birthday: sql.NullTime{Valid: false},
	}

	private := buildUserProfile(u)
	public := buildPublicProfile(u)
	assert.Empty(t, private.Region)
	assert.Empty(t, private.Birthday)
	assert.Empty(t, public.Region)
}

func TestStorageKeysAreNamespacedAndStable(t *testing.T) {
	assert.Equal(t, "verify:code:register:user@example.com", emailCodeKey(emailSceneRegister, "user@example.com"))
	assert.Equal(t, "verify:limit:bind_email:user@example.com", emailLimitKey(emailSceneBindEmail, "user@example.com"))
	assert.Equal(t, "auth:refresh:42:jti-1", refreshTokenKey(42, "jti-1"))
	assert.Equal(t, "auth:token:version:42", tokenVersionKey(42))

	first := blockedTokenKey("token-a")
	second := blockedTokenKey("token-a")
	assert.Equal(t, first, second)
	assert.NotContains(t, first, "token-a")
	assert.NotEqual(t, first, blockedTokenKey("token-b"))
}

func TestGeneratedValuesHaveExpectedFormat(t *testing.T) {
	jti, err := newTokenJti()
	require.NoError(t, err)
	assert.Regexp(t, `^[0-9a-f]{32}$`, jti)

	for i := 0; i < 20; i++ {
		code, err := randCode()
		require.NoError(t, err)
		assert.Regexp(t, `^[0-9]{6}$`, code)
	}
}

func TestBuildEmailMessageUsesCRLFAndUtf8Header(t *testing.T) {
	message := buildEmailMessage("from@example.com", "to@example.com", "验证码", "正文")
	assert.Contains(t, message, "From: from@example.com\r\n")
	assert.Contains(t, message, "To: to@example.com\r\n")
	assert.Contains(t, message, "Content-Type: text/plain; charset=UTF-8\r\n")
	assert.Contains(t, message, "\r\n\r\n正文")
}
