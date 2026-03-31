package middleware

import (
	"net/http"
	"time"

	appmetrics "gateway-api/internal/metrics"
)

// metricsResponseWriter 用来截获最终写回客户端的 HTTP status code。
// 因为标准的 ResponseWriter 默认不会把状态码暴露出来，所以要包一层。
type metricsResponseWriter struct {
	http.ResponseWriter
	status int
}

func (w *metricsResponseWriter) WriteHeader(code int) {
	w.status = code
	w.ResponseWriter.WriteHeader(code)
}

func NewMetricsMiddleware() func(http.HandlerFunc) http.HandlerFunc {
	return func(next http.HandlerFunc) http.HandlerFunc {
		return func(w http.ResponseWriter, r *http.Request) {
			// 这层 middleware 统计的是“网关入口视角”的总请求耗时。
			// 不区分后面是走 Java 代理、Go RPC，还是网关自己聚合。
			start := time.Now()
			mrw := &metricsResponseWriter{
				ResponseWriter: w,
				status:         http.StatusOK,
			}
			next.ServeHTTP(mrw, r)
			// 请求真正结束后再统一打点，这样拿到的是完整链路耗时和最终状态码。
			appmetrics.ObserveHTTPRequest(r.Method, r.URL.Path, mrw.status, time.Since(start))
		}
	}
}
