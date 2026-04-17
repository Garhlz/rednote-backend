package logic

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestCollectSuggestFallbacks(t *testing.T) {
	tests := []struct {
		name     string
		title    string
		tags     []string
		keyword  string
		expected []string
	}{
		{
			name:     "keyword in title only",
			title:    "Hello World",
			tags:     []string{"news", "tech"},
			keyword:  "world",
			expected: []string{"Hello World"},
		},
		{
			name:     "keyword in tags only",
			title:    "Unrelated Title",
			tags:     []string{"golang", "microservices"},
			keyword:  "go",
			expected: []string{"golang"},
		},
		{
			name:     "keyword in both title and tags",
			title:    "Go is awesome",
			tags:     []string{"golang", "programming"},
			keyword:  "go",
			expected: []string{"Go is awesome", "golang"},
		},
		{
			name:     "case insensitive match",
			title:    "SPRING BOOT Tutorial",
			tags:     []string{"Java", "Spring"},
			keyword:  "spring",
			expected: []string{"SPRING BOOT Tutorial", "Spring"},
		},
		{
			name:     "empty keyword",
			title:    "Some Title",
			tags:     []string{"tag1"},
			keyword:  "   ",
			expected: []string{}, // should return empty slice, not panic or match everything
		},
		{
			name:     "no match",
			title:    "Random stuff",
			tags:     []string{"abc"},
			keyword:  "xyz",
			expected: []string{},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			actual := collectSuggestFallbacks(tt.title, tt.tags, tt.keyword)

			// use assert.ElementsMatch if order doesn't matter,
			// but here order matters (title first, then tags) so we use Equal
			if len(tt.expected) == 0 {
				assert.Empty(t, actual, "Expected empty slice")
			} else {
				assert.Equal(t, tt.expected, actual)
			}
		})
	}
}
