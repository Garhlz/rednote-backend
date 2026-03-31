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
	searchRequestsTotal = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name: "search_rpc_requests_total",
			Help: "Total number of search-rpc business requests.",
		},
		[]string{"action", "result"},
	)
	searchRequestDuration = prometheus.NewHistogramVec(
		prometheus.HistogramOpts{
			Name:    "search_rpc_request_duration_seconds",
			Help:    "Duration of search-rpc business requests.",
			Buckets: prometheus.DefBuckets,
		},
		[]string{"action"},
	)
	esQueryDuration = prometheus.NewHistogramVec(
		prometheus.HistogramOpts{
			Name:    "search_es_query_duration_seconds",
			Help:    "Duration of Elasticsearch queries issued by search-rpc.",
			Buckets: prometheus.DefBuckets,
		},
		[]string{"action"},
	)
)

func init() {
	prometheus.MustRegister(
		searchRequestsTotal,
		searchRequestDuration,
		esQueryDuration,
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
		logx.Infof("starting search metrics server at %s%s", addr, path)
		if err := http.ListenAndServe(addr, mux); err != nil {
			logx.Errorf("search metrics server stopped: %v", err)
		}
	}()
}

func ObserveRequest(action, result string, duration time.Duration) {
	searchRequestsTotal.WithLabelValues(action, result).Inc()
	searchRequestDuration.WithLabelValues(action).Observe(duration.Seconds())
}

func ObserveESQuery(action string, duration time.Duration) {
	esQueryDuration.WithLabelValues(action).Observe(duration.Seconds())
}
