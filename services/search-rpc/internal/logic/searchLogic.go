package logic

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"time"

	"search-rpc/internal/svc"
	"search-rpc/search"

	"github.com/zeromicro/go-zero/core/logx"
	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/mongo/options"
)

type SearchLogic struct {
	ctx    context.Context
	svcCtx *svc.ServiceContext
	logx.Logger
}

func NewSearchLogic(ctx context.Context, svcCtx *svc.ServiceContext) *SearchLogic {
	return &SearchLogic{
		ctx:    ctx,
		svcCtx: svcCtx,
		Logger: logx.WithContext(ctx),
	}
}

// 对应 Java: PostEsDoc 结构 (用于解析 ES 返回结果)
type PostEsDoc struct {
	Id           string   `json:"id"`
	Title        string   `json:"title"`
	Content      string   `json:"content"`
	Cover        string   `json:"cover"`
	Type         int32    `json:"type"`
	LikeCount    int32    `json:"likeCount"`
	UserId       int64    `json:"userId"`
	UserNickname string   `json:"userNickname"`
	UserAvatar   string   `json:"userAvatar"`
	Tags         []string `json:"tags"`
	CoverWidth   int32    `json:"coverWidth"`
	CoverHeight  int32    `json:"coverHeight"`
	CreatedAt    string   `json:"createdAt"` // ES 默认存的是 UTC 时间
}

func (l *SearchLogic) Search(in *search.SearchRequest) (*search.SearchResponse, error) {
	// 1. 异步记录搜索历史 (对应 Java: rabbitTemplate.convertAndSend("search.history"))
	// 既然我们在搜索微服务内部，直接写 Mongo 效率更高，或者你也发 MQ。这里演示直接写库。
	if in.UserId > 0 && in.Keyword != "" {
		go l.recordSearchHistory(in.UserId, in.Keyword)
	}

	// 2. 构建 ES 查询 Body (对应 Java: Step 3)
	// 使用 Map 构建 JSON 结构
	queryBool := map[string]interface{}{}
	mustClauses := []map[string]interface{}{}
	filterClauses := []map[string]interface{}{}

	// A. 关键词匹配 (MultiMatch)
	if in.Keyword != "" {
		mustClauses = append(mustClauses, map[string]interface{}{
			"multi_match": map[string]interface{}{
				"query":  in.Keyword,
				"fields": []string{"title^3", "title.pinyin^1.5", "content", "tags^2", "tags.pinyin^1.0"},
			},
		})
	} else {
		mustClauses = append(mustClauses, map[string]interface{}{
			"match_all": map[string]interface{}{},
		})
	}

	// B. 标签过滤 (Term)
	if in.Tag != "" {
		filterClauses = append(filterClauses, map[string]interface{}{
			"term": map[string]interface{}{
				"tags.keyword": in.Tag,
			},
		})
	}

	queryBool["must"] = mustClauses
	if len(filterClauses) > 0 {
		queryBool["filter"] = filterClauses
	}

	// 3. 构建排序与最终 Query (对应 Java: Step 3.2)
	var finalQuery map[string]interface{}
	var sortRule []map[string]interface{}

	if in.Sort == "hot" {
		// [F] 综合热度: Function Score
		nowStr := time.Now().Format("2006-01-02T15:04:05.000")
		finalQuery = map[string]interface{}{
			"function_score": map[string]interface{}{
				"query": map[string]interface{}{"bool": queryBool},
				"functions": []map[string]interface{}{
					{
						// 点赞加分
						"filter": map[string]interface{}{"match_all": map[string]interface{}{}},
						"field_value_factor": map[string]interface{}{
							"field":    "likeCount",
							"modifier": "log1p",
							"factor":   1.0,
							"missing":  0.0,
						},
					},
					{
						// 高斯衰减
						"filter": map[string]interface{}{"match_all": map[string]interface{}{}},
						"gauss": map[string]interface{}{
							"createdAt": map[string]interface{}{
								"origin": nowStr,
								"scale":  "3d",
								"offset": "1d",
								"decay":  0.5,
							},
						},
					},
				},
				"boost_mode": "multiply",
			},
		}
		// 排序: 分数倒序, 时间倒序
		sortRule = []map[string]interface{}{
			{"_score": "desc"},
			{"createdAt": "desc"},
		}
	} else {
		// 普通查询，不需要 Function Score
		finalQuery = map[string]interface{}{"bool": queryBool}

		switch in.Sort {
		case "new":
			sortRule = []map[string]interface{}{{"createdAt": "desc"}}
		case "old":
			sortRule = []map[string]interface{}{{"createdAt": "asc"}}
		case "likes":
			sortRule = []map[string]interface{}{{"likeCount": "desc"}}
		default: // default is new/hot fallback
			sortRule = []map[string]interface{}{{"createdAt": "desc"}}
		}
	}

	// 4. 组装完整请求体
	from := (in.Page - 1) * in.PageSize
	if from < 0 {
		from = 0
	}

	reqBody := map[string]interface{}{
		"query": finalQuery,
		"sort":  sortRule,
		"from":  from,
		"size":  in.PageSize,
		"_source": []string{
			"id", "title", "content", "cover", "coverWidth", "coverHeight",
			"type", "tags", "userId", "userNickname", "userAvatar", "likeCount", "createdAt",
		},
	}

	var buf bytes.Buffer
	if err := json.NewEncoder(&buf).Encode(reqBody); err != nil {
		return nil, err
	}

	// 5. 执行 ES 搜索
	res, err := l.svcCtx.Es.Search(
		l.svcCtx.Es.Search.WithContext(l.ctx),
		l.svcCtx.Es.Search.WithIndex("posts"), // 索引名
		l.svcCtx.Es.Search.WithBody(&buf),
		l.svcCtx.Es.Search.WithTrackTotalHits(true),
	)
	if err != nil {
		return nil, err
	}
	defer res.Body.Close()

	if res.IsError() {
		return nil, fmt.Errorf("ES error: %s", res.String())
	}

	// 6. 解析结果 (对应 Java: searchHits 解析)
	var esResponse struct {
		Hits struct {
			Total struct {
				Value int64 `json:"value"`
			} `json:"total"`
			Hits []struct {
				Source PostEsDoc `json:"_source"`
			} `json:"hits"`
		} `json:"hits"`
	}

	if err := json.NewDecoder(res.Body).Decode(&esResponse); err != nil {
		return nil, err
	}

	// 7. 转换为 Proto 响应
	var items []*search.SearchPostVO
	for _, hit := range esResponse.Hits.Hits {
		doc := hit.Source

		// 处理摘要 (Java: StrUtil.subPre(content, 50))
		summary := doc.Content
		if len([]rune(summary)) > 50 {
			summary = string([]rune(summary)[:50]) + "..."
		}

		item := &search.SearchPostVO{
			Id:          doc.Id,
			Title:       doc.Title,
			Content:     summary,
			Cover:       doc.Cover,
			Type:        doc.Type,
			LikeCount:   doc.LikeCount,
			Tags:        doc.Tags,
			CoverWidth:  doc.CoverWidth,
			CoverHeight: doc.CoverHeight,
			Author: &search.UserVO{
				UserId:   fmt.Sprintf("%d", doc.UserId),
				Nickname: doc.UserNickname,
				Avatar:   doc.UserAvatar,
			},
			// IsLiked 可以在这里设为 false，让 Java 网关层去补全
			// 或者如果 Search 微服务能访问 Redis，也可以在这里查
			IsLiked: false,
		}
		items = append(items, item)
	}

	return &search.SearchResponse{
		Items: items,
		Total: esResponse.Hits.Total.Value,
	}, nil
}

// 辅助方法：记录搜索历史 (Upsert)
func (l *SearchLogic) recordSearchHistory(userId int64, keyword string) {
	collection := l.svcCtx.Mongo.Collection("search_histories")

	filter := bson.M{"userId": userId, "keyword": keyword}
	update := bson.M{
		"$set": bson.M{
			"updatedAt": time.Now(),
		},
		"$setOnInsert": bson.M{
			"userId":    userId,
			"keyword":   keyword,
			"createdAt": time.Now(),
		},
	}
	opts := options.Update().SetUpsert(true)

	_, err := collection.UpdateOne(context.Background(), filter, update, opts)
	if err != nil {
		logx.Errorf("Failed to record search history: %v", err)
	}
}
