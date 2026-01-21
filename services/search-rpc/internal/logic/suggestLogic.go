package logic

import (
	"bytes"
	"context"
	"encoding/json"
	"regexp"
	"search-rpc/internal/svc"
	"search-rpc/search"

	"github.com/zeromicro/go-zero/core/logx"
)

type SuggestLogic struct {
	ctx    context.Context
	svcCtx *svc.ServiceContext
	logx.Logger
}

func NewSuggestLogic(ctx context.Context, svcCtx *svc.ServiceContext) *SuggestLogic {
	return &SuggestLogic{
		ctx:    ctx,
		svcCtx: svcCtx,
		Logger: logx.WithContext(ctx),
	}
}

var chineseRegex = regexp.MustCompile("[\u4e00-\u9fa5]")

func (l *SuggestLogic) Suggest(in *search.SuggestRequest) (*search.SuggestResponse, error) {
	if in.Keyword == "" {
		return &search.SuggestResponse{}, nil
	}

	// 1. 判断是否包含中文 (对应 Java: keyword.matches(".*[\\u4e00-\\u9fa5].*"))
	hasChinese := chineseRegex.MatchString(in.Keyword)

	// 2. 构建查询 (Should match_phrase_prefix)
	shouldClauses := []map[string]interface{}{}

	if hasChinese {
		// 中文策略：只查 title 和 tags
		shouldClauses = append(shouldClauses,
			map[string]interface{}{"match_phrase_prefix": map[string]interface{}{"title": in.Keyword}},
			map[string]interface{}{"match_phrase_prefix": map[string]interface{}{"tags": in.Keyword}},
		)
	} else {
		// 拼音策略：查 title.pinyin 和 tags.pinyin
		shouldClauses = append(shouldClauses,
			map[string]interface{}{"match_phrase_prefix": map[string]interface{}{"title.pinyin": map[string]interface{}{"query": in.Keyword, "slop": 2}}},
			map[string]interface{}{"match_phrase_prefix": map[string]interface{}{"tags.pinyin": in.Keyword}},
		)
	}

	// 3. 构建高亮
	reqBody := map[string]interface{}{
		"query": map[string]interface{}{
			"bool": map[string]interface{}{
				"should": shouldClauses,
			},
		},
		"size":    10,
		"_source": []string{"title", "tags"}, // 只取需要的字段
		"highlight": map[string]interface{}{
			"pre_tags":  []string{"<em>"},
			"post_tags": []string{"</em>"},
			"fields": map[string]interface{}{
				"title":        map[string]interface{}{},
				"tags":         map[string]interface{}{},
				"title.pinyin": map[string]interface{}{},
				"tags.pinyin":  map[string]interface{}{},
			},
		},
	}

	var buf bytes.Buffer
	if err := json.NewEncoder(&buf).Encode(reqBody); err != nil {
		return nil, err
	}

	// 4. 执行搜索
	res, err := l.svcCtx.Es.Search(
		l.svcCtx.Es.Search.WithContext(l.ctx),
		l.svcCtx.Es.Search.WithIndex("posts"),
		l.svcCtx.Es.Search.WithBody(&buf),
	)
	if err != nil {
		return nil, err
	}
	defer res.Body.Close()

	// 5. 解析高亮结果
	var esResponse struct {
		Hits struct {
			Hits []struct {
				Highlight map[string][]string `json:"highlight"`
			} `json:"hits"`
		} `json:"hits"`
	}
	if err := json.NewDecoder(res.Body).Decode(&esResponse); err != nil {
		return nil, err
	}

	// 6. 提取建议词 (去重 + 保持顺序)
	// Java 用了 LinkedHashSet，Go 没有内置，可以用 map + slice 模拟
	suggestions := []string{}
	seen := map[string]bool{}

	// 添加原始词高亮
	rawHighlight := "<em>" + in.Keyword + "</em>"
	suggestions = append(suggestions, rawHighlight)
	seen[rawHighlight] = true

	for _, hit := range esResponse.Hits.Hits {
		// 检查各个字段的高亮
		fieldsToCheck := []string{"title", "title.pinyin", "tags", "tags.pinyin"}
		for _, field := range fieldsToCheck {
			if frags, ok := hit.Highlight[field]; ok && len(frags) > 0 {
				val := frags[0]
				if !seen[val] {
					suggestions = append(suggestions, val)
					seen[val] = true
				}
			}
		}
		if len(suggestions) >= 10 {
			break
		}
	}

	return &search.SuggestResponse{Suggestions: suggestions}, nil
}
