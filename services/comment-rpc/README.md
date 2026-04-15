# comment-rpc

`comment-rpc` 是从 `platform-java` 中拆出来的评论微服务，当前负责评论核心读写、敏感词校验、删除权限校验和评论事件发布。

## 服务职责

- 维护 MongoDB 中的 `comments` 集合
- 提供评论读接口
  - 一级评论列表
  - 子评论列表
- 提供评论写接口
  - 创建评论
  - 删除评论
- 发布评论事件
  - `comment.create`
  - `comment.delete`

## 当前拆分边界

当前评论链路已经切为：

```text
客户端
  -> gateway-api
  -> comment-rpc
  -> Mongo comments
```

写侧保持原有 MQ 语义：

```text
comment-rpc
  -> RabbitMQ
  -> platform-java listeners / sidecar
```

因此不会切断：

- 评论计数同步
- 评论通知
- AI 回复等依赖评论事件的链路

## 读写模型

### 读

- `ListRootComments`
  - 按帖子查询一级评论
  - 返回 Top 3 子评论预览
  - 返回当前用户是否点赞
- `ListSubComments`
  - 查询某条一级评论下的全部子评论
  - 返回当前用户是否点赞

### 写

- `CreateComment`
  - 校验敏感词
  - 建立一级/二级评论关系
  - 更新一级评论 `replyCount`
  - 发布 `comment.create`
- `DeleteComment`
  - 校验管理员 / 评论作者 / 帖子作者权限
  - 一级评论软删除
  - 二级评论物理删除
  - 更新一级评论 `replyCount`
  - 发布 `comment.delete`

## 敏感词过滤

当前服务复用了项目原有的加密词库：

- 词库文件位于 [internal/sensitive](/home/elaine/work/projects/rednote-backend/services/comment-rpc/internal/sensitive)
- 使用与 Java 相同的 XOR 密钥解密

这样可以保证 Java 和 Go 在评论敏感词规则上保持一致。

## 与其他服务的关系

- `gateway-api`
  - 负责 HTTP 参数解析
  - 负责把当前用户信息注入 RPC 请求
  - 创建评论时通过 `user-rpc` 补齐当前用户头像/昵称
- `interaction-rpc`
  - 继续负责评论点赞
- `platform-java`
  - 继续消费评论事件，处理评论计数、通知、AI 回复等异步逻辑

## Prometheus 指标

默认 metrics 端口：

- `40096`

抓取路径：

- `/metrics`

主要指标：

- `comment_rpc_requests_total`
- `comment_rpc_request_duration_seconds`

标签：

- `action`
  - `create_comment`
  - `delete_comment`
  - `list_root_comments`
  - `list_sub_comments`
- `result`
  - `success`
  - `mongo_unavailable`
  - `sensitive_word`
  - `invalid_post_id`
  - `post_not_found`
  - `invalid_parent_id`
  - `parent_not_found`
  - `invalid_root_id`
  - `reply_count_update_error`
  - `insert_error`
  - `invalid_comment_id`
  - `comment_not_found`
  - `permission_denied`
  - `mongo_error`
  - `count_error`
  - `find_error`
  - `decode_error`
  - `like_lookup_error`
  - `preview_load_error`

## 本地运行

### 编译

```bash
cd services/comment-rpc
go build ./...
```

### 运行

```bash
cd services/comment-rpc
go run comment.go -f etc/comment.yaml
```

### Docker Compose

```bash
docker compose up -d --build comment-rpc
```
