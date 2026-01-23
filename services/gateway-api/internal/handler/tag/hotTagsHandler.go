// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package tag

import (
	"net/http"

	"gateway-api/internal/logic/tag"
	"gateway-api/internal/svc"
	"github.com/zeromicro/go-zero/rest/httpx"
)

func HotTagsHandler(svcCtx *svc.ServiceContext) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		l := tag.NewHotTagsLogic(r.Context(), svcCtx)
		resp, err := l.HotTags()
		if err != nil {
			httpx.ErrorCtx(r.Context(), w, err)
		} else {
			httpx.OkJsonCtx(r.Context(), w, resp)
		}
	}
}
