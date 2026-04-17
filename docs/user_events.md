# User-RPC RabbitMQ 事件定义 (user_events.md)

本文档定义了 `user-rpc` 服务在状态变更时发布到 RabbitMQ 的事件约定。这些事件基于领域驱动设计（DDD）的发布-订阅模式，用于系统解耦，如搜索索引更新、用户缓存失效、用户动态同步等。

## 1. 交换机与路由配置

*   **交换机 (Exchange)**: `user.topic.exchange`
*   **交换机类型 (Type)**: `topic`
*   **持久化 (Durable)**: `true`

## 2. 事件类型及路由键

所有的事件都应包含标准的追踪字段，以便于在分布式追踪系统（如 Jaeger/Loki）中进行全链路排查。

### 2.1 用户信息更新事件 (`user.update`)

当用户的基本资料（如昵称、头像、简介等）发生更新时触发。

*   **路由键 (Routing Key)**: `user.event.update`
*   **触发时机**: `UpdateUserProfile` 或相关信息修改接口成功返回后。
*   **消费方示例**:
    *   `interaction-rpc`: 更新互动缓存中的冗余用户头像/昵称。
    *   `search-rpc`: 更新用户的全文检索索引（如果需要搜用户）。
    *   `sync-sidecar`: 记录变更审计日志。

**JSON Payload 结构**:

```json
{
  "eventId": "evt_1a2b3c4d5e6f",        // 全局唯一事件ID (UUID)
  "eventType": "user.update",           // 事件类型标识
  "timestamp": 1713254400,              // 事件发生时间 (Unix时间戳, 秒)
  "traceId": "abc123def456ghi789",      // OpenTelemetry Trace ID，用于链路追踪
  "payload": {
    "userId": "10001",                  // 发生变更的用户ID
    "updatedFields": ["nickname", "avatar"], // 明确指出的变更字段列表，方便消费者按需处理
    "snapshot": {                       // 变更后的完整用户数据快照 (部分敏感字段剔除)
      "nickname": "新昵称",
      "avatar": "https://cdn.example.com/avatar/new.png",
      "signature": "热爱生活",
      "gender": 1                       // 0:未知, 1:男, 2:女
    }
  }
}
```

### 2.2 用户删除事件 (`user.delete`)

当用户主动注销或被管理员强制删除时触发。支持软删除和硬删除标志。

*   **路由键 (Routing Key)**: `user.event.delete`
*   **触发时机**: 用户账号注销流程完成，或管理员执行硬删除后。
*   **消费方示例**:
    *   `comment-rpc`: 隐藏该用户的所有历史评论或标记为“已注销用户”。
    *   `platform-java`: 删除该用户发布的帖子，或下架相关内容。
    *   各类缓存: 清理该用户的登录态和所有相关缓存。

**JSON Payload 结构**:

```json
{
  "eventId": "evt_9z8y7x6w5v4u",
  "eventType": "user.delete",
  "timestamp": 1713258000,
  "traceId": "xyz987wvu654tsr321",
  "payload": {
    "userId": "10001",
    "deleteType": "soft",               // 删除类型: "soft" (软删除/注销), "hard" (硬删除/物理删除)
    "reason": "用户主动申请注销",          // 删除原因
    "deleteTime": 1713258000            // 执行删除的时间戳
  }
}
```

### 2.3 用户状态变更事件 (`user.status.update`)

当用户的账户状态发生变更时触发（如封禁、解封、冻结、激活等）。

*   **路由键 (Routing Key)**: `user.event.status.update`
*   **触发时机**: 管理后台执行封禁/解封操作，或系统风控自动冻结。
*   **消费方示例**:
    *   `gateway-api`: 强制令该用户的 JWT Token 失效，踢出登录。
    *   `notification-rpc`: 发送封禁/解封系统通知。

**JSON Payload 结构**:

```json
{
  "eventId": "evt_2b3c4d5e6f7g",
  "eventType": "user.status.update",
  "timestamp": 1713261600,
  "traceId": "def456ghi789jkl012",
  "payload": {
    "userId": "10001",
    "oldStatus": 1,                     // 变更前状态 (如: 1 正常)
    "newStatus": 2,                     // 变更后状态 (如: 2 封禁, 3 冻结)
    "reasonCode": "R_SPAM",             // 状态变更的原因编码 (如: R_SPAM 垃圾营销)
    "reasonDesc": "发布大量垃圾广告",     // 状态变更的原因描述
    "operatorId": "admin_88",           // 操作人ID (系统自动为 system)
    "banUntil": 1713866400              // 封禁结束时间戳 (如果为临时封禁，否则为 0 或 null)
  }
}
```

## 3. Go-Zero 消费者接入示例

消费者在 `go-zero` 中通常以基于 RabbitMQ 的 Listener 或使用 go-queue 的 `kq` / custom RabbitMQ 客户端接入。

处理事件时务必：
1.  **幂等性**: 消费者必须保证处理逻辑的幂等性（基于 `eventId` 或重试机制）。
2.  **错误处理**: 失败的事件应当进入死信队列 (DLX, Dead Letter Exchange) 进行人工干预或延时重试。
3.  **链路追踪传递**: 从消息体中提取 `traceId`，并将其注入到消费者处理上下文 (`context.Context`) 中，保证全链路的 Trace 不中断。