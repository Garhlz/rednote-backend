package model

import (
	"testing"

	"notification-rpc/notification"

	"github.com/stretchr/testify/assert"
)

func TestFormatNotificationType(t *testing.T) {
	cases := []struct {
		name     string
		input    notification.NotificationType
		expected string
	}{
		{"COMMENT", notification.NotificationType_COMMENT, "COMMENT"},
		{"REPLY", notification.NotificationType_REPLY, "REPLY"},
		{"LIKE_POST", notification.NotificationType_LIKE_POST, "LIKE_POST"},
		{"COLLECT_POST", notification.NotificationType_COLLECT_POST, "COLLECT_POST"},
		{"RATE_POST", notification.NotificationType_RATE_POST, "RATE_POST"},
		{"LIKE_COMMENT", notification.NotificationType_LIKE_COMMENT, "LIKE_COMMENT"},
		{"FOLLOW", notification.NotificationType_FOLLOW, "FOLLOW"},
		{"SYSTEM", notification.NotificationType_SYSTEM, "SYSTEM"},
		{"SYSTEM_AUDIT_PASS", notification.NotificationType_SYSTEM_AUDIT_PASS, "SYSTEM_AUDIT_PASS"},
		{"SYSTEM_AUDIT_REJECT", notification.NotificationType_SYSTEM_AUDIT_REJECT, "SYSTEM_AUDIT_REJECT"},
		{"SYSTEM_POST_DELETE", notification.NotificationType_SYSTEM_POST_DELETE, "SYSTEM_POST_DELETE"},
		// 默认 fallback：NOTIFICATION_TYPE_UNKNOWN → "UNKNOWN"
		{"UNKNOWN_fallback", notification.NotificationType_NOTIFICATION_TYPE_UNKNOWN, "UNKNOWN"},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			assert.Equal(t, tc.expected, FormatNotificationType(tc.input))
		})
	}
}

func TestParseNotificationType(t *testing.T) {
	cases := []struct {
		name     string
		input    string
		expected notification.NotificationType
	}{
		// 正向解析（大写）
		{"COMMENT", "COMMENT", notification.NotificationType_COMMENT},
		{"REPLY", "REPLY", notification.NotificationType_REPLY},
		{"LIKE_POST", "LIKE_POST", notification.NotificationType_LIKE_POST},
		{"COLLECT_POST", "COLLECT_POST", notification.NotificationType_COLLECT_POST},
		{"RATE_POST", "RATE_POST", notification.NotificationType_RATE_POST},
		{"LIKE_COMMENT", "LIKE_COMMENT", notification.NotificationType_LIKE_COMMENT},
		{"FOLLOW", "FOLLOW", notification.NotificationType_FOLLOW},
		{"SYSTEM", "SYSTEM", notification.NotificationType_SYSTEM},
		{"SYSTEM_AUDIT_PASS", "SYSTEM_AUDIT_PASS", notification.NotificationType_SYSTEM_AUDIT_PASS},
		{"SYSTEM_AUDIT_REJECT", "SYSTEM_AUDIT_REJECT", notification.NotificationType_SYSTEM_AUDIT_REJECT},
		{"SYSTEM_POST_DELETE", "SYSTEM_POST_DELETE", notification.NotificationType_SYSTEM_POST_DELETE},
		// 大小写不敏感：小写也能正确解析
		{"comment_lowercase", "comment", notification.NotificationType_COMMENT},
		{"like_post_lowercase", "like_post", notification.NotificationType_LIKE_POST},
		{"follow_lowercase", "follow", notification.NotificationType_FOLLOW},
		// 未知字符串 fallback 到 NOTIFICATION_TYPE_UNKNOWN
		{"unknown_string", "UNKNOWN", notification.NotificationType_NOTIFICATION_TYPE_UNKNOWN},
		{"empty_string", "", notification.NotificationType_NOTIFICATION_TYPE_UNKNOWN},
		{"garbage_string", "NOT_A_TYPE", notification.NotificationType_NOTIFICATION_TYPE_UNKNOWN},
		// 含空格前后 trim
		{"comment_with_spaces", "  COMMENT  ", notification.NotificationType_COMMENT},
		{"reply_with_spaces", " reply ", notification.NotificationType_REPLY},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			assert.Equal(t, tc.expected, ParseNotificationType(tc.input))
		})
	}
}
