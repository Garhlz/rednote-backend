package event

type UserUpdateEvent struct {
	UserId      int64  `json:"userId"`
	NewNickname string `json:"newNickname"`
	NewAvatar   string `json:"newAvatar"`
}
