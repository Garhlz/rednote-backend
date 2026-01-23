// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package user

import (
	"net/http"

	"gateway-api/internal/logic/user"
	"gateway-api/internal/svc"
	"github.com/zeromicro/go-zero/rest/httpx"
)

func GetMyCollectsHandler(svcCtx *svc.ServiceContext) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		l := user.NewGetMyCollectsLogic(r.Context(), svcCtx)
		resp, err := l.GetMyCollects()
		if err != nil {
			httpx.ErrorCtx(r.Context(), w, err)
		} else {
			httpx.OkJsonCtx(r.Context(), w, resp)
		}
	}
}
