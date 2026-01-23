// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package message

import (
	"net/http"

	"gateway-api/internal/logic/message"
	"gateway-api/internal/svc"
	"github.com/zeromicro/go-zero/rest/httpx"
)

func UnreadCountHandler(svcCtx *svc.ServiceContext) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		l := message.NewUnreadCountLogic(r.Context(), svcCtx)
		resp, err := l.UnreadCount()
		if err != nil {
			httpx.ErrorCtx(r.Context(), w, err)
		} else {
			httpx.OkJsonCtx(r.Context(), w, resp)
		}
	}
}
