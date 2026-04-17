package logic

import (
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
)

// TestBuildSearchSummary 对 searchLogic.go 中的纯函数 buildSearchSummary 进行表驱动单元测试。
//
// 该函数的核心规则：
//  1. 优先返回 highlight 字段中第一个非空片段（按 content → title → tags 顺序查找）。
//  2. 所有 highlight 均无有效内容时，回退到 content 字段截取（超过 50 个 rune 则截断并加省略号）。
//  3. content 为空时返回空字符串。
func TestBuildSearchSummary(t *testing.T) {
	tests := []struct {
		name      string
		content   string
		highlight map[string][]string
		want      string
	}{
		// ── highlight 优先场景 ──────────────────────────────────────────────────
		{
			name:    "highlight_content_优先于原始内容",
			content: "这是原始正文",
			highlight: map[string][]string{
				"content": {"这是<em>高亮</em>片段"},
			},
			want: "这是<em>高亮</em>片段",
		},
		{
			name:    "highlight_title_在_content_缺失时生效",
			content: "正文内容",
			highlight: map[string][]string{
				"title": {"<em>标题</em>命中"},
			},
			want: "<em>标题</em>命中",
		},
		{
			name:    "highlight_tags_在_content_和_title_均缺失时生效",
			content: "正文",
			highlight: map[string][]string{
				"tags": {"<em>golang</em>"},
			},
			want: "<em>golang</em>",
		},
		{
			name:    "highlight_content_优先于_highlight_title",
			content: "正文",
			highlight: map[string][]string{
				"content": {"content命中"},
				"title":   {"title命中"},
			},
			want: "content命中",
		},
		{
			name:    "highlight_content_为空切片时跳到_title",
			content: "正文",
			highlight: map[string][]string{
				"content": {},         // 空切片，跳过
				"title":   {"title命中"},
			},
			want: "title命中",
		},
		{
			name:    "highlight_content_第一个元素为纯空白时跳到_title",
			content: "正文",
			highlight: map[string][]string{
				"content": {"   "}, // 纯空白，跳过
				"title":   {"title命中"},
			},
			want: "title命中",
		},

		// ── 回退到 content 截断场景 ─────────────────────────────────────────────
		{
			name:      "无_highlight_短内容原样返回",
			content:   "这是一段较短的内容",
			highlight: nil,
			want:      "这是一段较短的内容",
		},
		{
			name:      "无_highlight_内容恰好_50_个_rune_不截断",
			content:   strings.Repeat("好", 50),
			highlight: nil,
			want:      strings.Repeat("好", 50),
		},
		{
			name:      "无_highlight_内容超过_50_个_rune_时截断并加省略号",
			content:   strings.Repeat("好", 51),
			highlight: nil,
			want:      strings.Repeat("好", 50) + "...",
		},
		{
			name:      "内容首尾有空白应被_trim",
			content:   "  前后有空格  ",
			highlight: nil,
			want:      "前后有空格",
		},
		{
			name:      "content_为空字符串时返回空字符串",
			content:   "",
			highlight: nil,
			want:      "",
		},
		{
			name:      "content_为纯空白时返回空字符串",
			content:   "   ",
			highlight: nil,
			want:      "",
		},

		// ── 混合边界场景 ─────────────────────────────────────────────────────────
		{
			name:    "highlight_map_存在但目标字段均缺失则回退_content",
			content: "正文内容回退",
			highlight: map[string][]string{
				"other_field": {"不相关字段"},
			},
			want: "正文内容回退",
		},
		{
			name:    "空_highlight_map_回退到_content",
			content: "回退内容",
			highlight: map[string][]string{},
			want:    "回退内容",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := buildSearchSummary(tt.content, tt.highlight)
			assert.Equal(t, tt.want, got)
		})
	}
}
