package model

import (
	"time"

	"comment-rpc/comment"

	"go.mongodb.org/mongo-driver/bson/primitive"
)

type CommentDoc struct {
	ID                  primitive.ObjectID `bson:"_id,omitempty"`
	PostID              string             `bson:"postId"`
	UserID              int64              `bson:"userId"`
	UserNickname        string             `bson:"userNickname"`
	UserAvatar          string             `bson:"userAvatar"`
	Content             string             `bson:"content"`
	ParentID            string             `bson:"parentId,omitempty"`
	ReplyToUserID       int64              `bson:"replyToUserId,omitempty"`
	ReplyToUserNickname string             `bson:"replyToUserNickname,omitempty"`
	ReplyCount          int32              `bson:"replyCount"`
	LikeCount           int32              `bson:"likeCount"`
	CreatedAt           time.Time          `bson:"createdAt"`
}

type CommentLikeDoc struct {
	ID        primitive.ObjectID `bson:"_id,omitempty"`
	UserID    int64              `bson:"userId"`
	CommentID string             `bson:"commentId"`
	CreatedAt time.Time          `bson:"createdAt"`
}

func (d CommentDoc) ToProto(isLiked bool) *comment.Comment {
	return &comment.Comment{
		Id:                  d.ID.Hex(),
		PostId:              d.PostID,
		UserId:              d.UserID,
		UserNickname:        d.UserNickname,
		UserAvatar:          d.UserAvatar,
		Content:             d.Content,
		ParentId:            d.ParentID,
		ReplyToUserId:       d.ReplyToUserID,
		ReplyToUserNickname: d.ReplyToUserNickname,
		ReplyCount:          d.ReplyCount,
		LikeCount:           d.LikeCount,
		IsLiked:             isLiked,
		CreatedAt:           d.CreatedAt.Unix(),
	}
}
