package admin

import (
	"net/http"

	"gateway-api/internal/handler/javaproxy"
	"gateway-api/internal/svc"
)

func AdminTopViewsHandler(svcCtx *svc.ServiceContext) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		javaproxy.ProxyHandler(svcCtx)(w, r)
	}
}
