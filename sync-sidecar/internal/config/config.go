package config

import (
	"os"
	"time"
)

type AppConfig struct {
	RabbitMQURL  string
	MongoURI     string
	ESAddress    string
	IndexName    string
	AuditEnable  bool
	WorkerCount  int // 每个队列的并发数
	TimeLocation *time.Location

	// 队列名称配置 (与 Java RabbitConfig 保持一致)
	QueueLog    string
	QueueSearch string
	QueueEsSync string
	QueueUser   string
}

func LoadConfig() *AppConfig {
	loc, err := time.LoadLocation("Asia/Shanghai")
	if err != nil {
		loc = time.FixedZone("CST", 8*3600)
	}

	return &AppConfig{
		RabbitMQURL:  getEnv("RABBITMQ_URL", "amqp://admin:admin@rabbitmq:5672/"),
		MongoURI:     getEnv("MONGO_URI", "mongodb://mongo:27017"),
		ESAddress:    getEnv("ES_ADDRESS", "http://elasticsearch:9200"),
		IndexName:    "post_index",
		AuditEnable:  getEnv("POST_AUDIT_ENABLE", "false") == "true",
		WorkerCount:  20,
		TimeLocation: loc,

		// 队列名硬编码以匹配 Java 配置
		QueueLog:    "platform.log.queue",
		QueueSearch: "platform.search.queue",
		QueueEsSync: "platform.es.sync.queue",
		QueueUser:   "platform.user.queue",
	}
}

func getEnv(key, fallback string) string {
	if value, ok := os.LookupEnv(key); ok {
		return value
	}
	return fallback
}
