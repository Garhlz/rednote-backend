package service

import (
	"log"
	"sync"

	amqp "github.com/rabbitmq/amqp091-go"
)

// ConsumerHandler 是具体的业务处理函数签名
type ConsumerHandler func(d amqp.Delivery)

// StartConsumerGroup 启动一组 Worker 来监听指定队列
func (i *Infra) StartConsumerGroup(wg *sync.WaitGroup, queueName string, handler ConsumerHandler) {
	ch, err := i.RMQ.Channel()
	if err != nil {
		log.Fatalf("Failed to open channel for %s: %v", queueName, err)
	}

	// 声明队列 (幂等操作，防止队列不存在)
	args := amqp.Table{"x-dead-letter-exchange": "platform.dlx.exchange"}
	_, err = ch.QueueDeclare(queueName, true, false, false, false, args)
	if err != nil {
		log.Fatalf("Queue declare error: %v", err)
	}

	// QoS: 这里的 WorkerCount 是全局配置，Prefetch 建议是 Worker 数的 2 倍
	err = ch.Qos(i.Cfg.WorkerCount*2, 0, false)
	if err != nil {
		log.Fatal("Qos error:", err)
	}

	msgs, err := ch.Consume(queueName, "", false, false, false, false, nil)
	if err != nil {
		log.Fatal("Consume error:", err)
	}

	log.Printf("🔥 Consumer Group Started: [%s] with %d workers", queueName, i.Cfg.WorkerCount)

	// 启动 Worker Pool
	for k := 0; k < i.Cfg.WorkerCount; k++ {
		wg.Add(1)
		go func(workerId int) {
			defer wg.Done()
			for d := range msgs {
				// 兜底 Panic，防止单个消息搞挂 Worker
				func() {
					defer func() {
						if r := recover(); r != nil {
							log.Printf("⚠️ [%s] Panic recovered: %v", queueName, r)
							d.Nack(false, false) // 丢入死信
						}
					}()
					handler(d)
				}()
			}
		}(k)
	}
}
