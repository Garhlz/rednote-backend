package service

import (
	"log"
	"sync"

	amqp "github.com/rabbitmq/amqp091-go"
)

// ConsumerHandler 是具体的业务处理函数签名
type ConsumerHandler func(d amqp.Delivery)

// StartConsumerGroup 启动一组 Worker 来监听指定队列，并自动完成交换机声明与绑定
func (i *Infra) StartConsumerGroup(wg *sync.WaitGroup, queueName string, handler ConsumerHandler) {
	ch, err := i.RMQ.Channel()
	if err != nil {
		log.Fatalf("Failed to open channel for %s: %v", queueName, err)
	}

	// 1. 声明业务交换机 (Topic 类型，持久化)
	// 对应 Java 中的 platformExchange()
	err = ch.ExchangeDeclare(
		i.Cfg.ExchangeMain, // "platform.topic.exchange"
		"topic",            // type
		true,               // durable
		false,              // auto-deleted
		false,              // internal
		false,              // no-wait
		nil,                // arguments
	)
	if err != nil {
		log.Fatalf("Failed to declare main exchange: %v", err)
	}

	// 2. 声明死信交换机 (Topic 类型，持久化)
	// 对应 Java 中的 dlxExchange()
	err = ch.ExchangeDeclare(
		i.Cfg.ExchangeDLX, // "platform.dlx.exchange"
		"topic",           // type
		true,              // durable
		false,             // auto-deleted
		false,             // internal
		false,             // no-wait
		nil,               // arguments
	)
	if err != nil {
		log.Fatalf("Failed to declare DLX exchange: %v", err)
	}

	// 3. 声明队列并绑定死信交换机
	// 对应 Java 中的 createQueueWithDlq()
	args := amqp.Table{
		"x-dead-letter-exchange": i.Cfg.ExchangeDLX,
	}
	_, err = ch.QueueDeclare(
		queueName, // 队列名
		true,      // durable
		false,     // delete when unused
		false,     // exclusive
		false,     // no-wait
		args,      // 指定死信交换机参数
	)
	if err != nil {
		log.Fatalf("Queue declare error for %s: %v", queueName, err)
	}

	// 4. 根据配置自动执行绑定 (Binding)
	// 解决你担心的“没有绑定关系”问题
	keys := i.Cfg.QueueBindings[queueName]
	for _, key := range keys {
		err = ch.QueueBind(
			queueName,          // 队列名
			key,                // 路由键 (如 "post.#")
			i.Cfg.ExchangeMain, // 交换机名
			false,              // no-wait
			nil,
		)
		if err != nil {
			log.Fatalf("Failed to bind queue %s with key %s: %v", queueName, key, err)
		}
		log.Printf("🔗 Bound: %s --(%s)--> %s", i.Cfg.ExchangeMain, key, queueName)
	}

	// 5. 设置 QoS (公平派遣)
	err = ch.Qos(i.Cfg.WorkerCount*2, 0, false)
	if err != nil {
		log.Fatal("Qos error:", err)
	}

	// 6. 开启消费
	msgs, err := ch.Consume(
		queueName,
		"",    // consumer tag
		false, // auto-ack (这里设为 false，由 Handler 手动 Ack)
		false, // exclusive
		false, // no-local
		false, // no-wait
		nil,   // args
	)
	if err != nil {
		log.Fatal("Consume error:", err)
	}

	log.Printf("🔥 Consumer Group Started: [%s] with %d workers", queueName, i.Cfg.WorkerCount)

	// 7. 启动 Worker Pool
	for k := 0; k < i.Cfg.WorkerCount; k++ {
		wg.Add(1)
		go func(workerId int) {
			defer wg.Done()
			for d := range msgs {
				// 兜底 Panic，防止单个消息搞挂整个进程
				func() {
					defer func() {
						if r := recover(); r != nil {
							log.Printf("⚠️ [%s] Worker %d Panic recovered: %v", queueName, workerId, r)
							// 发生严重错误时，将消息拒绝并丢入死信
							d.Nack(false, false)
						}
					}()
					handler(d)
				}()
			}
		}(k)
	}
}
