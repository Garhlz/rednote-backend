package model

import (
	"time"

	"go.mongodb.org/mongo-driver/bson/primitive"
)

// Mongo 中的 Post
type PostDoc struct {
	ID           primitive.ObjectID `bson:"_id"`
	UserId       int64              `bson:"userId"`
	Title        string             `bson:"title"`
	Content      string             `bson:"content"`
	Tags         []string           `bson:"tags"`
	Type         int                `bson:"type"`
	Resources    []string           `bson:"resources"`
	Cover        string             `bson:"cover"`
	CoverWidth   int                `bson:"coverWidth"`
	CoverHeight  int                `bson:"coverHeight"`
	UserNickname string             `bson:"userNickname"`
	UserAvatar   string             `bson:"userAvatar"`
	LikeCount    int                `bson:"likeCount"`
	Status       int                `bson:"status"`
	IsDeleted    int                `bson:"isDeleted"`
	CreatedAt    time.Time          `bson:"createdAt"`
}

// ES 中的 Post
type PostEsDoc struct {
	Id           string   `json:"id"`
	UserId       int64    `json:"userId"`
	Title        string   `json:"title"`
	Content      string   `json:"content"`
	Tags         []string `json:"tags"`
	Type         int      `json:"type"`
	Resources    []string `json:"resources"`
	Cover        string   `json:"cover"`
	CoverWidth   int      `json:"coverWidth"`
	CoverHeight  int      `json:"coverHeight"`
	UserNickname string   `json:"userNickname"`
	UserAvatar   string   `json:"userAvatar"`
	LikeCount    int      `json:"likeCount"`
	CreatedAt    string   `json:"createdAt"` // ES 建议用 ISO String
}
