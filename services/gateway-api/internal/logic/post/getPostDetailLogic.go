// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package post

import (
	"context"
	"encoding/json"
	"net/http"
	"strconv"

	"gateway-api/internal/pkg/ctxutil"
	"gateway-api/internal/response"
	"gateway-api/internal/svc"
	"gateway-api/internal/types"

	"github.com/zeromicro/go-zero/core/logx"
	"interaction-rpc/interactionservice"
)

type GetPostDetailLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewGetPostDetailLogic(ctx context.Context, svcCtx *svc.ServiceContext) *GetPostDetailLogic {
	return &GetPostDetailLogic{
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

func (l *GetPostDetailLogic) GetPostDetail(req *types.PostIdPath) (resp *types.PostVO, err error) {
	// 详情页的处理方式和列表/搜索略有不同：
	// 1. 帖子正文、图片、作者基础信息先从 Java 获取
	// 2. 点赞/收藏/关注/评分等互动字段再由 interaction-rpc 覆盖
	// 这样详情展示字段就被拆成：
	// - Java: 帖子主体内容
	// - interaction-rpc: 实时互动视图
	javaResp, err := l.svcCtx.ProxyToJava(l.ctx, svc.JavaProxyRequest{
		Method:  http.MethodGet,
		Path:    "/api/post/" + req.PostId,
		Headers: buildJavaHeaders(l.ctx),
	})
	if err != nil {
		return nil, err
	}

	var envelope javaEnvelope[types.PostVO]
	if err := json.Unmarshal(javaResp.Body, &envelope); err != nil {
		return nil, err
	}
	if javaResp.StatusCode != http.StatusOK || envelope.Code != 200 {
		return nil, response.NewError(envelope.Code, envelope.Message, javaResp.StatusCode)
	}

	post := envelope.Data

	// interaction-rpc 的 BatchPostStats 需要知道作者是谁，才能顺带补 isFollowed。
	postAuthorMap := map[string]int64{}
	if authorID, parseErr := strconv.ParseInt(post.Author.UserId, 10, 64); parseErr == nil && authorID > 0 {
		postAuthorMap[post.Id] = authorID
	}

	statsResp, statErr := interactionservice.NewInteractionService(l.svcCtx.InteractionRpc).BatchPostStats(l.ctx, &interactionservice.BatchPostStatsRequest{
		UserId:        ctxutil.UserID(l.ctx),
		PostIds:       []string{post.Id},
		PostAuthorMap: postAuthorMap,
	})
	if statErr != nil {
		// 这里故意做成“互动补充失败不影响主体详情返回”。
		// 也就是说，网关把 Java 返回的帖子详情当成主结果，把互动补充视为增强信息。
		l.Logger.Errorf("batch post stats failed: %v", statErr)
		return &post, nil
	}

	if stat := statsResp.GetStats()[post.Id]; stat != nil {
		// 用 interaction-rpc 的实时互动数据覆盖 Java 返回中的对应字段。
		post.LikeCount = stat.GetLikeCount()
		post.CollectCount = stat.GetCollectCount()
		post.IsLiked = stat.GetIsLiked()
		post.IsCollected = stat.GetIsCollected()
		post.IsFollowed = stat.GetIsFollowed()
		post.RatingAverage = stat.GetRatingAverage()
		post.RatingCount = stat.GetRatingCount()
		post.MyScore = stat.GetMyScore()
	}

	return &post, nil
}

// buildJavaHeaders 把网关 context 中的登录态转换为 Java 上游可消费的 HTTP 头。
func buildJavaHeaders(ctx context.Context) map[string]string {
	headers := map[string]string{}
	if token := ctxutil.AuthToken(ctx); token != "" {
		headers["Authorization"] = "Bearer " + token
	}
	if requestID := ctxutil.RequestID(ctx); requestID != "" {
		headers["X-Request-Id"] = requestID
	}
	if traceID := ctxutil.TraceID(ctx); traceID != "" {
		headers["X-Trace-Id"] = traceID
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
