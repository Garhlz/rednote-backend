# interaction-rpc

`interaction-rpc` 负责帖子和评论的高频互动能力，是项目里 Redis 读模型最集中的服务。

## 服务职责

- 帖子点赞 / 取消点赞
- 帖子收藏 / 取消收藏
- 帖子评分
- 评论点赞 / 取消点赞
- 批量查询帖子互动状态
- 用户主页统计数据聚合
- 发布 `interaction.create / interaction.delete` 事件

## 接口规范

主要通过 `gateway-api` 暴露 HTTP。

| 网关路径 | 方法 | 说明 |
| --- | --- | --- |
| `/api/interaction/like/post` | `POST` | 点赞帖子 |
| `/api/interaction/unlike/post` | `POST` | 取消点赞帖子 |
| `/api/interaction/collect/post` | `POST` | 收藏帖子 |
| `/api/interaction/uncollect/post` | `POST` | 取消收藏帖子 |
| `/api/interaction/rate/post` | `POST` | 给帖子评分 |
| `/api/interaction/like/comment` | `POST` | 点赞评论 |
| `/api/interaction/unlike/comment` | `POST` | 取消点赞评论 |

调用示例：

```bash
curl -X POST 'http://localhost:8090/api/interaction/like/post' \
  -H 'Authorization: Bearer <access-token>' \
  -H 'Content-Type: application/json' \
  -d '{"targetId":"69cd52ee9535d61382e92951"}'
```

## 技术实现

### Redis 读模型

- 帖子点赞、收藏、评论点赞使用 `Set`
- 帖子评分使用 `Hash`
- 查询帖子互动状态时不直接扫 Mongo，而是优先从 Redis 读

### 缓存保护策略

服务实现了完整的缓存保护组合：

- Dummy 占位
  - 避免空 key 被重复回源
- 短锁预热
  - 并发请求下只允许一个协程回源预热
- Bloom Filter
  - 判断某个目标是否“可能存在真实互动数据”
  - 若 Bloom 明确判断不存在，则可直接跳过预热

### 事件发布

- 成功写入 Redis 后发布 MQ 事件
- 事件中会注入 trace 上下文和 `routingKey`
- 下游 Java listener 可根据事件更新 Mongo 中的统计字段或生成通知

### 用户主页统计

`GetUserStats` 会聚合：

- followCount
- fanCount
- receivedLikeCount
- isFollowed

其中关注关系和累计获赞主要来自 Mongo 聚合。

## 技术亮点

- 不是“Redis 当缓存”，而是把 Redis 作为互动高频读模型
- `Dummy + Bloom + 短锁预热` 组合有效降低缓存穿透和击穿
- 批量接口 `BatchPostStats` 能一次性补齐帖子列表页需要的多个互动字段
- MQ 事件发布时同步透传 trace 信息，便于回溯一次互动对下游产生的影响

## 关键依赖

- `Redis`
- `MongoDB`
- `RabbitMQ`

## 本地运行

```bash
cd services/interaction-rpc
go build ./...
go test ./...
```

通过 Docker 启动：

```bash
docker compose up -d --build interaction-rpc
```
