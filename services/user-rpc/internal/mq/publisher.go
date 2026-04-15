package mq

import (
	"context"
	"encoding/json"
	"errors"
	"strconv"
	"strings"
	"time"

	"user-rpc/internal/config"

	amqp "github.com/rabbitmq/amqp091-go"
	"github.com/zeromicro/go-zero/core/logx"
	"go.opentelemetry.io/otel"
	"google.golang.org/grpc/metadata"
)

const typeIdHeader = "__TypeId__"

type Publisher struct {
	enabled    bool
	conn       *amqp.Connection
	channel    *amqp.Channel
	exchange   string
	routingKey string
}

func NewPublisher(cfg config.Config) (*Publisher, error) {
	enabled := true
	if strings.TrimSpace(cfg.RabbitMQ.Enabled) != "" {
		val, err := strconv.ParseBool(strings.TrimSpace(cfg.RabbitMQ.Enabled))
		if err != nil {
			return nil, err
		}
		enabled = val
	}
	if !enabled || cfg.RabbitMQ.Url == "" {
		return &Publisher{enabled: false}, nil
	}

	conn, err := amqp.Dial(cfg.RabbitMQ.Url)
	if err != nil {
		return nil, err
	}

	ch, err := conn.Channel()
	if err != nil {
		_ = conn.Close()
		return nil, err
	}

	exchange := cfg.RabbitMQ.Exchange
	if exchange == "" {
		exchange = "platform.topic.exchange"
	}

	if err := ch.ExchangeDeclare(
		exchange,
		"topic",
		true,
		false,
		false,
		false,
		nil,
	); err != nil {
		_ = ch.Close()
		_ = conn.Close()
		return nil, err
	}

	return &Publisher{
		enabled:    true,
		conn:       conn,
		channel:    ch,
		exchange:   exchange,
		routingKey: cfg.RabbitMQ.UserUpdateRoutingKey,
	}, nil
}

func (p *Publisher) Close() error {
	if !p.enabled {
		return nil
	}
	if p.channel != nil {
		_ = p.channel.Close()
	}
	if p.conn != nil {
		return p.conn.Close()
	}
	return nil
}

func (p *Publisher) Publish(ctx context.Context, routingKey string, typeId string, payload any) error {
	if !p.enabled {
		return nil
	}
	if routingKey == "" {
		return errors.New("routing key required")
	}

	body, err := json.Marshal(payload)
	if err != nil {
		return err
	}

	headers := amqp.Table{}
	if typeId != "" {
		headers[typeIdHeader] = typeId
	}
	requestID := requestIDFromContext(ctx)
	headers["X-Request-Id"] = requestID
	headers["X-Trace-Id"] = requestID
	headers["X-Service"] = "user-rpc"
	headers["X-Routing-Key"] = routingKey
	otel.GetTextMapPropagator().Inject(ctx, amqpHeaderCarrier(headers))

	err = p.channel.PublishWithContext(ctx, p.exchange, routingKey, false, false, amqp.Publishing{
		ContentType: "application/json",
		Body:        body,
		Headers:     headers,
	})
	if err != nil {
		logx.WithContext(ctx).Errorf("mq publish failed routingKey=%s requestId=%s err=%v", routingKey, requestID, err)
		return err
	}

	logx.WithContext(ctx).Infof("mq publish success routingKey=%s requestId=%s", routingKey, requestID)
	return nil
}

func requestIDFromContext(ctx context.Context) string {
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

type amqpHeaderCarrier amqp.Table

func (c amqpHeaderCarrier) Get(key string) string {
	value, ok := amqp.Table(c)[key]
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
	amqp.Table(c)[key] = value
}

func (c amqpHeaderCarrier) Keys() []string {
	keys := make([]string, 0, len(c))
	for k := range amqp.Table(c) {
		keys = append(keys, k)
	}
	return keys
}
