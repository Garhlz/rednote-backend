package event

type PostCreateEvent struct {
	Id           string   `json:"id"`
	Title        string   `json:"title"`
	Content      string   `json:"content"`
	Tags         []string `json:"tags"`
	UserId       int64    `json:"userId"`
	UserNickname string   `json:"userNickname"`
	UserAvatar   string   `json:"userAvatar"`
	Type         int      `json:"type"`
	Cover        string   `json:"cover"`
	CoverWidth   int      `json:"coverWidth"`
	CoverHeight  int      `json:"coverHeight"`
	Images       []string `json:"images"`
	Video        string   `json:"video"`
}

type PostUpdateEvent struct {
	PostId string `json:"postId"`
}

type PostDeleteEvent struct {
	PostId string `json:"postId"`
}

type UserUpdateEvent struct {
	UserId      int64  `json:"userId"`
	NewNickname string `json:"newNickname"`
	NewAvatar   string `json:"newAvatar"`
}

// 【新增】
type UserDeleteEvent struct {
	UserId int64 `json:"userId"`
}
