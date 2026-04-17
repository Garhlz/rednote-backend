# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

总是使用简体中文进行回答、思考和注释

不需要帮我使用 git commit

## Commands

- **启动基础设施与 Go 服务**：`docker compose up -d --build`（启动 Redis、MongoDB、Elasticsearch、RabbitMQ、Etcd 及所有 Go 服务）
- **本地启动 Java 服务**：`./scripts/run-platform-java-with-otel.sh`（挂载 OpenTelemetry agent 运行 Spring Boot）
- **构建单个 Go 服务**：在服务目录下执行 `go build ./...`，例如 `cd services/gateway-api && go build ./...`
- **运行 Go 单元测试**：在服务目录下执行 `go test ./...`；新增依赖后需先执行 `go mod tidy`
- **运行单个测试函数**：`go test ./internal/... -run TestFunctionName -v`
- **MongoDB 索引审计**：`./scripts/audit_mongo_indexes.sh`（列出各集合现有索引，检查 Java/Go 双写下的索引冲突）
- **联调回归脚本**：`./scripts/verify_full_integration.sh`（评论软删、通知 upsert、Cookie 鉴权等场景回归）

## 项目架构

本项目是一个面向内容社区的 **Go + Java 双栈混合微服务后端**，采用渐进式演进策略：Java 核心服务保留高耦合的主业务写链路，高频读/高并发的领域逐步抽离为独立的 Go 服务。

### 核心组件

1. **API 网关（`services/gateway-api`）**
   - 统一 HTTP 入口，兼容强鉴权与弱登录态（匿名访问时仍可注入 userId 以补全互动状态字段）
   - 基于 `internal/handler/javaproxy/proxy.go` 的 `ProxyHandler` 将未迁移的接口（管理后台 `/admin/*`、用户复杂查询域）平滑反向代理回 Java，并自动透传 `X-User-Id / X-User-Role / X-User-Nickname / traceId` 等头
   - 已迁移到 Go 的路由走 zRPC 调用；其余全部走 `javaproxy`，不留空 TODO

2. **领域 RPC 服务（Go / go-zero / gRPC）**
   - **`user-rpc`**：注册、登录、Token（AccessToken + RefreshToken 双令牌 + Redis jti 即时失效）、邮箱验证码、资料更新
   - **`interaction-rpc`**：点赞、收藏、评分。Redis 作为读模型，异步与 MongoDB 保持最终一致
   - **`search-rpc`**：ES 全文检索、建议词（原词优先但不盲目插入策略）、搜索历史
   - **`comment-rpc`**：评论树（创建、软删/硬删分支、一级列表、子评论分页）；MQ 发布失败时回滚写入
   - **`notification-rpc`**：未读数、通知列表、状态型通知 upsert 去重（`uk_notifications_state_key` 唯一部分索引）

3. **Java 核心（`services/platform-java`，Spring Boot 3.3）**
   - 帖子主写链路、管理后台、关注/粉丝/好友列表、个人互动历史等尚未迁移的业务
   - 通过 RabbitMQ 发布领域事件（`platform.topic.exchange`）供 sync-sidecar 消费

4. **异步同步层（`services/sync-sidecar`）**
   - 消费 RabbitMQ 事件，将帖子增量/全量同步至 ES（`PostCreateEvent / PostUpdateEvent / PostAuditPassEvent / PostDeleteEvent`）
   - 同步用户资料变更至 Mongo 多集合冗余字段（`UserUpdateEvent / UserDeleteEvent`）
   - 提供管理 HTTP 接口 `POST /reindex/posts`（需 `X-Admin-Token`）触发全量重建，内置并发互斥保护

### 数据存储分工

| 存储 | 职责 |
|------|------|
| MySQL | 账号、认证，强事务数据 |
| MongoDB | 帖子、评论、通知、互动明细等文档数据 |
| Redis | JWT 失效控制（jti/版本）+ 高频互动读模型（点赞/收藏状态） |
| Elasticsearch | 帖子全文检索、热度排序、建议词 |
| RabbitMQ | 事件总线，主写与副作用解耦（最终一致性） |
| Etcd | Go 服务注册与发现 |

### 关键设计模式

- **双栈代理**：网关通过 `javaproxy` 屏蔽 Go/Java 分工细节，前端调用无感知
- **读写分离**：互动状态走 Redis 读模型，不每次回查 MongoDB
- **MQ 副作用隔离**：评论创建、帖子发布均先完成主写，再异步发布事件；事件发布失败则回滚主写
- **索引兼容规范**：Java（Spring Data `@CompoundIndex`）和 Go（`ensureIndexes` 启动时建索引）共用同一个 MongoDB 实例；Go 侧需与 Java 侧保持索引名一致，避免 `IndexOptionsConflict`；唯一索引建失败时记录 warning 而非 panic

### 可观测性

本地通过 Docker Compose 提供完整可观测栈：

- **Jaeger**（`:16686`）：分布式链路追踪（OpenTelemetry）
- **Grafana + Loki + Promtail**（`:3001`）：结构化日志，按 `service / traceId / routingKey` 查询
- **Prometheus**：指标采集

所有服务日志统一输出 `service`、`traceId`、`requestId`、`routingKey` 字段，支持跨 HTTP / gRPC / MQ 三类链路串联排查。详见 `docs/observability.md`。

### 测试规范（概要）

- **框架**：Go 原生 `testing` + `testify/assert`（前置断言用 `require`，其余用 `assert`）
- **命名**：场景化，如 `TestCreateComment_MQPublishFailed`、`TestSuggest_PreferOriginalKeyword`
- **隔离**：L1 单测绝不连真实数据库；需要依赖时用 fake/stub；集成测试用独立容器
- **结构**：优先表驱动，减少重复样板
- 详细规划见 `docs/todo-list.md` 的「补充完整的自动化测试规划与规范」章节