package logic

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"regexp"
	"strings"
	"time"

	appmetrics "search-rpc/internal/metrics"
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
	start := time.Now()
	if in.Keyword == "" {
		appmetrics.ObserveRequest("suggest", "empty_keyword", time.Since(start))
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
		appmetrics.ObserveRequest("suggest", "encode_error", time.Since(start))
		return nil, err
	}

	// 4. 执行搜索
	esStart := time.Now()
	res, err := l.svcCtx.Es.Search(
		l.svcCtx.Es.Search.WithContext(l.ctx),
		l.svcCtx.Es.Search.WithIndex("posts"),
		l.svcCtx.Es.Search.WithBody(&buf),
	)
	if err != nil {
		appmetrics.ObserveESQuery("suggest", time.Since(esStart))
		appmetrics.ObserveRequest("suggest", "es_error", time.Since(start))
		return nil, err
	}
	defer res.Body.Close()
	appmetrics.ObserveESQuery("suggest", time.Since(esStart))

	if res.IsError() {
		appmetrics.ObserveRequest("suggest", "es_status_error", time.Since(start))
		return nil, fmt.Errorf("ES error: %s", res.String())
	}

	// 5. 解析高亮结果
	var esResponse struct {
		Hits struct {
			Hits []struct {
				Highlight map[string][]string `json:"highlight"`
				Source    struct {
					Title string   `json:"title"`
					Tags  []string `json:"tags"`
				} `json:"_source"`
			} `json:"hits"`
		} `json:"hits"`
	}
	if err := json.NewDecoder(res.Body).Decode(&esResponse); err != nil {
		appmetrics.ObserveRequest("suggest", "decode_error", time.Since(start))
		return nil, err
	}

	// 6. 提取建议词 (去重 + 保持顺序)
	// Java 用了 LinkedHashSet，Go 没有内置，可以用 map + slice 模拟。
	// 这里的策略是“原词优先，但只有在确实有搜索结果时才插入”，
	// 避免建议框总是回显一条没有命中的原词，体验上像“假联想”。
	suggestions := []string{}
	seen := map[string]bool{}
	seededOriginal := false

	if len(esResponse.Hits.Hits) > 0 {
		original := strings.TrimSpace(in.Keyword)
		if original != "" {
			suggestions = append(suggestions, original)
			seen[original] = true
			seededOriginal = true
		}
	}

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
		for _, candidate := range collectSuggestFallbacks(hit.Source.Title, hit.Source.Tags, in.Keyword) {
			if !seen[candidate] {
				suggestions = append(suggestions, candidate)
				seen[candidate] = true
			}
			if len(suggestions) >= 10 {
				break
			}
		}
		if len(suggestions) >= 10 {
			break
		}
	}

	// 如果只有原词一条，而没有任何真正候选，就把它去掉。
	// 这样最终返回空数组，前端会比“只回显用户输入”更符合预期。
	if seededOriginal && len(suggestions) == 1 {
		suggestions = suggestions[:0]
	}

	appmetrics.ObserveRequest("suggest", "success", time.Since(start))
	return &search.SuggestResponse{Suggestions: suggestions}, nil
}

func collectSuggestFallbacks(title string, tags []string, keyword string) []string {
	candidates := make([]string, 0, 1+len(tags))
	kw := strings.ToLower(strings.TrimSpace(keyword))
	if kw == "" {
		return candidates
	}

	if strings.Contains(strings.ToLower(title), kw) {
		candidates = append(candidates, title)
	}
	for _, tag := range tags {
		if strings.Contains(strings.ToLower(tag), kw) {
			candidates = append(candidates, tag)
		}
	}
	return candidates
}
