package handler

import (
	"sync"
	"sync/atomic"
	"testing"

	"github.com/stretchr/testify/assert"
)

// TestSyncHandler_ReindexConcurrencyGuard 测试 beginReindex/finishReindex 的并发保护逻辑。
// SyncHandler 零值即可使用，无需初始化 Infra 字段。
func TestSyncHandler_ReindexConcurrencyGuard(t *testing.T) {
	t.Run("first_call_returns_true", func(t *testing.T) {
		h := &SyncHandler{}
		got := h.beginReindex()
		assert.True(t, got, "首次调用 beginReindex() 应返回 true")
	})

	t.Run("second_call_while_running_returns_false", func(t *testing.T) {
		h := &SyncHandler{}
		first := h.beginReindex()
		assert.True(t, first, "第一次调用应返回 true")

		second := h.beginReindex()
		assert.False(t, second, "未调用 finishReindex 时第二次调用应返回 false")
	})

	t.Run("after_finish_returns_true_again", func(t *testing.T) {
		h := &SyncHandler{}
		h.beginReindex()
		h.finishReindex()

		got := h.beginReindex()
		assert.True(t, got, "调用 finishReindex 之后再次调用 beginReindex 应返回 true")
	})

	t.Run("concurrent_only_one_wins", func(t *testing.T) {
		h := &SyncHandler{}

		const goroutines = 10
		var (
			wg      sync.WaitGroup
			barrier = make(chan struct{}) // 用来让所有 goroutine 同时出发
			wins    int64                 // atomic 计数：有多少个 goroutine 拿到了锁
		)

		for i := 0; i < goroutines; i++ {
			wg.Add(1)
			go func() {
				defer wg.Done()
				<-barrier // 等待起跑信号
				if h.beginReindex() {
					atomic.AddInt64(&wins, 1)
				}
			}()
		}

		close(barrier) // 同时放行所有 goroutine
		wg.Wait()

		assert.Equal(t, int64(1), atomic.LoadInt64(&wins),
			"10 个 goroutine 并发调用 beginReindex，只有 1 个应返回 true")
	})
}
