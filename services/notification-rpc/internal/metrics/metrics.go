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

var (
	// 当前通知服务先暴露最有用的一层指标：
	// 每类业务动作的请求量和耗时。
	// 这里不引入高基数标签，避免 Prometheus 被 userId / targetId 这类业务字段打爆。
	notificationRequestsTotal = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name: "notification_rpc_requests_total",
			Help: "Total number of notification-rpc business requests.",
		},
		[]string{"action", "result"},
	)
	notificationRequestDuration = prometheus.NewHistogramVec(
		prometheus.HistogramOpts{
			Name:    "notification_rpc_request_duration_seconds",
			Help:    "Duration of notification-rpc business requests.",
			Buckets: prometheus.DefBuckets,
		},
		[]string{"action"},
	)
)

func init() {
	prometheus.MustRegister(
		notificationRequestsTotal,
		notificationRequestDuration,
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
		// metrics 服务独立暴露，和 zRPC 主端口解耦。
		mux := http.NewServeMux()
		mux.Handle(path, promhttp.Handler())
		logx.Infof("starting notification metrics server at %s%s", addr, path)
		if err := http.ListenAndServe(addr, mux); err != nil {
			logx.Errorf("notification metrics server stopped: %v", err)
		}
	}()
}

// ObserveRequest 统一记录 notification-rpc 各业务动作的次数与耗时。
// action 表示业务动作，result 表示结果归因，例如 success / mongo_error。
func ObserveRequest(action, result string, duration time.Duration) {
	notificationRequestsTotal.WithLabelValues(action, result).Inc()
	notificationRequestDuration.WithLabelValues(action).Observe(duration.Seconds())
}
