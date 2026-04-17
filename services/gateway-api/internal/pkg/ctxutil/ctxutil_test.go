package ctxutil

import (
	"context"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestUserID(t *testing.T) {
	tests := []struct {
		name     string
		ctxVal   any
		ctxKey   string
		expected int64
	}{
		{
			name:     "valid int64 userId",
			ctxKey:   "userId",
			ctxVal:   int64(1024),
			expected: 1024,
		},
		{
			name:     "missing userId",
			ctxKey:   "other",
			ctxVal:   123,
			expected: 0,
		},
		{
			name:     "wrong type (string) userId",
			ctxKey:   "userId",
			ctxVal:   "1024",
			expected: 0,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			ctx := context.Background()
			if tt.ctxKey != "" {
				ctx = context.WithValue(ctx, tt.ctxKey, tt.ctxVal)
			}
			assert.Equal(t, tt.expected, UserID(ctx))
		})
	}
}

func TestStringExtractors(t *testing.T) {
	tests := []struct {
		name      string
		extractor func(context.Context) string
		ctxKey    string
		ctxVal    any
		expected  string
	}{
		{"Role - valid", Role, "role", "admin", "admin"},
		{"Role - missing", Role, "other", "user", ""},
		{"Nickname - valid", Nickname, "nickname", "Bob", "Bob"},
		{"AuthToken - valid", AuthToken, "accessToken", "jwt.token.abc", "jwt.token.abc"},
		{"RefreshToken - valid", RefreshToken, "refreshToken", "refresh.abc", "refresh.abc"},
		{"RequestID - valid", RequestID, "requestId", "req-123", "req-123"},
		{"TraceID - valid", TraceID, "traceId", "trace-456", "trace-456"},
		{"Wrong Type (int) fallback", Role, "role", 123, ""},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			ctx := context.Background()
			if tt.ctxKey != "" {
				ctx = context.WithValue(ctx, tt.ctxKey, tt.ctxVal)
			}
			assert.Equal(t, tt.expected, tt.extractor(ctx))
		})
	}
}
