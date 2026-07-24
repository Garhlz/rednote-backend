package comment

import (
	"testing"
	"time"

	commentpb "comment-rpc/comment"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestToCommentPageResult(t *testing.T) {
	got := toCommentPageResult(&commentpb.CommentPageResponse{
		Items:    []*commentpb.Comment{{Id: "root-1", UserId: 8}},
		Total:    21,
		Page:     2,
		PageSize: 10,
	})

	require.Len(t, got.Records, 1)
	assert.Equal(t, "root-1", got.Records[0].Id)
	assert.Equal(t, "8", got.Records[0].Author.UserId)
	assert.Equal(t, int64(21), got.Total)
	assert.Equal(t, int32(2), got.Current)
	assert.Equal(t, int32(10), got.Size)
}

func TestToCommentVO(t *testing.T) {
	timestamp := time.Date(2026, 7, 23, 8, 30, 0, 0, time.UTC).Unix()
	got := toCommentVO(&commentpb.Comment{
		Id:                  "root",
		Content:             "内容",
		CreatedAt:           timestamp,
		UserId:              9,
		UserNickname:        "作者",
		UserAvatar:          "avatar.png",
		LikeCount:           3,
		IsLiked:             true,
		ReplyToUserId:       10,
		ReplyToUserNickname: "被回复者",
		ReplyCount:          1,
		ChildComments: []*commentpb.Comment{
			{Id: "child", UserId: 11, Content: "回复"},
		},
	})

	assert.Equal(t, "root", got.Id)
	assert.Equal(t, "2026-07-23 16:30:00", got.CreatedAt)
	assert.Equal(t, "9", got.Author.UserId)
	assert.Equal(t, "10", got.ReplyToUser.UserId)
	assert.Equal(t, "被回复者", got.ReplyToUser.Nickname)
	require.Len(t, got.ChildComments, 1)
	assert.Equal(t, "child", got.ChildComments[0].Id)
	assert.Equal(t, "11", got.ChildComments[0].Author.UserId)
}

func TestToCommentVO_OptionalFieldsRemainEmpty(t *testing.T) {
	got := toCommentVO(nil)
	assert.Empty(t, got.CreatedAt)
	assert.Empty(t, got.ReplyToUser.UserId)
	assert.Nil(t, got.ChildComments)
}
