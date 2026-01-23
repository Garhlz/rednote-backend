// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package common

import (
	"net/http"

	javaproxy "gateway-api/internal/handler/javaproxy"
	"gateway-api/internal/svc"
)

func UploadHandler(svcCtx *svc.ServiceContext) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		javaproxy.ProxyHandler(svcCtx)(w, r)
	}
}
