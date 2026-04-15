// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package post

import (
	"net/http"
	"strings"

	"gateway-api/internal/handler/javaproxy"
	"gateway-api/internal/logic/post"
	"gateway-api/internal/svc"
	"gateway-api/internal/types"
	"github.com/zeromicro/go-zero/rest/httpx"
)

func ListPostsHandler(svcCtx *svc.ServiceContext) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		// follow 流仍然复用 Java 侧原实现：它本质是关系链时间流，不是搜索/推荐流。
		if strings.EqualFold(r.URL.Query().Get("tab"), "follow") {
			javaproxy.ProxyHandler(svcCtx)(w, r)
			return
		}

		var req types.SearchReq
		if err := httpx.Parse(r, &req); err != nil {
			httpx.ErrorCtx(r.Context(), w, err)
			return
		}
		l := post.NewListPostsLogic(r.Context(), svcCtx)
		resp, err := l.ListPosts(&req)
		if err != nil {
			httpx.ErrorCtx(r.Context(), w, err)
		} else {
			httpx.OkJsonCtx(r.Context(), w, resp)
		}
	}
}
