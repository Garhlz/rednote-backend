package main

import (
	"log"
	"os"
	"os/signal"
	"sync"
	"syscall"

	"sync-sidecar/internal/config"
	"sync-sidecar/internal/handler"
	"sync-sidecar/internal/service"
)

func main() {
	// 1. 加载配置
	cfg := config.LoadConfig()
	log.Println("🚀 Go Sidecar Starting...")

	// 2. 初始化基础设施 (Infra)
	infra := service.InitInfra(cfg)
	defer infra.Close()

	// 3. 初始化 Handlers
	logHandler := &handler.LogHandler{Infra: infra}
	searchHandler := &handler.SearchHandler{Infra: infra}
	syncHandler := &handler.SyncHandler{Infra: infra}
	userHandler := &handler.UserHandler{Infra: infra}

	// 4. 启动多队列消费者
	var wg sync.WaitGroup

	// Group 1: 日志
	infra.StartConsumerGroup(&wg, cfg.QueueLog, logHandler.Handle)

	// Group 2: 搜索历史
	infra.StartConsumerGroup(&wg, cfg.QueueSearch, searchHandler.Handle)

	// Group 3: ES 同步 (只负责 ES)
	infra.StartConsumerGroup(&wg, cfg.QueueEsSync, syncHandler.Handle)

	// Group 4: 用户数据同步 (只负责 Mongo) 【新增】
	// 监听 platform.user.queue
	infra.StartConsumerGroup(&wg, cfg.QueueUser, userHandler.Handle)

	// 5. 优雅停机
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)
	<-sigChan

	log.Println("⚠️ Shutting down sidecar...")
	// 注意：rabbitmq connection 关闭会导致 channel 关闭，从而让 worker loop 退出
	infra.Close()
	wg.Wait()
	log.Println("✅ Sidecar stopped gracefully.")
}
