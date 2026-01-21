package logic

import (
	"context"
	"encoding/json"
	"time"

	"github.com/rabbitmq/amqp091-go"
	"github.com/zeromicro/go-zero/core/logx"
)

// ==========================================
// 1. 常量定义 (保持与 Java RedisKey 一致)
// ==========================================
const (
	// BizRedis Key 前缀
	KeyPostLikeSet    = "post:like:"    // 对应 Java: POST_LIKE_SET
	KeyPostCollectSet = "post:collect:" // 对应 Java: POST_COLLECT_SET
	KeyPostRateHash   = "post:rate:"    // 对应 Java: POST_RATE_HASH
	KeyCommentLikeSet = "comment:like:" // 对应 Java: COMMENT_LIKE_SET

	// RabbitMQ 配置 (必须与 Java 端配置一致)
	ExchangeName     = "platform.topic.exchange"
	RoutingKeyCreate = "interaction.create"
	RoutingKeyDelete = "interaction.delete"
)

// ==========================================
// 2. 事件结构体 (保持与 Java InteractionEvent 一致)
// ==========================================
type InteractionEvent struct {
	UserId   int64       `json:"userId"`
	TargetId string      `json:"targetId"`
	Type     string      `json:"type"`   // LIKE, COLLECT, RATE, COMMENT_LIKE
	Action   string      `json:"action"` // ADD, REMOVE
	Value    interface{} `json:"value"`  // 评分的分数 (Double/Float)
}

// ==========================================
// 3. 通用 MQ 发送函数
// ==========================================
func publishEvent(ctx context.Context, channel *amqp091.Channel, routingKey string, event *InteractionEvent) error {
	// 1. 序列化为 JSON
	body, err := json.Marshal(event)
	if err != nil {
		return err
	}

	// 2. 发送消息
	// 注意：Context 用于控制超时，amqp091-go 支持带 Context 的 Publish
	err = channel.PublishWithContext(ctx,
		ExchangeName, // exchange
		routingKey,   // routing key
		false,        // mandatory
		false,        // immediate
		amqp091.Publishing{
			ContentType:  "application/json",
			DeliveryMode: amqp091.Persistent, // 消息持久化
			Timestamp:    time.Now(),
			Body:         body,
		},
	)

	if err != nil {
		logx.WithContext(ctx).Errorf("Failed to publish event: %v", err)
		return err
	}

	logx.WithContext(ctx).Infof("Sent MQ Event: %s -> %s", routingKey, string(body))
	return nil
}
