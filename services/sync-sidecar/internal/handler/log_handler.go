package handler

import (
	"context"
	"encoding/json"
	"log"
	"time"

	amqp "github.com/rabbitmq/amqp091-go"

	"sync-sidecar/internal/event"
	"sync-sidecar/internal/model"
	"sync-sidecar/internal/service"
)

type LogHandler struct {
	Infra *service.Infra
}

func (h *LogHandler) Handle(d amqp.Delivery) {
	var e event.LogEvent
	if err := json.Unmarshal(d.Body, &e); err != nil {
		log.Printf("Log JSON parse error: %v", err)
		d.Ack(false)
		return
	}

	coll := h.Infra.Mongo.Database("rednote").Collection("api_logs")

	// 处理时间解析, 尝试解析 Java 发来的 ISO 字符串
	createTime, err := time.Parse(time.RFC3339, e.CreatedAt)
	if err != nil || createTime.IsZero() {
		// 如果解析失败，使用配置的时区生成当前时间
		createTime = time.Now().In(h.Infra.Cfg.TimeLocation)
	} else {
		// 如果解析成功，确保它处于正确的时区（虽然 Mongo 存的是 UTC，但逻辑上保持一致是个好习惯）
		createTime = createTime.In(h.Infra.Cfg.TimeLocation)
	}

	doc := model.ApiLogDoc{
		TraceId: e.TraceId, LogType: e.LogType, Module: e.Module,
		BizId: e.BizId, UserId: e.UserId, Username: e.Username,
		Role: e.Role, Method: e.Method, Uri: e.Uri,
		Ip: e.Ip, Params: e.Params, Status: e.Status,
		TimeCost: e.TimeCost, Description: e.Description, ErrorMsg: e.ErrorMsg,
		CreatedAt: createTime, // Mongo 驱动会自动处理 time.Time
	}

	_, err = coll.InsertOne(context.Background(), doc)
	if err != nil {
		log.Printf("❌ [Log] Insert Error: %v", err)
	} else {
		// ✅ 增加成功日志 (仅打印关键信息，防止太长)
		log.Printf("📝 [Log] Saved: [%s] %s %s (Trace: %s)", e.LogType, e.Method, e.Uri, e.TraceId)
	}
	d.Ack(false)
}
