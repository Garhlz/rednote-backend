# sync-sidecar

`sync-sidecar` 是项目里的异步同步和日志侧车服务，核心职责是消费 MQ 事件并维护 Mongo / ES 的冗余数据。

## 服务职责

- 消费日志事件并落库
- 消费帖子和用户更新事件，增量同步到 Elasticsearch
- 提供手动触发的全量重建接口
- 输出统一结构化日志和链路追踪

## 接口规范

该服务不对前端开放业务接口，只提供管理接口。

| 路径 | 方法 | 说明 |
| --- | --- | --- |
| `/admin/reindex/posts` | `POST` | 手动触发 Mongo -> ES 全量同步 |

调用示例：

```bash
curl --noproxy '*' -X POST 'http://127.0.0.1:18088/admin/reindex/posts' \
  -H 'X-Admin-Token: szu123'
```

## 技术实现

### 1. MQ 消费模型

当前绑定的队列和 routing key：

- `platform.log.queue`
  - `log.#`
- `platform.es.sync.queue`
  - `post.#`
  - `user.update`
  - `post.audit.pass`
- `platform.user.queue`
  - `user.#`

sidecar 启动后会为各队列启动固定 worker 并发消费。

### 2. ES 增量同步

- 监听帖子创建、更新、审核通过等事件
- 回查 Mongo 中的帖子主文档
- 组装 ES 索引文档写入 `posts` 索引
- 用户昵称/头像更新时，同步刷新帖子索引中的冗余作者信息

### 3. 全量重建

管理接口触发后会：

1. 校验 `X-Admin-Token`
2. 清空目标索引
3. 分批扫描 Mongo `posts`
4. 批量写入 Elasticsearch

适合以下场景：

- 初次接入 ES
- 历史增量消息丢失后修复索引
- 调整映射或排序策略后重建数据

### 4. 日志落库

- 消费 `log.#` 事件
- 将访问日志、操作日志统一存入 Mongo
- 便于后台检索、导出和审计

## 技术亮点

- 把“异步同步”和“主业务写链路”剥离开，避免 Java/Go 主流程里夹杂太多同步逻辑
- 既支持 MQ 增量同步，也支持 HTTP 手动全量重建，运维手段完整
- 统一消费层能集中处理 traceId、routingKey、失败重试日志
- 对 ES 写入失败场景有更好的定位能力，适合本地排查和扩展成生产方案

## 关键依赖

- `RabbitMQ`
- `MongoDB`
- `Elasticsearch`

## 本地运行

```bash
cd services/sync-sidecar
go build ./...
```

通过 Docker 启动：

```bash
docker compose up -d --build sync-sidecar
```
