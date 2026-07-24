package logic

import (
	"context"
	"errors"
	"testing"

	"interaction-rpc/interaction"
	"interaction-rpc/internal/svc"

	"github.com/alicebob/miniredis/v2"
	goredis "github.com/redis/go-redis/v9"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/zeromicro/go-zero/core/stores/redis"
)

func newInteractionTestContext(t *testing.T) (*svc.ServiceContext, *miniredis.Miniredis) {
	t.Helper()
	server := miniredis.RunT(t)
	return &svc.ServiceContext{
		Redis: redis.MustNewRedis(redis.RedisConf{
			Host: server.Addr(),
			Type: "node",
		}),
		RawRedis: goredis.NewClient(&goredis.Options{Addr: server.Addr()}),
	}, server
}

func TestLikePost_IdempotencyAndEventPublishing(t *testing.T) {
	const (
		postID = "post-1"
		userID = int64(9)
	)
	key := KeyPostLikeSet + postID

	t.Run("duplicate like does not publish", func(t *testing.T) {
		svcCtx, server := newInteractionTestContext(t)
		server.SAdd(key, "9")
		var published int
		logic := NewLikePostLogic(context.Background(), svcCtx)
		logic.publish = func(string, *InteractionEvent) error {
			published++
			return nil
		}

		resp, err := logic.LikePost(&interaction.InteractionRequest{UserId: userID, TargetId: postID})

		require.NoError(t, err)
		require.NotNil(t, resp)
		assert.Zero(t, published)
		members, err := server.SMembers(key)
		require.NoError(t, err)
		assert.Equal(t, []string{"9"}, members)
	})

	t.Run("new like removes dummy and publishes once", func(t *testing.T) {
		svcCtx, server := newInteractionTestContext(t)
		server.SAdd(key, DummyUserId)
		var (
			routingKey string
			gotEvent   *InteractionEvent
			published  int
		)
		logic := NewLikePostLogic(context.Background(), svcCtx)
		logic.publish = func(gotRoutingKey string, event *InteractionEvent) error {
			published++
			routingKey = gotRoutingKey
			gotEvent = event
			return nil
		}

		resp, err := logic.LikePost(&interaction.InteractionRequest{UserId: userID, TargetId: postID})

		require.NoError(t, err)
		require.NotNil(t, resp)
		assert.Equal(t, 1, published)
		assert.Equal(t, RoutingKeyCreate, routingKey)
		assert.Equal(t, &InteractionEvent{
			UserId:   userID,
			TargetId: postID,
			Type:     "LIKE",
			Action:   "ADD",
		}, gotEvent)
		hasDummy, err := server.SIsMember(key, DummyUserId)
		require.NoError(t, err)
		hasUser, err := server.SIsMember(key, "9")
		require.NoError(t, err)
		assert.False(t, hasDummy)
		assert.True(t, hasUser)
	})

	t.Run("mq failure keeps weak consistency success", func(t *testing.T) {
		svcCtx, server := newInteractionTestContext(t)
		server.SAdd(key, DummyUserId)
		logic := NewLikePostLogic(context.Background(), svcCtx)
		logic.publish = func(string, *InteractionEvent) error {
			return errors.New("mq unavailable")
		}

		resp, err := logic.LikePost(&interaction.InteractionRequest{UserId: userID, TargetId: postID})

		require.NoError(t, err)
		require.NotNil(t, resp)
		hasUser, memberErr := server.SIsMember(key, "9")
		require.NoError(t, memberErr)
		assert.True(t, hasUser)
	})
}
