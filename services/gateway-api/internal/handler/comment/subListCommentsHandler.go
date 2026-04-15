// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package comment

import (
	"net/http"

	"gateway-api/internal/logic/comment"
	"gateway-api/internal/svc"
	"gateway-api/internal/types"
	"github.com/zeromicro/go-zero/rest/httpx"
)

func SubListCommentsHandler(svcCtx *svc.ServiceContext) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var req types.CommentSubListReq
		if err := httpx.Parse(r, &req); err != nil {
			httpx.ErrorCtx(r.Context(), w, err)
			return
		}
		l := comment.NewSubListCommentsLogic(r.Context(), svcCtx)
		resp, err := l.SubListComments(&req)
		if err != nil {
			httpx.ErrorCtx(r.Context(), w, err)
		} else {
			httpx.OkJsonCtx(r.Context(), w, resp)
		}
	}
}
