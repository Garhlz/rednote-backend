package model

import (
	"strings"
	"time"

	"notification-rpc/notification"

	"go.mongodb.org/mongo-driver/bson/primitive"
)

type NotificationDoc struct {
	// Mongo 主键。
	ID primitive.ObjectID `bson:"_id,omitempty"`
	// ReceiverID 是通知接收者，也就是“谁会在消息中心里看到这条通知”。
	ReceiverID int64 `bson:"receiverId"`
	// SenderID 是动作发起者，比如点赞人、评论人、关注发起人。
	SenderID       int64  `bson:"senderId"`
	SenderNickname string `bson:"senderNickname"`
	SenderAvatar   string `bson:"senderAvatar"`
	// Type 直接复用业务通知类型，方便 Java/Go 两侧对齐。
	Type string `bson:"type"`
	// TargetID 通常指向被作用的业务对象，比如帖子 ID、评论 ID。
	TargetID string `bson:"targetId"`
	// TargetPreview 是前端消息列表里展示的摘要信息。
	TargetPreview string    `bson:"targetPreview"`
	IsRead        bool      `bson:"isRead"`
	CreatedAt     time.Time `bson:"createdAt"`
}

// ToProto 用于把 Mongo 文档转换成 RPC 响应结构。
// created_at 统一转换成 unix 秒，避免 Java/Go 对时间字符串格式做额外适配。
func (d NotificationDoc) ToProto() *notification.Notification {
	return &notification.Notification{
		Id:             d.ID.Hex(),
		ReceiverId:     d.ReceiverID,
		SenderId:       d.SenderID,
		SenderNickname: d.SenderNickname,
		SenderAvatar:   d.SenderAvatar,
		Type:           ParseNotificationType(d.Type),
		TargetId:       d.TargetID,
		TargetPreview:  d.TargetPreview,
		IsRead:         d.IsRead,
		CreatedAt:      d.CreatedAt.Unix(),
	}
}

// ParseNotificationType 把 Mongo 里存储的字符串类型映射回 proto 枚举。
// 这里允许输入大小写混杂，尽量提高读旧数据时的兼容性。
func ParseNotificationType(v string) notification.NotificationType {
	switch strings.TrimSpace(strings.ToUpper(v)) {
	case "COMMENT":
		return notification.NotificationType_COMMENT
	case "REPLY":
		return notification.NotificationType_REPLY
	case "LIKE_POST":
		return notification.NotificationType_LIKE_POST
	case "COLLECT_POST":
		return notification.NotificationType_COLLECT_POST
	case "RATE_POST":
		return notification.NotificationType_RATE_POST
	case "LIKE_COMMENT":
		return notification.NotificationType_LIKE_COMMENT
	case "FOLLOW":
		return notification.NotificationType_FOLLOW
	case "SYSTEM":
		return notification.NotificationType_SYSTEM
	case "SYSTEM_AUDIT_PASS":
		return notification.NotificationType_SYSTEM_AUDIT_PASS
	case "SYSTEM_AUDIT_REJECT":
		return notification.NotificationType_SYSTEM_AUDIT_REJECT
	case "SYSTEM_POST_DELETE":
		return notification.NotificationType_SYSTEM_POST_DELETE
	default:
		return notification.NotificationType_NOTIFICATION_TYPE_UNKNOWN
	}
}

// FormatNotificationType 把 proto 枚举映射成 Mongo 中持久化的字符串值。
// 当前通知服务以字符串作为存储格式，便于与 platform-java 的旧数据保持一致。
func FormatNotificationType(v notification.NotificationType) string {
	switch v {
	case notification.NotificationType_COMMENT:
		return "COMMENT"
	case notification.NotificationType_REPLY:
		return "REPLY"
	case notification.NotificationType_LIKE_POST:
		return "LIKE_POST"
	case notification.NotificationType_COLLECT_POST:
		return "COLLECT_POST"
	case notification.NotificationType_RATE_POST:
		return "RATE_POST"
	case notification.NotificationType_LIKE_COMMENT:
		return "LIKE_COMMENT"
	case notification.NotificationType_FOLLOW:
		return "FOLLOW"
	case notification.NotificationType_SYSTEM:
		return "SYSTEM"
	case notification.NotificationType_SYSTEM_AUDIT_PASS:
		return "SYSTEM_AUDIT_PASS"
	case notification.NotificationType_SYSTEM_AUDIT_REJECT:
		return "SYSTEM_AUDIT_REJECT"
	case notification.NotificationType_SYSTEM_POST_DELETE:
		return "SYSTEM_POST_DELETE"
	default:
		return "UNKNOWN"
	}
}
