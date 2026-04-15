# comment-rpc

`comment-rpc` 是从 `platform-java` 中拆出的评论微服务，当前负责评论核心读写、敏感词校验、删除权限判定和评论事件投递。

## 服务职责

- 维护 MongoDB 中的 `comments` / `comment_likes` 集合
- 提供评论创建、删除、一级评论列表、子评论列表能力
- 对评论内容做敏感词校验
- 发布 `comment.create / comment.delete` 事件

## 接口规范

该服务通过 `gateway-api` 暴露 HTTP。

| 网关路径 | 方法 | 说明 |
| --- | --- | --- |
| `/api/comment/` | `POST` | 创建评论 |
| `/api/comment/:id` | `DELETE` | 删除评论 |
| `/api/comment/list` | `GET` | 一级评论列表 |
| `/api/comment/sub-list` | `GET` | 子评论列表 |

调用示例：

```bash
curl -X POST 'http://localhost:8090/api/comment/' \
  -H 'Authorization: Bearer <access-token>' \
  -H 'Content-Type: application/json' \
  -d '{
    "postId": "693d0f62add946254e35d2b5",
    "content": "这条内容很有帮助"
  }'
```

```bash
curl 'http://localhost:8090/api/comment/list?postId=693d0f62add946254e35d2b5&page=1&size=10'
```

## 技术实现

### 1. 评论模型

- 一级评论：
  - `parentId` 为空
  - 支持软删除
- 二级评论：
  - `parentId` 指向根评论
  - 支持物理删除
- 列表接口返回：
  - 评论作者信息
  - 点赞数 / 是否点赞
  - 根评论 `replyCount`
  - 一级评论下 Top N 子评论预览

### 2. 敏感词校验

- 复用项目原有敏感词库
- 创建评论前先做命中检测
- 避免 Java 和 Go 出现两套不一致规则

### 3. 删除语义

- 一级评论采用软删除
  - 内容改为 `该评论已删除`
  - 占位仍保留在列表中
- 二级评论采用物理删除
  - 真正删除评论记录
  - 同时清理 `comment_likes`

### 4. 评论事件流

- 创建评论后发布 `comment.create`
- 只有二级评论物理删除时发布 `comment.delete`
- 一级评论软删除不再发送 delete 事件
  - 避免下游重复扣减 `post.commentCount`

### 5. MQ 失败回滚

评论创建不是“写库成功就返回”：

- 先插入 Mongo
- 再发 MQ 事件
- 如果 MQ 发布失败：
  - 删除刚插入的评论
  - 如果是二级评论，再把根评论 `replyCount` 回滚
  - 最终向调用方返回失败

这样可以避免评论记录落库成功，但帖子计数、通知、AI 回复等异步副作用永久缺失。

### 6. 启动期鲁棒性

- RabbitMQ 连接增加多次重试
- comment_likes 索引采用兼容创建策略
- 历史环境如果已有不同名字的同结构索引，不会直接把服务启动打崩

## 技术亮点

- 创建评论把“主写成功但 MQ 失败”的不一致问题显式修掉
- 一级评论软删、二级评论硬删，兼顾产品展示和异步计数准确性
- 删除逻辑避免根评论 `replyCount` 被重复扣减
- 启动期对 MQ 和 Mongo 索引做了兼容处理，降低容器抖动导致的 502 风险

## 与其他服务的关系

- `gateway-api`
  - 负责注入当前用户信息
  - 创建评论前通过 `user-rpc` 补齐昵称和头像
- `interaction-rpc`
  - 继续承接评论点赞
- `platform-java`
  - 继续消费评论事件，处理帖子评论数、通知、AI 回复等副作用

## 本地运行

```bash
cd services/comment-rpc
go build ./...
go test ./...
```

通过 Docker 启动：

```bash
docker compose up -d --build comment-rpc
```
