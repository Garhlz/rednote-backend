// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package interaction

import (
	"net/http"

	"gateway-api/internal/logic/interaction"
	"gateway-api/internal/svc"
	"gateway-api/internal/types"
	"github.com/zeromicro/go-zero/rest/httpx"
)

func UnlikePostHandler(svcCtx *svc.ServiceContext) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var req types.InteractionReq
		if err := httpx.Parse(r, &req); err != nil {
			httpx.ErrorCtx(r.Context(), w, err)
			return
		}

		l := interaction.NewUnlikePostLogic(r.Context(), svcCtx)
		resp, err := l.UnlikePost(&req)
		if err != nil {
			httpx.ErrorCtx(r.Context(), w, err)
		} else {
			httpx.OkJsonCtx(r.Context(), w, resp)
		}
	}
}
