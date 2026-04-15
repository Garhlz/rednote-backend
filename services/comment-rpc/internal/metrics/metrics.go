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
	commentRequestsTotal = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name: "comment_rpc_requests_total",
			Help: "Total number of comment-rpc business requests.",
		},
		[]string{"action", "result"},
	)
	commentRequestDuration = prometheus.NewHistogramVec(
		prometheus.HistogramOpts{
			Name:    "comment_rpc_request_duration_seconds",
			Help:    "Duration of comment-rpc business requests.",
			Buckets: prometheus.DefBuckets,
		},
		[]string{"action"},
	)
)

func init() {
	prometheus.MustRegister(commentRequestsTotal, commentRequestDuration)
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
		logx.Infof("starting comment metrics server at %s%s", addr, path)
		if err := http.ListenAndServe(addr, mux); err != nil {
			logx.Errorf("comment metrics server stopped: %v", err)
		}
	}()
}

func ObserveRequest(action, result string, duration time.Duration) {
	commentRequestsTotal.WithLabelValues(action, result).Inc()
	commentRequestDuration.WithLabelValues(action).Observe(duration.Seconds())
}
