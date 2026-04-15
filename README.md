# Sharely / 分享派后端

面向内容社区场景的混合微服务后端项目，核心目标不是演示简单 CRUD，而是尽量贴近真实业务中的账号体系、内容写入、互动读模型、搜索、事件驱动同步和可观测性建设。

当前项目采用渐进式拆分方案：

- `gateway-api` 作为统一 HTTP 入口
- `user-rpc`、`interaction-rpc`、`search-rpc`、`comment-rpc`、`notification-rpc` 作为 Go 微服务
- `platform-java` 继续承接帖子主业务、关注关系、后台管理、部分聚合接口和异步消费逻辑
- `sync-sidecar` 负责 MQ 消费、日志落库、Mongo/ES 异步同步

## 技术栈总览

### 后端框架

- `Go 1.25`
- `go-zero`
- `gRPC / zRPC`
- `Java 17`
- `Spring Boot 3`
- `MyBatis-Plus`
- `Spring Data MongoDB`

### 数据与中间件

- `MySQL 8`
- `MongoDB 6`
- `Redis Stack`
- `RabbitMQ 3.12`
- `Elasticsearch 8`
- `etcd`

### 观测与运维

- `OpenTelemetry`
- `Jaeger`
- `Loki`
- `Promtail`
- `Grafana`
- `Prometheus`
- `Docker Compose`

## 项目架构

### 1. 网关层

`gateway-api` 是唯一 HTTP 入口，负责以下事情：

- JWT 鉴权与弱登录态兼容
- `requestId / traceId` 生成和透传
- 统一错误包装与响应格式
- 将 HTTP 请求编排为 gRPC 调用或 Java 代理调用
- 聚合搜索、互动、评论、通知等跨服务数据

### 2. 领域服务层

- `user-rpc`
  - 账号注册、登录、刷新令牌、资料更新
  - refresh token 白名单、tokenVersion 即时失效
- `interaction-rpc`
  - 点赞、收藏、评分、评论点赞
  - Redis 互动读模型和缓存预热
- `search-rpc`
  - ES 搜索、联想词、搜索历史
  - 热度排序与搜索历史写入
- `comment-rpc`
  - 评论创建、删除、一级评论列表、子评论列表
  - 评论敏感词校验、评论事件投递
- `notification-rpc`
  - 通知未读数、列表、已读
  - 状态型通知去重 upsert

### 3. Java 主业务层

`platform-java` 当前仍是帖子和后台能力的主写服务，负责：

- 帖子发布、编辑、删除、详情
- 关注流、关注关系、用户列表聚合
- 后台审核、日志、统计
- MQ 事件消费后对帖子计数、通知语义、AI 回复等业务做异步处理

### 4. 持久化与读模型层

- `MySQL`
  - 用户主事实表
  - tokenVersion、账号状态等强一致字段
- `MongoDB`
  - 帖子、评论、通知、关注关系、搜索历史等文档型数据
- `Redis`
  - refresh token 白名单
  - tokenVersion 缓存
  - 点赞/收藏/评分等互动高频读模型
- `Elasticsearch`
  - 帖子搜索索引
  - 搜索建议
  - 搜索排序

### 5. 事件驱动与同步层

- `RabbitMQ`
  - 承接 `post.*`、`comment.*`、`interaction.*`、`user.update` 等事件
- `sync-sidecar`
  - 消费日志事件写入 Mongo
  - 消费帖子/用户更新事件同步 ES
  - 提供手动全量重建索引能力

## 服务交互逻辑

典型读链路：

1. 前端请求 `gateway-api`
2. 网关判断该接口是直连 Go RPC 还是代理 Java
3. 如果需要聚合，则分别调用 `search-rpc`、`interaction-rpc`、`comment-rpc`、`notification-rpc`
4. 网关统一返回前端视图模型

典型写链路：

1. 主写服务先写主存储
2. 再通过 RabbitMQ 发布领域事件
3. 由 Java listener 或 `sync-sidecar` 消费处理
4. 更新计数、通知、ES 索引等冗余数据

这套方案没有强行引入分布式事务，而是采用“主写成功 + 异步补偿 + 最终一致性”的设计。

## 设计思路

### 为什么采用 Go + Java 混合架构

- Java 侧原有帖子、后台和复杂业务逻辑已经较完整，直接保留能降低迁移成本
- Go 更适合承担高并发网关、账号、互动读模型、搜索和轻量 RPC 服务
- 渐进式拆分比一次性重写风险更低，适合实战项目演进

### 为什么选择 go-zero

- 自带 API / RPC 脚手架，适合快速建立规范化微服务结构
- 与 etcd、zRPC、middleware 链路集成较顺滑
- 对于 Go 实习项目来说，既能体现工程化，也不会引入过重框架复杂度

### 为什么帖子主业务暂时保留在 Spring Boot

- 内容发布、审核、后台管理、关注流和历史接口原本就在 Java 中，迁移边界更复杂
- 保留 Java 作为业务核心，可以专注把高并发、读模型和外围能力逐步迁到 Go
- 这种拆法更符合真实业务系统中的“旧系统迁移”场景

### 如何处理分布式事务问题

项目没有采用 2PC/TCC。

当前方案是：

- 主写路径只保证本地事务成功
- 冗余更新通过 MQ 解耦
- ES、计数、通知等都通过异步消费者更新
- 对关键写链路增加幂等、去重、失败回滚和重试机制

例如：

- 评论创建在 `comment-rpc` 中若 MQ 发布失败，会回滚 Mongo 插入，避免评论落库成功但通知/计数不同步
- 通知服务对点赞、收藏、关注类消息使用 `upsert` + 唯一键，避免重复通知
- sidecar 对 ES 写入增加重试和手动全量重建入口

### Redis 在项目中的使用方式

Redis 不是简单拿来做缓存，而是分别承担了三类职责：

1. 会话控制
   - refresh token 白名单
   - blocked token 黑名单
   - tokenVersion 快速校验

2. 高频互动读模型
   - 点赞、收藏使用 Set
   - 评分使用 Hash
   - 支持批量聚合帖子互动状态

3. 缓存保护
   - Dummy 占位防止缓存穿透
   - 短锁防止并发预热击穿
   - Bloom Filter 减少无意义预热

## 技术亮点

- 渐进式微服务拆分：评论、通知等能力从 Java 中平滑迁出，避免一次性重构
- 网关聚合编排：将搜索结果、实时互动态、评论和通知状态统一聚合为前端可直接消费的数据结构
- 最终一致性落地：以 RabbitMQ 为中心解耦主写链路和冗余同步链路
- Redis 互动读模型：用 Set / Hash + Dummy + Bloom + 预热锁，解决热点查询、空值穿透和缓存抖动
- 评论删除语义细分：一级评论软删除、二级评论物理删除，兼顾产品体验和计数一致性
- 通知去重模型：对状态型通知做唯一键 upsert，避免消息中心刷屏
- 本地可观测性完整：日志、trace、metrics 在本地开发环境中均可直接观测

## 核心能力列表

### 已完成

- 邮箱验证码注册登录
- JWT + refresh token + tokenVersion 会话体系
- 帖子发布、详情、推荐流、关注流
- 搜索、建议词、搜索历史
- 点赞、收藏、评分、评论点赞
- 评论创建、删除、分页查询
- 通知未读数、通知列表、已读
- Mongo -> ES 增量与全量同步
- Jaeger / Loki / Grafana 本地观测链路

### 当前仍由 Java 主导

- 帖子主写链路
- 关注关系与关注流查询
- 后台管理、审核、日志导出
- 评论/互动事件消费后的部分业务副作用

## 目录结构

```text
.
├── services/
│   ├── gateway-api
│   ├── user-rpc
│   ├── interaction-rpc
│   ├── search-rpc
│   ├── comment-rpc
│   ├── notification-rpc
│   ├── sync-sidecar
│   └── platform-java
├── proto/
├── deploy/
├── scripts/
├── docs/
└── docker-compose.yml
```

## 本地启动

### 1. 启动基础设施和 Go 服务

```bash
docker compose up -d --build
```

### 2. 宿主机启动 Java

```bash
cd services/platform-java
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

### 3. 打开观测面板

- Grafana: `http://localhost:3001`
- Jaeger: `http://localhost:16686`
- Prometheus: `http://localhost:9091`
- RabbitMQ 管理台: `http://localhost:15672`

## 服务端口

| 服务 | 端口 | 说明 |
| --- | --- | --- |
| gateway-api | 8090 | HTTP 网关 |
| platform-java | 8080 | Java 主业务服务 |
| interaction-rpc | 8081 / 40092 | HTTP metrics / gRPC |
| search-rpc | 8082 / 40093 | HTTP metrics / gRPC |
| user-rpc | 8083 / 40091 | HTTP metrics / gRPC |
| notification-rpc | 8084 / 40095 | HTTP metrics / gRPC |
| comment-rpc | 8085 / 40096 | HTTP metrics / gRPC |
| sync-sidecar | 18088 | 管理接口 |
| MySQL | 3306 | 关系型数据 |
| MongoDB | 27017 | 文档数据 |
| Redis | 6379 | 缓存与读模型 |
| RabbitMQ | 5672 / 15672 | MQ / 控制台 |
| Elasticsearch | 9200 | 搜索索引 |
| etcd | 2379 | 服务发现 |
| Jaeger | 16686 / 4318 | Trace UI / OTLP HTTP |

## 相关文档

- [AGENTS.md](./AGENTS.md)
- [shixi.md](./shixi.md)
- [docs/todo-list.md](./docs/todo-list.md)
- 各服务 README 位于 `services/*/README.md`
