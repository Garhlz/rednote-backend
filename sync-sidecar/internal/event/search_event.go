package event

type UserSearchEvent struct {
	UserId  int64  `json:"userId"`
	Keyword string `json:"keyword"`
}
