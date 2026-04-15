package logic

import (
	"context"
	"encoding/json"
	"strconv"
	"time"

	"github.com/rabbitmq/amqp091-go"
	"github.com/zeromicro/go-zero/core/logx"
	"go.opentelemetry.io/otel"
	"google.golang.org/grpc/metadata"
)

const (
	commentExchangeName     = "platform.topic.exchange"
	commentRoutingKeyCreate = "comment.create"
	commentRoutingKeyDelete = "comment.delete"
)

type CommentEvent struct {
	Type          string `json:"type"`
	CommentId     string `json:"commentId"`
	PostId        string `json:"postId"`
	UserId        int64  `json:"userId"`
	UserNickname  string `json:"userNickname"`
	Content       string `json:"content"`
	PostAuthorId  int64  `json:"postAuthorId"`
	ReplyToUserId int64  `json:"replyToUserId"`
	ParentId      string `json:"parentId"`
}

func publishCommentEvent(ctx context.Context, channel *amqp091.Channel, routingKey string, event *CommentEvent) error {
	if channel == nil || event == nil {
		return nil
	}

	body, err := json.Marshal(event)
	if err != nil {
		return err
	}

	requestID := requestIDFromContext(ctx)
	headers := amqp091.Table{
		"X-Request-Id":  requestID,
		"X-Trace-Id":    requestID,
		"X-Service":     "comment-rpc",
		"X-Routing-Key": routingKey,
	}
	otel.GetTextMapPropagator().Inject(ctx, amqpHeaderCarrier(headers))
	err = channel.PublishWithContext(ctx,
		commentExchangeName,
		routingKey,
		false,
		false,
		amqp091.Publishing{
			ContentType:  "application/json",
			DeliveryMode: amqp091.Persistent,
			Timestamp:    time.Now(),
			Body:         body,
			Headers:      headers,
		},
	)
	if err != nil {
		logx.WithContext(ctx).Errorf("mq publish failed routingKey=%s requestId=%s err=%v", routingKey, requestID, err)
		return err
	}

	logx.WithContext(ctx).Infof("mq publish success routingKey=%s requestId=%s commentId=%s postId=%s", routingKey, requestID, event.CommentId, event.PostId)
	return nil
}

func requestIDFromContext(ctx context.Context) string {
	if ctx == nil {
		return strconv.FormatInt(time.Now().UnixNano(), 36)
	}
	if md, ok := metadata.FromIncomingContext(ctx); ok {
		if values := md.Get("x-request-id"); len(values) > 0 && values[0] != "" {
			return values[0]
		}
		if values := md.Get("x-trace-id"); len(values) > 0 && values[0] != "" {
			return values[0]
		}
	}
	return strconv.FormatInt(time.Now().UnixNano(), 36)
}

type amqpHeaderCarrier amqp091.Table

func (c amqpHeaderCarrier) Get(key string) string {
	value, ok := amqp091.Table(c)[key]
	if !ok {
		return ""
	}
	switch v := value.(type) {
	case string:
		return v
	case []byte:
		return string(v)
	default:
		return ""
	}
}

func (c amqpHeaderCarrier) Set(key, value string) {
	amqp091.Table(c)[key] = value
}

func (c amqpHeaderCarrier) Keys() []string {
	keys := make([]string, 0, len(c))
	for k := range amqp091.Table(c) {
		keys = append(keys, k)
	}
	return keys
}
