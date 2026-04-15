# TODO List

本文档记录当前代码库里已经确认、但尚未处理完的后端问题与优化项，按优先级划分，优先处理会直接影响功能正确性的事项。

## P1

### 检查前端 follow 流请求是否显式携带 credentials
- 现状：网关现在已经支持从 Cookie 中读取 `accessToken`，但如果前端跨域请求没有带 `credentials`，Cookie 仍然不会被发送。
- 影响：`/api/post/list?tab=follow` 可能继续表现为空列表或未登录。
- 涉及链路：
  - 前端请求配置
  - `services/gateway-api/internal/middleware/auth.go`
- 建议：确认前端在需要登录态的请求上使用 `credentials: 'include'` 或等价配置。

### 检查前端用户搜索请求是否在匿名态误调用
- 现状：`/api/user/search` 在 OpenAPI 里是强鉴权接口。
- 影响：如果前端在匿名态触发该请求，会被网关正常拦截，但产品表现可能像“接口异常”。
- 建议：前端在未登录时避免调用，或改成显式登录后才展示用户搜索入口。

## P2

### 为 Mongo 老集合补一个索引审计脚本
- 现状：`comment-rpc` 和 `notification-rpc` 的问题都暴露出：Go 新服务接管 Java 老集合时，索引兼容性是高风险点。
- 风险：
  - 旧索引名与新服务不一致会触发 `IndexOptionsConflict`
  - 新唯一索引覆盖范围过大时会被历史数据卡住
- 建议：增加一个脚本，一次性列出 `comments/comment_likes/notifications/posts/user_follows` 等集合的：
  - 索引名
  - 键模式
  - unique
  - partialFilterExpression

### 评估 comment-rpc 删除事件模型是否需要结构化扩展
- 现状：当前评论删除事件字段较少，下游只能根据 `type/commentId/postId/parentId` 猜测语义。
- 影响：随着评论域继续演进，软删、级联删、管理员删、作者删等情况可能越来越难区分。
- 涉及文件：
  - `services/comment-rpc/internal/logic/common.go`
  - `services/platform-java/src/main/java/com/szu/afternoon3/platform/event/CommentEvent.java`
- 建议：后续考虑为事件增加 `deleteMode`、`isRoot`、`operatorRole` 等字段。

### 为 comment-rpc / notification-rpc 增加更多联调与回归脚本
- 现状：目前已有基础联调脚本，但还没有覆盖删除一级评论、通知去重 upsert、异常权限校验等边界场景。
- 建议：后续补：
  - 一级评论软删回归
  - 通知重复 upsert 回归
  - 评论权限校验回归
  - Cookie / Header 双登录态验证脚本
  - RPC 启动后的索引兼容性回归

### 统一 Go / Java 日志字段并接入集中日志方案
- 现状：日志已经部分结构化，但 `service`、`traceId/requestId`、`routingKey` 等字段尚未完全统一，排障仍依赖分散的容器日志。
- 建议：
  - 统一 `service` 字段
  - 统一 `traceId / requestId`
  - MQ 发布/消费日志统一输出 `routingKey`
  - 后续接入 `Loki + Promtail + Grafana`

### 接入 OpenTelemetry + Jaeger，补齐链路追踪
- 现状：当前已经具备 metrics 和 logs，且 `service`、`traceId/requestId`、`routingKey` 的统一工作已基本完成，但还没有真正的分布式 tracing。
- 目标：形成 `Metrics + Logs + Traces` 的完整可观测性闭环，能在发帖、评论、通知、搜索、MQ 同步等链路上按 trace 追踪跨服务调用。
- 第一阶段建议范围：
  - `gateway-api -> comment-rpc`
  - `gateway-api -> notification-rpc`
  - `gateway-api -> search-rpc`
  - `gateway-api -> user-rpc`
  - `platform-java` HTTP 入站链路
  - `sync-sidecar` MQ 消费链路
- 落地步骤：
  - 在 `docker-compose.yml` 中增加 `jaeger`（all-in-one），开放 `16686`、`4317`、`4318`
  - Go 侧接入 OpenTelemetry SDK 和 OTLP exporter
  - 为 `gateway-api` 增加 HTTP tracing 中间件
  - 为 Go RPC client/server 增加 tracing interceptor
  - `platform-java` 优先用 OpenTelemetry Java agent 接入
  - MQ 发布/消费补充 `traceparent` / `tracestate` 传播
  - `sync-sidecar` 在 MQ handler 中恢复 trace context 并创建消费 span
- 验证目标：
  - 在 Jaeger UI 中看到 `gateway -> rpc -> java / sidecar` 的调用链
  - 能定位发帖 -> MQ -> ES、评论 -> 通知、搜索调用等典型链路

## 已完成

### 评论删除链路一致性修复
- `comment-rpc` 现在会在一级/二级评论删除后统一发布 `comment.delete` 事件。
- `comment-rpc` 删除时会主动清理当前评论的点赞关系。
- Java `CommentEventListener` 不再重复扣减子评论对应的根评论 `replyCount`，避免双重递减。

### 用户搜索排序优化
- `/api/user/search` 已增加搜索匹配权重：
  - 昵称完全命中
  - 昵称前缀命中
  - 昵称包含
  - 邮箱前缀命中
  - 邮箱包含
- 关注关系排序现在作为次级排序因子，而不是唯一排序依据。

### comment-rpc 索引兼容修复
- 已对齐 `comment_likes` 集合的旧索引名：
  - `idx_user_comment_unique`
  - `commentId`
- 避免了 Go 新服务接管后因索引名不一致导致的 `IndexOptionsConflict` 启动失败。

### notification-rpc 唯一索引范围修复
- `notifications` 集合的唯一索引已经改成部分唯一索引（partial unique index）。
- 现在仅对状态型通知做唯一约束：
  - `LIKE_POST`
  - `COLLECT_POST`
  - `RATE_POST`
  - `LIKE_COMMENT`
  - `FOLLOW`
- `COMMENT` / `REPLY` / `SYSTEM` 等历史型通知不再被错误纳入唯一约束。

### 网关鉴权支持 Cookie 回退
- `gateway-api` 现在在读取登录态时：
  - 优先读取 `Authorization`
  - 取不到时回退读取 `accessToken` / `refreshToken` Cookie
- 修复了 `follow` 流和用户搜索在前端使用 Cookie 登录态时的弱鉴权/强鉴权问题。
