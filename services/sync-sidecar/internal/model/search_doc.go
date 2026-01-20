package model

import "time"

type SearchHistoryDoc struct {
	UserId    int64     `bson:"userId"`
	Keyword   string    `bson:"keyword"`
	UpdatedAt time.Time `bson:"updatedAt"`
}
