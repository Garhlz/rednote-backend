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
	authRequestsTotal = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name: "user_auth_requests_total",
			Help: "Total number of key auth requests handled by user-rpc.",
		},
		[]string{"action", "result"},
	)
	authRequestDuration = prometheus.NewHistogramVec(
		prometheus.HistogramOpts{
			Name:    "user_auth_request_duration_seconds",
			Help:    "Duration of key auth requests handled by user-rpc.",
			Buckets: prometheus.DefBuckets,
		},
		[]string{"action"},
	)
	emailCodeRequestsTotal = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name: "user_email_code_requests_total",
			Help: "Total number of email code operations handled by user-rpc.",
		},
		[]string{"scene", "result"},
	)
)

func init() {
	prometheus.MustRegister(
		authRequestsTotal,
		authRequestDuration,
		emailCodeRequestsTotal,
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
		mux := http.NewServeMux()
		mux.Handle(path, promhttp.Handler())
		logx.Infof("starting user metrics server at %s%s", addr, path)
		if err := http.ListenAndServe(addr, mux); err != nil {
			logx.Errorf("user metrics server stopped: %v", err)
		}
	}()
}

func ObserveAuth(action, result string, duration time.Duration) {
	authRequestsTotal.WithLabelValues(action, result).Inc()
	authRequestDuration.WithLabelValues(action).Observe(duration.Seconds())
}

func IncEmailCode(scene, result string) {
	emailCodeRequestsTotal.WithLabelValues(scene, result).Inc()
}
