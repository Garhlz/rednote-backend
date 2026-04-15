package types

type UserSearchReq struct {
	Keyword string `form:"keyword"`
}

type UserSearchVO struct {
	UserId        string `json:"userId"`
	Nickname      string `json:"nickname"`
	Avatar        string `json:"avatar"`
	Email         string `json:"email"`
	IsFollowed    bool   `json:"isFollowed"`
	IsFollowingMe bool   `json:"isFollowingMe"`
}
