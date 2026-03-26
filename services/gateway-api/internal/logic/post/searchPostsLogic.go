// Code scaffolded by goctl. Safe to edit.
// goctl 1.9.2

package post

import (
	"context"
	"strconv"

	"gateway-api/internal/pkg/ctxutil"
	"gateway-api/internal/svc"
	"gateway-api/internal/types"

	"github.com/zeromicro/go-zero/core/logx"
	"interaction-rpc/interactionservice"
	"search-rpc/searchservice"
)

type SearchPostsLogic struct {
	logx.Logger
	ctx    context.Context
	svcCtx *svc.ServiceContext
}

func NewSearchPostsLogic(ctx context.Context, svcCtx *svc.ServiceContext) *SearchPostsLogic {
	return &SearchPostsLogic{
		Logger: logx.WithContext(ctx),
		ctx:    ctx,
		svcCtx: svcCtx,
	}
}

func (l *SearchPostsLogic) SearchPosts(req *types.SearchReq) (resp *types.PageResultPost, err error) {
	// 兼容前端可能传来的 size/pageSize 两种分页参数。
	if req.PageSize == 0 && req.Size > 0 {
		req.PageSize = req.Size
	}
	if req.Page == 0 {
		req.Page = 1
	}

	// 第一步：先调用 search-rpc，拿到搜索/排序结果。
	// search-rpc 负责：
	// 1. 关键词搜索
	// 2. Tag 过滤
	// 3. ES 排序
	// 4. 返回帖子基础信息
	client := searchservice.NewSearchService(l.svcCtx.SearchRpc)
	result, err := client.Search(l.ctx, &searchservice.SearchRequest{
		Keyword:  req.Keyword,
		Tag:      req.Tag,
		Page:     req.Page,
		PageSize: req.PageSize,
		Sort:     req.Sort,
		UserId:   ctxutil.UserID(l.ctx),
	})
	if err != nil {
		return nil, err
	}

	// 第二步：从搜索结果里提取 postIds 和 authorId 映射，
	// 为后面的 interaction-rpc 批量聚合做准备。
	postIds := make([]string, 0, len(result.GetItems()))
	postAuthorMap := make(map[string]int64, len(result.GetItems()))
	for _, item := range result.GetItems() {
		postIds = append(postIds, item.GetId())
		if item.GetAuthor() != nil {
			if authorId, err := strconv.ParseInt(item.GetAuthor().GetUserId(), 10, 64); err == nil {
				postAuthorMap[item.GetId()] = authorId
			}
		}
	}

	statsMap := map[string]*interactionservice.PostStats{}
	if len(postIds) > 0 {
		// 第三步：批量调用 interaction-rpc，把“实时互动字段”一次性补齐。
		// 这里是网关编排职责最核心的地方：
		// 搜索服务只负责找到帖子，互动服务负责补点赞/收藏/评分/关注态。
		interactionClient := interactionservice.NewInteractionService(l.svcCtx.InteractionRpc)
		statsResp, err := interactionClient.BatchPostStats(l.ctx, &interactionservice.BatchPostStatsRequest{
			UserId:        ctxutil.UserID(l.ctx),
			PostIds:       postIds,
			PostAuthorMap: postAuthorMap,
		})
		if err != nil {
			l.Logger.Errorf("batch post stats failed: %v", err)
		} else {
			statsMap = statsResp.GetStats()
		}
	}

	records := make([]types.PostVO, 0, len(result.GetItems()))
	for _, item := range result.GetItems() {
		author := types.PostAuthor{}
		if item.GetAuthor() != nil {
			author.UserId = item.GetAuthor().GetUserId()
			author.Nickname = item.GetAuthor().GetNickname()
			author.Avatar = item.GetAuthor().GetAvatar()
		}
		stat := statsMap[item.GetId()]
		post := types.PostVO{
			Id:            item.GetId(),
			Author:        author,
			Title:         item.GetTitle(),
			Content:       item.GetContent(),
			Type:          item.GetType(),
			Cover:         item.GetCover(),
			Images:        []string{},
			Video:         "",
			LikeCount:     item.GetLikeCount(),
			CollectCount:  0,
			CommentCount:  0,
			IsLiked:       item.GetIsLiked(),
			IsCollected:   false,
			IsFollowed:    false,
			RatingAverage: 0,
			RatingCount:   0,
			MyScore:       0,
			CreatedAt:     "",
			Tags:          item.GetTags(),
			CoverWidth:    item.GetCoverWidth(),
			CoverHeight:   item.GetCoverHeight(),
		}

		if stat != nil {
			// interaction-rpc 返回时，用它覆盖搜索结果里那些需要实时性的字段。
			// 这样最终前端拿到的是“搜索结果 + 实时互动态”的统一视图。
			post.LikeCount = stat.GetLikeCount()
			post.IsLiked = stat.GetIsLiked()
			post.IsCollected = stat.GetIsCollected()
			post.IsFollowed = stat.GetIsFollowed()
			post.CollectCount = stat.GetCollectCount()
			post.CommentCount = stat.GetCommentCount()
			post.RatingAverage = stat.GetRatingAverage()
			post.RatingCount = stat.GetRatingCount()
			post.MyScore = stat.GetMyScore()
		}

		records = append(records, post)
	}

	return &types.PageResultPost{
		Records: records,
		Total:   result.GetTotal(),
		Current: req.Page,
		Size:    req.PageSize,
	}, nil
}
