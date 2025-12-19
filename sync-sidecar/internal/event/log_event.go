package event

// LogEvent 对应 Java 发来的 JSON
type LogEvent struct {
	TraceId     string `json:"traceId"`
	LogType     string `json:"logType"`
	Module      string `json:"module"`
	BizId       string `json:"bizId"`
	UserId      int64  `json:"userId"`
	Username    string `json:"username"`
	Role        string `json:"role"`
	Method      string `json:"method"`
	Uri         string `json:"uri"`
	Ip          string `json:"ip"`
	Params      string `json:"params"`
	Status      int    `json:"status"`
	TimeCost    int64  `json:"timeCost"`
	Description string `json:"description"`
	ErrorMsg    string `json:"errorMsg"`
	// Java 发来的可能是字符串，用 string 接收最稳妥，然后再解析
	CreatedAt string `json:"createdAt"`
}
