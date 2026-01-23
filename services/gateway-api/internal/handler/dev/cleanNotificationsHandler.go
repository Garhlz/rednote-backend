// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package dev

import (
	"net/http"

	"gateway-api/internal/logic/dev"
	"gateway-api/internal/svc"
	"github.com/zeromicro/go-zero/rest/httpx"
)

func CleanNotificationsHandler(svcCtx *svc.ServiceContext) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		l := dev.NewCleanNotificationsLogic(r.Context(), svcCtx)
		resp, err := l.CleanNotifications()
		if err != nil {
			httpx.ErrorCtx(r.Context(), w, err)
		} else {
			httpx.OkJsonCtx(r.Context(), w, resp)
		}
	}
}
