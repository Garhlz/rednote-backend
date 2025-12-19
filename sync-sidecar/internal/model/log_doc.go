package model

import (
	"time"

	"go.mongodb.org/mongo-driver/bson/primitive"
)

// ApiLogDoc 对应 MongoDB 里的 api_logs 集合
type ApiLogDoc struct {
	ID          primitive.ObjectID `bson:"_id,omitempty"`
	TraceId     string             `bson:"traceId"`
	LogType     string             `bson:"logType"`
	Module      string             `bson:"module"`
	BizId       string             `bson:"bizId"`
	UserId      int64              `bson:"userId"`
	Username    string             `bson:"username"`
	Role        string             `bson:"role"`
	Method      string             `bson:"method"`
	Uri         string             `bson:"uri"`
	Ip          string             `bson:"ip"`
	Params      string             `bson:"params"`
	Status      int                `bson:"status"`
	TimeCost    int64              `bson:"timeCost"`
	Description string             `bson:"description"`
	ErrorMsg    string             `bson:"errorMsg"`
	CreatedAt   time.Time          `bson:"createdAt"` // BSON Date 类型
}
