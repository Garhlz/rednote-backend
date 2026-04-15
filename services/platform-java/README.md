# platform-java

`platform-java` 是当前系统的主业务服务，负责帖子主写、关注关系、后台管理和多个异步 listener 的业务副作用处理。

虽然项目已经把评论、通知等能力逐步迁到了 Go 服务，但 Java 仍然是业务主干。

## 服务职责

- 帖子发布、编辑、删除、详情
- 首页推荐流、关注流、用户帖子列表
- 关注 / 取关、粉丝 / 关注列表、好友关系
- 后台审核、日志、统计
- MQ 事件消费
  - 更新帖子计数
  - 生成通知语义
  - 触发 AI 回复

## 接口规范

该服务对外接口通常经由 `gateway-api` 暴露，以下列出当前仍主要由 Java 实现的核心能力。

| 网关路径 | 方法 | 说明 |
| --- | --- | --- |
| `/api/post/` | `POST` | 发布帖子 |
| `/api/post/:postId` | `GET` | 帖子详情 |
| `/api/post/list` | `GET` | 推荐流 / 关注流 |
| `/api/post/user/:userId` | `GET` | 用户帖子列表 |
| `/api/user/follows/:userId` | `GET` | 关注列表 |
| `/api/user/fans/:userId` | `GET` | 粉丝列表 |
| `/api/user/follow` | `POST` | 关注用户 |
| `/api/user/unfollow` | `POST` | 取消关注 |
| `/admin/**` | 多种 | 后台管理、审核、日志导出、统计 |

调用示例：

```bash
curl -X POST 'http://localhost:8090/api/post/' \
  -H 'Authorization: Bearer <access-token>' \
  -H 'Content-Type: application/json' \
  -d '{
    "title": "测试帖子",
    "content": "分享一次完整的后端重构经历",
    "images": ["https://example.com/a.png"],
    "tags": ["Go", "微服务"],
    "coverWidth": 1080,
    "coverHeight": 1350,
    "type": 2
  }'
```

## 技术实现

### 1. 帖子主写

- 帖子、评论、通知等内容型数据主存储在 Mongo
- 发帖成功后发布 `post.create` 或审核相关事件
- 推荐流依赖 Mongo / ES / Redis 的组合查询

### 2. 关注关系

- 关注关系使用 Mongo `UserFollowDoc`
- 关注流不是走 ES 搜索，而是先查关注关系，再查对应作者帖子
- 适合“我关注的人发了什么”的关系型 Feed

### 3. 异步 listener

Java 当前仍保留多个关键 listener：

- `InteractionEventListener`
  - 处理点赞、收藏、关注、评分等副作用
- `CommentEventListener`
  - 处理评论数、回复数、评论通知
- `PostEventListener`
  - 处理帖子事件相关业务
- `PostAuditListener`
  - 处理审核通过/驳回通知

这些 listener 会继续把通知写入委托给 `notification-rpc`，把评论核心能力委托给 `comment-rpc`。

### 4. 后台与运营能力

- 用户管理
- 内容审核
- 操作日志导出
- 访问统计与热门内容统计

这部分暂时仍集中在 Java，更适合 Spring Boot + 后台脚手架式开发。

## 技术亮点

- 采用“保留 Java 主业务 + 渐进式迁出 Go 子域服务”的演进路径，风险可控
- 关注流和推荐流分开建模，分别走关系链查询和搜索/排序逻辑
- 通过 MQ listener 把通知、计数、审核等副作用从主请求链路中剥离
- Java 不再直接承担所有读写接口，而是开始与 Go RPC 形成协同边界

## 本地运行

```bash
cd services/platform-java
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

开启 Jaeger 链路追踪：

```bash
./scripts/run-platform-java-with-otel.sh
```
