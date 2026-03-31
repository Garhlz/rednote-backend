package metrics

import (
	"fmt"
	"net/http"
	"strings"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	"github.com/zeromicro/go-zero/core/logx"
)

// interaction-rpc 的指标重点不是“每个 RPC 调了多少次”，
// 而是这条 Redis 互动读模型链路是否健康。
// 所以这里优先暴露：
// 1. 缓存预热次数与耗时
// 2. Bloom 检查次数
// 3. Dummy 占位节点操作次数
// 4. MQ 发布成功/失败情况
var (
	// 缓存预热次数，result 用来区分命中 Redis、回源 Mongo、写 Redis 出错等不同结果。
	cacheWarmTotal = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name: "interaction_cache_warm_total",
			Help: "Total number of cache warmup attempts in interaction-rpc.",
		},
		[]string{"kind", "result"},
	)
	cacheWarmDuration = prometheus.NewHistogramVec(
		prometheus.HistogramOpts{
			Name:    "interaction_cache_warm_duration_seconds",
			Help:    "Duration of cache warmup in interaction-rpc.",
			Buckets: prometheus.DefBuckets,
		},
		[]string{"kind"},
	)
	// Bloom 检查次数，用于看快速判定层是否工作正常。
	bloomChecksTotal = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name: "interaction_bloom_checks_total",
			Help: "Total number of bloom filter checks in interaction-rpc.",
		},
		[]string{"kind", "result"},
	)
	dummyOpsTotal = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name: "interaction_dummy_ops_total",
			Help: "Total number of dummy placeholder operations in interaction-rpc.",
		},
		[]string{"kind", "action"},
	)
	// MQ 发布结果，用于观察 Redis 实时写成功后，异步事件传播是否稳定。
	mqPublishTotal = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name: "interaction_mq_publish_total",
			Help: "Total number of MQ publish attempts in interaction-rpc.",
		},
		[]string{"routing_key", "result"},
	)
)

func init() {
	prometheus.MustRegister(
		cacheWarmTotal,
		cacheWarmDuration,
		bloomChecksTotal,
		dummyOpsTotal,
		mqPublishTotal,
	)
}

func StartServer(host string, port int, path string) {
	if port <= 0 {
		return
	}
	if strings.TrimSpace(path) == "" {
		path = "/metrics"
	}
	addr := fmt.Sprintf("%s:%d", host, port)
	go func() {
		// 和 gateway 一样，interaction-rpc 也单独暴露 metrics 服务，
		// 避免和 zRPC 主服务端口耦合。
		mux := http.NewServeMux()
		mux.Handle(path, promhttp.Handler())
		logx.Infof("starting interaction metrics server at %s%s", addr, path)
		if err := http.ListenAndServe(addr, mux); err != nil {
			logx.Errorf("interaction metrics server stopped: %v", err)
		}
	}()
}

func ObserveCacheWarm(kind, result string, duration time.Duration) {
	// 一次预热既记次数，也记耗时。
	cacheWarmTotal.WithLabelValues(kind, result).Inc()
	cacheWarmDuration.WithLabelValues(kind).Observe(duration.Seconds())
}

func IncBloomCheck(kind, result string) {
	bloomChecksTotal.WithLabelValues(kind, result).Inc()
}

func IncDummyOp(kind, action string) {
	dummyOpsTotal.WithLabelValues(kind, action).Inc()
}

func IncMQPublish(routingKey, result string) {
	mqPublishTotal.WithLabelValues(routingKey, result).Inc()
}
