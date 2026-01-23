package mq

import (
	"context"
	"encoding/json"
	"errors"
	"strconv"
	"strings"

	"user-rpc/internal/config"

	amqp "github.com/rabbitmq/amqp091-go"
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

	return p.channel.PublishWithContext(ctx, p.exchange, routingKey, false, false, amqp.Publishing{
		ContentType: "application/json",
		Body:        body,
		Headers:     headers,
	})
}
