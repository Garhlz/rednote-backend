package event

type UserDeleteEvent struct {
	UserId int64 `json:"userId"`
}
