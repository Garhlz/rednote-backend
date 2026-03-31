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

// 这一层负责两件事：
// 1. 定义 gateway-api 需要暴露给 Prometheus 的指标
// 2. 单独起一个轻量 HTTP server 暴露 /metrics
//
// 这里特意没有把 metrics 路由挂到业务路由树里，而是走独立端口。
// 这样可以避免和 go-zero 的业务路由、鉴权中间件、统一响应包装耦合在一起。
var (
	// 网关总请求量：适合看 QPS、状态码分布、错误比例。
	httpRequestsTotal = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name: "gateway_http_requests_total",
			Help: "Total number of HTTP requests handled by gateway-api.",
		},
		[]string{"method", "path", "status"},
	)
	httpRequestDuration = prometheus.NewHistogramVec(
		prometheus.HistogramOpts{
			Name:    "gateway_http_request_duration_seconds",
			Help:    "HTTP request duration in seconds for gateway-api.",
			Buckets: prometheus.DefBuckets,
		},
		[]string{"method", "path"},
	)
	// Java 代理请求量：适合观察还有多少流量在走 platform-java。
	javaProxyRequestsTotal = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name: "gateway_java_proxy_requests_total",
			Help: "Total number of requests proxied from gateway-api to platform-java.",
		},
		[]string{"method", "path", "status"},
	)
	javaProxyErrorsTotal = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name: "gateway_java_proxy_errors_total",
			Help: "Total number of proxy failures when gateway-api calls platform-java.",
		},
		[]string{"method", "path", "kind"},
	)
	// Java 代理耗时：用于观察 gateway -> Java 这条链的延迟情况。
	javaProxyDuration = prometheus.NewHistogramVec(
		prometheus.HistogramOpts{
			Name:    "gateway_java_proxy_duration_seconds",
			Help:    "Duration in seconds of requests proxied from gateway-api to platform-java.",
			Buckets: prometheus.DefBuckets,
		},
		[]string{"method", "path"},
	)
)

func init() {
	// 指标在进程启动时一次性注册到默认 registry。
	prometheus.MustRegister(
		httpRequestsTotal,
		httpRequestDuration,
		javaProxyRequestsTotal,
		javaProxyErrorsTotal,
		javaProxyDuration,
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
		// metrics server 只负责暴露 Prometheus 文本格式，不承载任何业务流量。
		mux := http.NewServeMux()
		mux.Handle(path, promhttp.Handler())
		logx.Infof("starting gateway metrics server at %s%s", addr, path)
		if err := http.ListenAndServe(addr, mux); err != nil {
			logx.Errorf("gateway metrics server stopped: %v", err)
		}
	}()
}

func ObserveHTTPRequest(method, path string, status int, duration time.Duration) {
	// 业务 path 先做归一化，避免把 /api/post/123、/api/post/456 这种具体 ID
	// 直接打进 label，导致 Prometheus label 基数爆炸。
	normalized := NormalizePath(path)
	statusLabel := fmt.Sprintf("%d", status)
	httpRequestsTotal.WithLabelValues(method, normalized, statusLabel).Inc()
	httpRequestDuration.WithLabelValues(method, normalized).Observe(duration.Seconds())
}

func ObserveJavaProxy(method, path string, status int, duration time.Duration) {
	// 这组指标只统计 gateway 代理到 Java 的请求，不包含走 Go RPC 的链路。
	normalized := NormalizePath(path)
	statusLabel := fmt.Sprintf("%d", status)
	javaProxyRequestsTotal.WithLabelValues(method, normalized, statusLabel).Inc()
	javaProxyDuration.WithLabelValues(method, normalized).Observe(duration.Seconds())
}

func IncJavaProxyError(method, path, kind string) {
	javaProxyErrorsTotal.WithLabelValues(method, NormalizePath(path), kind).Inc()
}

func NormalizePath(path string) string {
	// Prometheus label 不适合直接放无限变化的原始路径。
	// 这里用一个轻量归一化策略，把明显像 ID 的路径段压成 :id。
	if path == "" {
		return "/"
	}
	parts := strings.Split(path, "/")
	for i, part := range parts {
		if part == "" {
			continue
		}
		if looksLikeIdentifier(part) {
			parts[i] = ":id"
		}
	}
	normalized := strings.Join(parts, "/")
	if normalized == "" {
		return "/"
	}
	return normalized
}

func looksLikeIdentifier(part string) bool {
	if len(part) >= 24 && isHex(part) {
		return true
	}
	allDigits := true
	for _, ch := range part {
		if ch < '0' || ch > '9' {
			allDigits = false
			break
		}
	}
	return allDigits
}

func isHex(s string) bool {
	for _, ch := range s {
		if (ch < '0' || ch > '9') &&
			(ch < 'a' || ch > 'f') &&
			(ch < 'A' || ch > 'F') {
			return false
		}
	}
	return true
}
