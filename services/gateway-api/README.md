# gateway-api

`gateway-api` 是整个系统的统一 HTTP 入口。它不承担重业务落库，而是负责鉴权、请求编排、结果聚合和协议整形。

## 服务职责

- 暴露统一 REST API
- 校验 JWT，并兼容公开接口上的弱登录态
- 将 HTTP 请求路由到 Go RPC 或 Java 服务
- 聚合 `search-rpc`、`interaction-rpc`、`comment-rpc`、`notification-rpc` 等结果
- 统一输出 `requestId / traceId`、指标和结构化日志

## 接口规范

### 认证与用户

| 路径 | 方法 | 说明 |
| --- | --- | --- |
| `/api/auth/send-code` | `POST` | 发送邮箱验证码 |
| `/api/auth/register` | `POST` | 注册 |
| `/api/auth/login` | `POST` | 登录 |
| `/api/auth/refresh` | `POST` | 刷新 access token |
| `/api/auth/logout` | `POST` | 登出 |
| `/api/user/profile` | `GET` | 获取当前用户资料 |
| `/api/user/profile` | `PUT` | 更新当前用户资料 |
| `/api/user/:userId/profile` | `GET` | 获取公开用户资料 |

调用示例：

```bash
curl -X POST 'http://localhost:8090/api/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{
    "email": "demo@example.com",
    "password": "123456"
  }'
```

### 搜索与帖子

| 路径 | 方法 | 说明 |
| --- | --- | --- |
| `/api/post/list` | `GET` | 首页推荐/列表 |
| `/api/post/search` | `GET` | 搜索帖子 |
| `/api/post/search/suggest` | `GET` | 搜索建议词 |
| `/api/post/:postId` | `GET` | 帖子详情 |
| `/api/post/` | `POST` | 创建帖子，实际由 Java 主写 |
| `/api/tag/hot` | `GET` | 热门标签 |

调用示例：

```bash
curl 'http://localhost:8090/api/post/search?keyword=test&sort=hot&page=1&size=24'
```

### 评论与消息

| 路径 | 方法 | 说明 |
| --- | --- | --- |
| `/api/comment/` | `POST` | 创建评论 |
| `/api/comment/:id` | `DELETE` | 删除评论 |
| `/api/comment/list` | `GET` | 一级评论列表 |
| `/api/comment/sub-list` | `GET` | 子评论列表 |
| `/api/message/unread-count` | `GET` | 未读通知数 |
| `/api/message/notifications` | `GET` | 通知列表 |
| `/api/message/read` | `POST/PUT` | 单条/批量已读 |

调用示例：

```bash
curl 'http://localhost:8090/api/comment/list?postId=693d0f62add946254e35d2b5&page=1&size=10'
```

## 技术实现

### 鉴权策略

- 强鉴权接口必须携带 access token
- 公开接口支持弱登录态
  - 如搜索、列表、评论列表允许匿名访问
  - 但若请求携带合法 token，网关仍会注入 `userId`
  - 这样前端能拿到 `isLiked / isCollected / isFollowed`
- 登出接口同时透传 access token 和 refresh token，便于下游做拉黑和白名单删除

### 聚合方式

- 帖子搜索：
  - 先调用 `search-rpc` 获取搜索结果
  - 再调用 `interaction-rpc` 批量补齐实时互动字段
- 评论列表：
  - 直接调用 `comment-rpc`
  - 由 comment-rpc 返回已补齐的作者、点赞态和子评论预览
- 通知列表：
  - 直接调用 `notification-rpc`
- 帖子主写、详情、后台：
  - 继续走 Java 代理或 Java 聚合能力

### 观测能力

- 中间件统一生成 `requestId / traceId`
- OpenTelemetry 记录 HTTP server span
- 请求耗时、状态码通过 Prometheus 指标暴露
- 结构化日志统一输出 `service / trace / span`

## 技术亮点

- 公开接口支持弱登录态，兼顾匿名访问与个性化字段补齐
- 聚合职责集中在网关，前端无需自行拼装多服务数据
- 统一 trace / requestId 透传，便于跨 HTTP、gRPC、MQ 排查问题
- 通过 Go 网关逐步替代 Java adapter，迁移成本低、边界清晰

## 依赖关系

- 上游：Web / App / 管理后台
- 下游：
  - `user-rpc`
  - `interaction-rpc`
  - `search-rpc`
  - `comment-rpc`
  - `notification-rpc`
  - `platform-java`

## 本地运行

```bash
cd services/gateway-api
go build ./...
```

通过 Docker 启动：

```bash
docker compose up -d --build gateway-api
```
