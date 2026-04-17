# TODO List

## P1 — 功能与架构

### 网关 OpenAPI 对齐
- `/admin/*` 路径下所有后台管理 API 在 `gateway.api` 中均使用 `Empty` 占位，Go logic 层全是 TODO
- 建议：梳理完后统一重新生成网关代码并实现代理

### 用户域接口迁移策略
- 关注列表、粉丝列表、好友列表、个人互动历史等仍由 Java 承担
- 建议：明确哪些接口短期留 Java、哪些迁 `user-rpc`

### 接口回归（至少覆盖）
- `/api/user/search`、`/api/post/list?tab=follow`
- `/api/message/unread-count`、`/api/comment/list`
- `/api/post/search`、`/api/post/search/suggest`

---

## P2 — 工程质量

### Mongo 索引审计
- `comments`、`comment_likes`、`notifications`、`posts`、`user_follows` 集合存在 Java/Go 双写索引冲突风险
- 已有脚本：`scripts/audit_mongo_indexes.sh`

### comment 事件模型扩展（可选）
- 删除事件可补充 `deleteMode / isRoot / operatorRole / operatorUserId` 字段

### 服务发现多实例验证（可选）
- Go 侧已接入 etcd；如需展示微服务治理能力可继续做 Java 侧适配

### 推荐流升级（可选）
- 当前为热度排序 + 关注流；后续可考虑标签召回、协同过滤、用户画像

---

## P3 — 自动化测试

### 测试规范
- 框架：Go 原生 `testing` + `testify/assert`（前置断言用 `require`）
- 命名：场景化，如 `TestCreateComment_MQPublishFailed`
- 隔离：L1 不连真实数据库；L2 用 fake/stub/mtest/httptest；L3 用 docker compose
- 结构：优先表驱动；每修复一个真实 bug 补一条对应测试
- 分层：**L1** 纯函数/DTO/映射 | **L2** handler/logic/fake 依赖 | **L3** 跨服务冒烟

---

### 测试完成情况

#### L1（已全部完成）

| 文件 | 覆盖内容 |
|------|---------|
| `gateway-api/ctxutil_test.go` | UserID/Role/Nickname/Token/RequestID/TraceID 提取，类型错误回退 |
| `comment-rpc/comment_doc_test.go` | `ToProto()` 全字段映射，含 ReplyToUserId/ReplyToUserNickname |
| `comment-rpc/mq_event_test.go` | `requestIDFromContext`，nil/无metadata/x-request-id/x-trace-id 优先级 |
| `comment-rpc/delete_comment_logic_test.go` | 规则锁定：软删不发 MQ、权限条件组合（保留，不扩散） |
| `search-rpc/suggestLogic_test.go` | `collectSuggestFallbacks`，大小写/title/tags/空关键词 |
| `search-rpc/search_score_test.go` | `buildSearchSummary`，highlight优先/截断/trim/多字节边界 |
| `search-rpc/suggest_dedup_test.go` | `assembleSuggestions`，原词优先/仅有原词清空/去重/高亮优先/最多10条 |
| `notification-rpc/notification_doc_test.go` | `FormatNotificationType`/`ParseNotificationType` 全类型双向映射 |
| `sync-sidecar/reindex_test.go` | `beginReindex/finishReindex` 并发互斥，10 goroutine 仅1个成功 |

#### L2（已完成）

| 文件 | 覆盖内容 |
|------|---------|
| `gateway-api/auth_test.go` | 无/无效/过期 token→401；有效 JWT 注入 userId/role/nickname；Cookie 兼容；公开路径弱鉴权；refresh token 被拒 |
| `gateway-api/proxy_test.go` | 用户头透传；匿名不发头；上游不可达/非法URL→502；状态码与body透传 |
| `sync-sidecar/sync_http_test.go` | 非POST→405；Token错→403；并发冲突→409 |
| `sync-sidecar/sync_event_routing_test.go` | PostCreate→ES index；PostDelete→ES delete；未知类型/缺失TypeId不调用ES；审核模式跳过；非法JSON不调ES；ES404不panic |
| `comment-rpc/create_comment_logic_test.go` | Mongo不可用；违规词→InvalidArgument；非法postId；回复字段写入（mtest） |
| `comment-rpc/delete_comment_logic_l2_test.go` | Mongo不可用；非法commentId；权限拒绝；管理员可删；根评论软删；子评论硬删；like清理失败 |
| `comment-rpc/list_sub_comments_logic_test.go` | 分页参数兜底；匿名态isLiked全false；登录态isLiked聚合；空结果 |
| `notification-rpc/notification_logic_test.go`（追加） | MarkBatchRead 幂等性；upsert 覆盖更新 isRead 重置为 false；COMMENT 类型走 CreateNotification 不走 upsert |
| `sync-sidecar/sync_http_test.go`（追加） | AdminToken 为空时行为锁定（空==空放行，409 冲突返回） |
| `sync-sidecar/sync_event_routing_test.go`（追加） | PostUpdateEvent Mongo 查不到走 handleDelete → 调用 ES DELETE |
| `gateway-api/middleware/requestid_proxy_test.go` | RequestIdMiddleware 生成/透传 requestId；联合 ProxyHandler 验证 X-Request-Id/X-Trace-Id 到上游透传 |
| `search-rpc/suggest_integration_test.go` | 空关键词不调ES；5xx透传错误；0命中原词不插入；有高亮原词在前；非法JSON解码错误；仅有原词清空 |
| `notification-rpc/notification_logic_test.go` | GetUnreadCount；Create/Upsert（nil payload/正常/重复）；MarkAllRead；MarkBatchRead（空ids/非法ids） |
| `notification-rpc/list_notifications_logic_test.go` | 分页参数兜底；空结果；字段映射；未知类型映射为UNKNOWN |

#### L3（已完成）

| 脚本 | 覆盖链路 |
|------|---------|
| `scripts/smoke/01_post_to_es.sh` | 发帖 → sidecar → ES 可搜索 → 建议词含原词 |
| `scripts/smoke/02_comment_soft_delete.sh` | 一级评论软删后列表可见占位文案 |
| `scripts/smoke/03_notification_unread.sh` | 点赞触发通知 → 未读数增加 → 全部已读归零 |
| `scripts/smoke/04_sub_comment_reply_count.sh` | 子评论删除后根评论 replyCount 正确扣减 |
| `scripts/smoke/05_auth_cookie_vs_header.sh` | Cookie 鉴权与 Header 鉴权两种路径打通评论/通知接口；无 Token 公开接口不 401 |
| `scripts/smoke/run_all.sh` | 汇总执行入口 |

---

### 当前状态说明

#### L2 测试
已补齐大部分高价值测试（见上方已完成表格），待进一步 review / 收敛。

#### L3 smoke 脚本
已有所有 5 条脚本的初版，已按真实网关契约修正（端口 8090、`/api/auth/login`、Cookie 名 `accessToken`、`POST /api/post/`、`POST /api/comment/`、`DELETE /api/comment/:id`）。脚本依赖真实运行环境，需 `docker compose up` 后执行验证。

---

## 已完成功能

- 评论 / 通知 RPC 拆分（comment-rpc、notification-rpc）
- 评论链路一致性修复（MQ 回滚、replyCount 扣减、软删不扣 commentCount）
- 搜索与建议词优化（hot 权重、原词优先策略）
- 网关鉴权增强（Cookie 回退、用户头透传、javaproxy 平滑代理）
- 可观测性建设（OpenTelemetry、Jaeger、Loki + Grafana、统一日志字段）
- 文档体系（README、intern_prepare.md、observability.md、user_events.md）
- 工具脚本（audit_mongo_indexes.sh、verify_full_integration.sh、smoke/）
- 自动化测试体系（L1/L2 全层覆盖；L3 smoke 脚本已对齐真实网关契约）
