package logic

import (
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
)

// TestAssembleSuggestions 测试 assembleSuggestions 的核心策略：
//   - 原词优先，但仅当 ES 有命中时才插入
//   - 仅有原词时清空（避免"假联想"）
//   - 高亮字段优先于 fallback 候选
//   - 最多返回 10 条，去重保序
func TestAssembleSuggestions_OriginalWordFirst(t *testing.T) {
	hits := []suggestHit{
		{
			Highlight: map[string][]string{
				"title": {"<em>go</em>lang 入门"},
			},
			Title: "golang 入门",
			Tags:  []string{"golang"},
		},
	}
	result := assembleSuggestions("go", hits)

	assert.Equal(t, "go", result[0], "原词应排在最前")
	assert.Contains(t, result, "<em>go</em>lang 入门", "高亮命中应包含在结果中")
}

func TestAssembleSuggestions_NoHits_ReturnsEmpty(t *testing.T) {
	result := assembleSuggestions("xyz", []suggestHit{})
	assert.Empty(t, result, "ES 无命中时不应插入原词，返回空")
}

func TestAssembleSuggestions_OnlyOriginalWord_ClearedToAvoidFakeHint(t *testing.T) {
	// 有命中，但没有高亮也没有 fallback 匹配 => 结果只有原词 => 应清空
	hits := []suggestHit{
		{
			Highlight: map[string][]string{},
			Title:     "完全不相关的标题",
			Tags:      []string{"irrelevant"},
		},
	}
	result := assembleSuggestions("go", hits)
	assert.Empty(t, result, "仅有原词时应清空，避免只回显用户输入")
}

func TestAssembleSuggestions_Dedup(t *testing.T) {
	// 原词与 fallback 候选重复时只保留一份
	hits := []suggestHit{
		{
			Highlight: map[string][]string{},
			Title:     "golang 教程",
			Tags:      []string{"golang"},
		},
	}
	result := assembleSuggestions("golang", hits)

	// 计算 "golang" 出现次数
	count := 0
	for _, s := range result {
		if s == "golang" {
			count++
		}
	}
	assert.Equal(t, 1, count, "golang 应只出现一次（原词与 fallback 去重）")
}

func TestAssembleSuggestions_HighlightBeforeFallback(t *testing.T) {
	// 同一个 hit 里既有高亮也有 fallback 候选时，高亮应先被插入
	hits := []suggestHit{
		{
			Highlight: map[string][]string{
				"title": {"<em>Go</em> 语言实战"},
			},
			Title: "Go 语言实战",
			Tags:  []string{"go", "programming"},
		},
	}
	result := assembleSuggestions("go", hits)

	// result[0] = 原词 "go"
	// result[1] = 高亮 "<em>Go</em> 语言实战"（在 fallback 之前）
	if len(result) < 2 {
		t.Fatalf("结果太短：%v", result)
	}
	assert.Equal(t, "go", result[0], "原词应最先")
	assert.Equal(t, "<em>Go</em> 语言实战", result[1], "高亮片段应早于 fallback 候选出现")
}

func TestAssembleSuggestions_MaxTen(t *testing.T) {
	// 构造足够多的命中，验证结果不超过 10 条
	hits := make([]suggestHit, 0, 15)
	for i := 0; i < 15; i++ {
		tag := strings.Repeat("a", i+1) // aa, aaa, aaaa...
		hits = append(hits, suggestHit{
			Highlight: map[string][]string{},
			Title:     "",
			Tags:      []string{tag},
		})
	}
	// 关键词随便取一个能匹配 tag 的前缀
	result := assembleSuggestions("a", hits)
	assert.LessOrEqual(t, len(result), 10, "结果最多 10 条")
}

func TestAssembleSuggestions_EmptyKeyword_NoOriginalInserted(t *testing.T) {
	hits := []suggestHit{
		{
			Highlight: map[string][]string{"title": {"高亮标题"}},
			Title:     "高亮标题",
			Tags:      []string{},
		},
	}
	result := assembleSuggestions("", hits)
	for _, s := range result {
		assert.NotEqual(t, "", s, "空关键词不应插入空字符串")
	}
}
