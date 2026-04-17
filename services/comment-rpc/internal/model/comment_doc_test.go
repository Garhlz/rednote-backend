package model

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

func TestCommentDoc_ToProto(t *testing.T) {
	// 固定时间，便于断言 CreatedAt
	now := time.Date(2024, 6, 1, 12, 0, 0, 0, time.UTC)
	objID := primitive.NewObjectID()

	baseDoc := CommentDoc{
		ID:                  objID,
		PostID:              "post-001",
		UserID:              42,
		UserNickname:        "小红",
		UserAvatar:          "https://cdn.example.com/avatar.jpg",
		Content:             "这是一条评论",
		ParentID:            "",
		ReplyToUserID:       0,
		ReplyToUserNickname: "",
		ReplyCount:          3,
		LikeCount:           10,
		CreatedAt:           now,
	}

	tests := []struct {
		name           string
		doc            CommentDoc
		isLiked        bool
		wantIsLiked    bool
		wantParentID   string
		wantHasParent  bool
	}{
		{
			name:          "isLiked=true 时 proto.IsLiked 为 true",
			doc:           baseDoc,
			isLiked:       true,
			wantIsLiked:   true,
			wantParentID:  "",
			wantHasParent: false,
		},
		{
			name:          "isLiked=false 时 proto.IsLiked 为 false",
			doc:           baseDoc,
			isLiked:       false,
			wantIsLiked:   false,
			wantParentID:  "",
			wantHasParent: false,
		},
		{
			name: "根评论 ParentID 为空，proto.ParentId 也为空",
			doc:  baseDoc, // ParentID == ""
			isLiked:       false,
			wantIsLiked:   false,
			wantParentID:  "",
			wantHasParent: false,
		},
		{
			name: "子评论 ParentID 不为空，proto.ParentId 不为空",
			doc: func() CommentDoc {
				d := baseDoc
				d.ParentID = "parent-999"
				d.ReplyToUserID = 7
				d.ReplyToUserNickname = "某用户"
				return d
			}(),
			isLiked:       true,
			wantIsLiked:   true,
			wantParentID:  "parent-999",
			wantHasParent: true,
		},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			proto := tc.doc.ToProto(tc.isLiked)

			// IsLiked 字段
			assert.Equal(t, tc.wantIsLiked, proto.IsLiked, "IsLiked 不匹配")

			// ParentId 字段
			assert.Equal(t, tc.wantParentID, proto.ParentId, "ParentId 不匹配")
			if tc.wantHasParent {
				assert.NotEmpty(t, proto.ParentId, "子评论的 ParentId 不应为空")
			} else {
				assert.Empty(t, proto.ParentId, "根评论的 ParentId 应为空")
			}

			// 所有字段映射正确
			assert.Equal(t, tc.doc.ID.Hex(), proto.Id, "Id 不匹配")
			assert.Equal(t, tc.doc.PostID, proto.PostId, "PostId 不匹配")
			assert.Equal(t, tc.doc.UserID, proto.UserId, "UserId 不匹配")
			assert.Equal(t, tc.doc.UserNickname, proto.UserNickname, "UserNickname 不匹配")
			assert.Equal(t, tc.doc.UserAvatar, proto.UserAvatar, "UserAvatar 不匹配")
			assert.Equal(t, tc.doc.Content, proto.Content, "Content 不匹配")
			assert.Equal(t, tc.doc.ReplyCount, proto.ReplyCount, "ReplyCount 不匹配")
			assert.Equal(t, tc.doc.LikeCount, proto.LikeCount, "LikeCount 不匹配")
			assert.Equal(t, tc.doc.CreatedAt.Unix(), proto.CreatedAt, "CreatedAt 不匹配")
			// 回复关系字段：ToProto 中这两个字段也有映射，需要一并锁住，
			// 防止后续改坏 ReplyToUserId / ReplyToUserNickname 时测试无法感知。
			assert.Equal(t, tc.doc.ReplyToUserID, proto.ReplyToUserId, "ReplyToUserId 不匹配")
			assert.Equal(t, tc.doc.ReplyToUserNickname, proto.ReplyToUserNickname, "ReplyToUserNickname 不匹配")
		})
	}
}
