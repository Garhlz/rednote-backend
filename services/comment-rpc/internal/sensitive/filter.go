package sensitive

import (
	"embed"
	"strings"
	"sync"
)

//go:embed *.dat
var dictFS embed.FS

const xorKey = "REDNOTE_SECURE_2025"

var (
	once  sync.Once
	words []string
)

func loadWords() {
	entries, err := dictFS.ReadDir(".")
	if err != nil {
		return
	}

	key := []byte(xorKey)
	for _, entry := range entries {
		if entry.IsDir() || !strings.HasSuffix(entry.Name(), ".dat") {
			continue
		}
		data, err := dictFS.ReadFile(entry.Name())
		if err != nil {
			continue
		}
		for i := range data {
			data[i] ^= key[i%len(key)]
		}
		for _, line := range strings.Split(strings.ReplaceAll(string(data), "\r\n", "\n"), "\n") {
			word := strings.TrimSpace(line)
			if word != "" {
				words = append(words, word)
			}
		}
	}
}

func FirstMatch(text string) string {
	once.Do(loadWords)
	if strings.TrimSpace(text) == "" {
		return ""
	}
	for _, word := range words {
		if strings.Contains(text, word) {
			return word
		}
	}
	return ""
}
