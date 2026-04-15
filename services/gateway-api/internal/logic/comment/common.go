package comment

import (
	"strconv"
	"time"

	commentpb "comment-rpc/comment"
	"gateway-api/internal/types"
)

func toCommentPageResult(in *commentpb.CommentPageResponse) *types.CommentPageResult {
	records := make([]types.CommentVO, 0, len(in.GetItems()))
	for _, item := range in.GetItems() {
		records = append(records, *toCommentVO(item))
	}
	return &types.CommentPageResult{
		Records: records,
		Total:   in.GetTotal(),
		Current: in.GetPage(),
		Size:    in.GetPageSize(),
	}
}

func toCommentVO(in *commentpb.Comment) *types.CommentVO {
	vo := &types.CommentVO{
		Id:        in.GetId(),
		Content:   in.GetContent(),
		LikeCount: in.GetLikeCount(),
		IsLiked:   in.GetIsLiked(),
		ReplyCount: in.GetReplyCount(),
		Author: types.SimpleUserVO{
			UserId:   strconv.FormatInt(in.GetUserId(), 10),
			Nickname: in.GetUserNickname(),
			Avatar:   in.GetUserAvatar(),
		},
	}
	if in.GetCreatedAt() > 0 {
		vo.CreatedAt = time.Unix(in.GetCreatedAt(), 0).In(time.FixedZone("CST", 8*3600)).Format("2006-01-02 15:04:05")
	}
	if in.GetReplyToUserId() > 0 {
		vo.ReplyToUser = types.ReplyUserInfo{
			UserId:   strconv.FormatInt(in.GetReplyToUserId(), 10),
			Nickname: in.GetReplyToUserNickname(),
		}
	}
	if len(in.GetChildComments()) > 0 {
		vo.ChildComments = make([]types.CommentVO, 0, len(in.GetChildComments()))
		for _, child := range in.GetChildComments() {
			vo.ChildComments = append(vo.ChildComments, *toCommentVO(child))
		}
	}
	return vo
}
