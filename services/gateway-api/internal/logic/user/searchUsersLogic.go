// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package user

import (
	"context"
	"encoding/json"
	"net/http"
	"net/url"
	"strconv"
	"strings"

	"gateway-api/internal/pkg/ctxutil"
	"gateway-api/internal/response"
	"gateway-api/internal/svc"
	"gateway-api/internal/types"

	"github.com/zeromicro/go-zero/core/logx"
)

type SearchUsersLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewSearchUsersLogic(ctx context.Context, svcCtx *svc.ServiceContext) *SearchUsersLogic {
	return &SearchUsersLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

type javaEnvelope[T any] struct {
	Code    int    `json:"code"`
	Message string `json:"message"`
	Data    T      `json:"data"`
}

func (l *SearchUsersLogic) SearchUsers(req *types.UserSearchReq) (resp []types.UserSearchVO, err error) {
	keyword := strings.TrimSpace(req.Keyword)
	if keyword == "" {
		return []types.UserSearchVO{}, nil
	}

	javaResp, err := l.svcCtx.ProxyToJava(l.ctx, svc.JavaProxyRequest{
		Method: http.MethodGet,
		Path:   "/api/user/search",
		Query: url.Values{
			"keyword": []string{keyword},
		},
		Headers: buildJavaHeaders(l.ctx),
	})
	if err != nil {
		return nil, err
	}

	var envelope javaEnvelope[[]types.UserSearchVO]
	if err := json.Unmarshal(javaResp.Body, &envelope); err != nil {
		return nil, err
	}
	if javaResp.StatusCode != http.StatusOK || envelope.Code != 200 {
		return nil, response.NewError(envelope.Code, envelope.Message, javaResp.StatusCode)
	}

	if envelope.Data == nil {
		return []types.UserSearchVO{}, nil
	}

	return envelope.Data, nil
}

func buildJavaHeaders(ctx context.Context) map[string]string {
	headers := map[string]string{}
	if token := ctxutil.AuthToken(ctx); token != "" {
		headers["Authorization"] = "Bearer " + token
	}
	if userID := ctxutil.UserID(ctx); userID > 0 {
		headers["X-User-Id"] = strconv.FormatInt(userID, 10)
	}
	if role := ctxutil.Role(ctx); role != "" {
		headers["X-User-Role"] = role
	}
	if nickname := ctxutil.Nickname(ctx); nickname != "" {
		headers["X-User-Nickname"] = nickname
	}
	return headers
}
