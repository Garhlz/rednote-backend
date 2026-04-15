# TODO List

本文档整合了仓库里原有两份待办，仅保留当前版本里仍然值得继续推进的功能、风险和优化项。

## P1

### 完成网关 OpenAPI / `gateway.api` 与真实实现的最后一轮对齐

- 现状：
  - `/api/user/search` 已由网关补齐实现
  - `tag/hot`、`tag/ai-generate`、`message/unread-count`、`message/notifications`、`message/read`、`common/upload` 等核心返回结构也已补齐到 `gateway.api`
  - 但仍有部分后台接口和 Java 直代接口在 `gateway.api` 中使用占位返回结构
- 影响：
  - 文档、代码生成结果和真实接口返回不一致
  - 前端和 Apifox 容易继续基于旧结构联调
- 建议：
  - 继续梳理部分后台接口、开发辅助接口等当前仍走 Java 且返回结构未建模的接口
  - 在确认生成结果不会覆盖现有自定义实现后，统一重新生成网关代码

### 补齐用户域仍由 Java 承担的接口迁移策略

- 现状：
  - 用户搜索已能通过网关正确代理到 Java
  - 但关注列表、粉丝列表、好友列表、我的点赞/收藏/评分/历史/评论等接口仍主要依赖 Java
- 影响：
  - 用户域边界还不够清晰
  - 文档层面容易给人“已经完全迁到 Go”的误解
- 建议：
  - 明确哪些接口短期保留在 Java
  - 明确哪些接口后续适合迁到 `user-rpc` 或独立读模型服务

### 做一轮真实接口回归，确认文档与系统行为一致

- 现状：
  - README 和面试材料已经补齐
  - 但部分接口属于“网关代理 Java”的混合模式
- 建议至少回归：
  - `/api/user/search`
  - `/api/post/list?tab=follow`
  - `/api/message/unread-count`
  - `/api/comment/list`
  - `/api/post/search`
  - `/api/post/search/suggest`

## P2

### 为 Mongo 老集合补一个索引审计脚本

- 现状：
  - `comment-rpc`、`notification-rpc` 的迁移已经暴露出老集合索引兼容风险
- 风险：
  - 索引名冲突
  - 唯一索引和历史脏数据冲突
- 建议：
  - 增加脚本列出以下集合的索引定义：
    - `comments`
    - `comment_likes`
    - `notifications`
    - `posts`
    - `user_follows`

### 评估 comment 事件模型是否要继续结构化

- 现状：
  - 当前 `comment.create / comment.delete` 事件已能满足现有副作用
  - 但删除事件字段仍偏少
- 建议后续可扩展：
  - `deleteMode`
  - `isRoot`
  - `operatorRole`
  - `operatorUserId`

### 补一轮更完整的联调脚本

- 建议增加：
  - 一级评论软删回归
  - 二级评论删除后 `replyCount` 回归
  - 状态型通知重复 upsert 回归
  - Cookie / Header 双鉴权模式回归
  - sidecar 全量重建 ES 回归

### 为 user-rpc 补更完整的事件发布约定

- 现状：
  - 资料更新已经会发 `user.update`
  - 但删除/注销、状态变更等事件边界还不够系统
- 建议：
  - 明确 `user.update / user.delete / user.status.update` 的统一约定
  - 让 sidecar 和 Java 消费方更容易扩展

### 统一并固化观测看板

- 现状：
  - Jaeger、Loki、Promtail、Grafana 已经接入
  - 但查询方式和面板仍偏手动
- 建议：
  - 保存一套默认 Grafana dashboard
  - 预置按 `service / traceId / routingKey` 查询的 Loki panel
  - 预置按 HTTP / gRPC / MQ 分类的 Jaeger 使用说明

## P3

### 评估是否继续推进服务发现与多实例验证

- 现状：
  - Go 服务已接入 etcd
  - Java 仍主要依赖固定地址或网关代理
- 建议：
  - 如果后续目标是“展示微服务治理能力”，可以继续做 Java 侧服务发现适配
  - 如果目标主要是实习项目展示，可以保留现状，不必继续扩大复杂度

### 评估推荐流是否继续升级为更真实的推荐系统

- 现状：
  - 当前首页更接近“热度排序 + 关注流 + 搜索聚合”
- 后续方向：
  - 标签召回
  - 简单协同过滤
  - 行为分层打分
  - 用户画像和多路召回

## 已完成

### 评论 / 通知 RPC 拆分

- `comment-rpc` 已接管评论创建、删除、一级评论列表、子评论列表
- `notification-rpc` 已接管通知未读数、通知列表、已读和状态型通知写入

### 评论链路一致性修复

- 修复评论创建时 MQ 发布失败仍返回成功的问题
- 修复二级评论删除导致根评论 `replyCount` 被重复扣减的问题
- 修复一级评论软删除仍发送 delete 事件导致 `post.commentCount` 错扣的问题

### 搜索与建议词优化

- 已优化 `hot` 排序权重
- 已增加“原词优先但不无脑插入”的建议词策略

### 网关鉴权与代理增强

- 支持从 Cookie 回退读取 `accessToken / refreshToken`
- Java 代理支持透传 `X-User-Id / X-User-Role / X-User-Nickname`
- `/api/user/search` 已在网关层补齐实现，并已切到自定义 handler，不再走旧的空逻辑或裸代理

### 可观测性建设

- Go 服务已接入 OpenTelemetry
- Java 已可通过脚本接入 Jaeger
- Loki + Promtail + Grafana 已接入
- `service / traceId / requestId / routingKey` 日志字段已基本统一
