// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package admin

import (
	"net/http"

	"gateway-api/internal/logic/admin"
	"gateway-api/internal/svc"
	"gateway-api/internal/types"
	"github.com/zeromicro/go-zero/rest/httpx"
)

func AdminPostAuditHandler(svcCtx *svc.ServiceContext) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var req types.IdPath
		if err := httpx.Parse(r, &req); err != nil {
			httpx.ErrorCtx(r.Context(), w, err)
			return
		}

		l := admin.NewAdminPostAuditLogic(r.Context(), svcCtx)
		resp, err := l.AdminPostAudit(&req)
		if err != nil {
			httpx.ErrorCtx(r.Context(), w, err)
		} else {
			httpx.OkJsonCtx(r.Context(), w, resp)
		}
	}
}
