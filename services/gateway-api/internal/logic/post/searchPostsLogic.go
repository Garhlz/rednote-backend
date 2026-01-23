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
	if req.PageSize == 0 && req.Size > 0 {
		req.PageSize = req.Size
	}
	if req.Page == 0 {
		req.Page = 1
	}
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
			post.IsLiked = stat.GetIsLiked()
			post.IsCollected = stat.GetIsCollected()
			post.IsFollowed = stat.GetIsFollowed()
			post.CollectCount = stat.GetCollectCount()
			post.CommentCount = stat.GetCommentCount()
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
