# notification-rpc

`notification-rpc` 是从 `platform-java` 中拆出来的通知微服务，当前负责通知读写存储与通知读模型查询。

## 服务职责

- 维护 MongoDB 中的 `notifications` 集合
- 提供通知读接口
  - 未读数
  - 通知列表
  - 全部已读
  - 批量已读
- 提供通知写接口
  - 普通通知插入
  - 状态类通知去重 upsert
- 提供历史重复通知清洗接口

## 当前拆分边界

这次拆分采用的是“先拆存储与读写接口，再拆事件消费”的方式。

也就是说：

- `gateway-api` 读取通知时，已经直接走 `notification-rpc`
- `platform-java` 里的 listener 仍然负责根据业务事件组装通知语义
- 但最终通知写入已经改成通过 gRPC 调用 `notification-rpc`

当前链路可以理解为：

```text
客户端
  -> gateway-api
  -> notification-rpc
  -> Mongo notifications

platform-java listeners
  -> notification-rpc
  -> Mongo notifications
```

这样做的好处是：

- 迁移风险更小
- 前端协议几乎不用改
- Java 原有通知生成逻辑可以平滑复用
- 后续仍可继续把 MQ 消费和通知生成逻辑迁到 Go

## 读写模型

### 1. 读模型

客户端当前对通知仍然是拉模型，也就是轮询读取：

- `GetUnreadCount`
- `ListNotifications`
- `MarkAllRead`
- `MarkBatchRead`

这些接口都直接读取或更新 `notifications` 集合。

### 2. 写模型

写入分为两类：

- `CreateNotification`
  - 用于评论、回复、系统通知等“逐条保留”的消息
- `UpsertNotification`
  - 用于点赞、收藏、评分、关注等“状态型通知”
  - 按 `receiverId + senderId + type + targetId` 去重

这样可以避免“同一个人连续点赞同一篇内容”在消息中心里堆出很多重复记录。

## Mongo 文档结构

当前通知文档核心字段如下：

- `receiverId`
- `senderId`
- `senderNickname`
- `senderAvatar`
- `type`
- `targetId`
- `targetPreview`
- `isRead`
- `createdAt`

其中：

- `receiverId` 表示谁会收到这条通知
- `senderId` 表示谁触发了这次动作
- `targetId` 表示被作用的业务对象
- `targetPreview` 用于消息列表摘要展示

## 通知类型

当前已支持的通知类型包括：

- `COMMENT`
- `REPLY`
- `LIKE_POST`
- `COLLECT_POST`
- `RATE_POST`
- `LIKE_COMMENT`
- `FOLLOW`
- `SYSTEM`
- `SYSTEM_AUDIT_PASS`
- `SYSTEM_AUDIT_REJECT`
- `SYSTEM_POST_DELETE`

## 与 platform-java 的关系

`platform-java` 当前没有直接写 `notifications` 集合，而是通过 `NotificationServiceImpl` 做 gRPC 适配。

因此原有的：

- `InteractionEventListener`
- `CommentEventListener`
- `PostAuditListener`
- `PostEventListener`

不需要大改调用方式，仍然可以继续调用：

- `notificationService.save(...)`
- `notificationService.markAllAsRead(...)`
- `notificationService.getMyNotifications(...)`

只是底层实现已经改为走 `notification-rpc`。

## Prometheus 指标

当前服务暴露独立 metrics 端口，默认：

- `40095`

Prometheus 抓取路径：

- `/metrics`

当前主要指标：

- `notification_rpc_requests_total`
- `notification_rpc_request_duration_seconds`

标签设计：

- `action`
  - `get_unread_count`
  - `list_notifications`
  - `mark_all_read`
  - `mark_batch_read`
  - `create_notification`
  - `upsert_notification`
  - `clean_duplicate_notifications`
- `result`
  - `success`
  - `mongo_error`
  - `count_error`
  - `find_error`
  - `decode_error`
  - `aggregate_error`
  - `delete_error`
  - `empty_payload`
  - `empty_ids`
  - `invalid_ids`

## 本地运行

### 编译

```bash
cd services/notification-rpc
go build ./...
```

### 运行

```bash
cd services/notification-rpc
go run notification.go -f etc/notification.yaml
```

### 通过 Docker Compose 启动

```bash
docker compose up -d --build notification-rpc
```

## 后续可继续优化的方向

- 把通知事件消费逻辑也从 Java listener 迁到 Go
- 给 `notifications` 集合补合适索引
  - `receiverId + createdAt`
  - `receiverId + isRead`
  - `receiverId + senderId + type + targetId`
- 进一步接入链路追踪
- 后续如果前端需要，可在此基础上增加 SSE / WebSocket 推送能力
