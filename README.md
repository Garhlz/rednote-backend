# Sharely / 分享派后端

这是一个面向内容社区场景的混合微服务后端项目。当前架构不是单一技术栈单体，而是：

- `gateway-api` 作为统一入口网关
- `user-rpc`、`interaction-rpc`、`search-rpc` 作为 Go 专职微服务
- `platform-java` 作为帖子、评论、通知、后台等业务主服务
- `sync-sidecar` 作为 MQ 消费与 ES/Mongo 同步 Sidecar

项目目标不是演示简单 CRUD，而是尽量贴近真实工程场景：统一鉴权、跨服务聚合、事件驱动、最终一致性、搜索、互动高频读模型、多存储协作。

- [业务代码规范](./business_codes.md)
- [OpenAPI / Apifox 导出](./openapi.json)

## 当前技术栈

- `Go 1.25`：实现网关、账号、互动、搜索、同步 Sidecar
- `Java 17 + Spring Boot 3`：实现业务主服务 `platform-java`
- `go-zero`：用于 API 网关、zRPC 服务、服务发现与工程脚手架
- `gRPC / zRPC`：用于网关与 Go 微服务之间的内部调用
- `MySQL 8`：承载用户域核心关系数据
- `MongoDB 6`：承载帖子、评论、通知、关注关系等文档数据
- `Redis 7 / Redis Stack`：承载 token 状态、验证码、互动读模型、Bloom Filter
- `RabbitMQ 3.12`：承载业务事件与异步解耦
- `Elasticsearch 8`：承载搜索、建议与排序
- `Docker / Docker Compose`：承载本地基础设施和 Go 服务联调
- `JWT`：承载 access / refresh token 会话体系
- `Micrometer + Prometheus`：Java 服务指标暴露基础
- `MyBatis-Plus`：Java 侧关系型访问
- `Spring Data MongoDB`：Java 侧 MongoDB 访问

## 当前系统架构

```mermaid
graph TD
    Client[Web / App / 管理后台] --> Gateway[gateway-api (Go)]

    Gateway -->|zRPC| UserRPC[user-rpc]
    Gateway -->|zRPC| InteractionRPC[interaction-rpc]
    Gateway -->|zRPC| SearchRPC[search-rpc]
    Gateway -->|HTTP 代理 / 聚合| Java[platform-java]

    UserRPC --> MySQL[(MySQL: users)]
    UserRPC --> Redis[(Redis)]

    InteractionRPC --> Redis
    InteractionRPC --> Mongo[(MongoDB)]
    InteractionRPC --> MQ[(RabbitMQ)]

    SearchRPC --> ES[(Elasticsearch)]
    SearchRPC --> Mongo

    Java --> Mongo
    Java --> Redis
    Java --> MQ
    Java --> ES

    MQ --> Sidecar[sync-sidecar (Go)]
    Sidecar --> Mongo
    Sidecar --> ES
```

## 架构设计亮点

- **混合微服务架构**：采用 `Go 网关 + Go 专职服务 + Java 业务主服务` 的迁移式架构，而不是一次性暴力拆分单体。
- **统一网关入口**：所有请求先进入 `gateway-api`，由网关统一完成鉴权、错误包装、上下文透传、路由和结果聚合。
- **用户域独立**：`user-rpc` 统一负责账号注册登录、JWT 签发、refresh token 轮换、tokenVersion 失效控制。
- **互动域独立**：`interaction-rpc` 统一负责点赞、收藏、评分及互动状态查询，避免互动逻辑散落在 Java 与前端。
- **搜索域独立**：`search-rpc` 专注 ES 搜索、搜索建议、搜索历史与排序能力。
- **事件驱动解耦**：通过 `RabbitMQ` 传递帖子、互动、用户变更事件，把主写链路与索引/冗余同步解耦。
- **最终一致性设计**：Mongo、ES、冗余用户信息更新通过 MQ + Sidecar 异步完成，避免分布式事务。
- **互动读模型设计**：将点赞、收藏、评分等高频展示数据收敛到 Redis 读模型，由 `interaction-rpc` 统一对外提供。
- **缓存冷启动保护**：互动链路实现了懒加载预热、短锁防击穿、Dummy 占位防穿透、Bloom Filter 优化。
- **JWT 即时失效机制**：结合 `refresh token jti`、Redis 白名单、tokenVersion、黑名单机制，实现更可控的会话管理。
- **网关聚合能力**：列表、搜索、详情等接口由网关聚合搜索结果、互动状态、用户上下文，统一输出前端需要的视图模型。
- **可演进的数据边界**：用户主事实源在 MySQL；内容与关系数据在 Mongo；搜索在 ES；短期状态与高频集合在 Redis。

## 亮眼特性

- **帖子列表/搜索聚合链路**
  - `search-rpc` 返回搜索结果
  - `interaction-rpc` 批量补齐 `likeCount / collectCount / isLiked / isCollected / isFollowed / rating`
  - `gateway-api` 输出统一 `PostVO`

- **帖子详情聚合链路**
  - `platform-java` 返回帖子正文、图片、作者基础信息
  - `interaction-rpc` 返回实时互动字段
  - `gateway-api` 做最终拼装

- **Redis 互动高频链路**
  - 点赞、收藏使用 Redis Set
  - 评分使用 Redis Hash
  - 通过写前预热避免冷缓存覆盖历史数据
  - 通过 Dummy 占位避免空值穿透
  - 通过 Bloom 减少无意义预热

- **用户登录态设计**
  - access token / refresh token 分离
  - refresh token 引入 `jti` 做单张白名单管理
  - `tokenVersion` 实现整批 token 即时失效
  - 网关统一校验并向下游透传用户上下文

- **网关弱登录态支持**
  - 公开接口允许匿名访问
  - 若请求携带合法 access token，网关仍会解析登录态
  - 列表/搜索页可在匿名和登录两种状态下复用同一接口

## 核心模块

### gateway-api

- 统一 HTTP 入口
- JWT 校验与 tokenVersion 校验
- 用户上下文注入与 Java 头透传
- Java 反向代理
- 搜索页、列表页、详情页跨服务聚合

### user-rpc

- 邮箱注册、登录、刷新、登出
- 用户资料查询与更新
- 验证码发送与校验
- refresh token 白名单管理
- tokenVersion 维护与即时失效

### interaction-rpc

- 点赞、取消点赞
- 收藏、取消收藏
- 帖子评分
- 评论点赞
- 批量帖子互动状态聚合
- Redis 冷启动预热与缓存保护逻辑

### search-rpc

- 帖子搜索
- 搜索建议
- 搜索历史
- ES 排序与查询

### platform-java

- 帖子、评论、通知、管理后台
- MongoDB 业务主写入
- RabbitMQ 事件发布与消费
- ES 业务冗余更新
- 部分后台能力通过 gRPC 回调 `user-rpc`

### sync-sidecar

- 消费 MQ 事件
- 回查 Mongo 数据
- 将帖子与用户冗余变更同步到 ES / Mongo

## 关键请求流

### 登录

```text
Client
  -> gateway-api /api/auth/login
  -> user-rpc Login
  -> 校验用户与密码
  -> 签发 access/refresh token
  -> Redis 写 refresh 白名单与 tokenVersion
  -> gateway-api 返回统一响应
```

### 帖子列表

```text
Client
  -> gateway-api /api/post/list
  -> search-rpc Search
  -> interaction-rpc BatchPostStats
  -> gateway-api 聚合为统一 PostVO
  -> 返回前端
```

### 帖子详情

```text
Client
  -> gateway-api /api/post/:postId
  -> platform-java 获取帖子主体内容
  -> interaction-rpc 获取实时互动字段
  -> gateway-api 覆盖并聚合结果
  -> 返回前端
```

## 本地开发启动方式

当前推荐的本地联调方式是：

- Java 主服务 `platform-java` 直接在宿主机运行
- MySQL / Mongo / Redis / RabbitMQ / ES 与 Go 服务走 Docker Compose

### 1. 启动基础设施和 Go 服务

在项目根目录执行：

```bash
docker compose up -d --build
```

常用服务端口：

- `gateway-api`: `8090`
- `interaction-rpc`: `8081`
- `search-rpc`: `8082`
- `user-rpc`: `8083`
- `MySQL`: `3306`
- `MongoDB`: `27017`
- `Redis`: `6379`
- `RabbitMQ`: `5672`
- `RabbitMQ Console`: `15672`
- `Elasticsearch`: `9200`

### 2. 在宿主机启动 Java

```bash
cd services/platform-java
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

如果 Java 需要访问本地基础设施，常见环境变量可以显式指定为：

```bash
SPRING_PROFILES_ACTIVE=dev \
DB_URL='jdbc:mysql://127.0.0.1:3306/user_db?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false' \
MONGO_URI='mongodb://127.0.0.1:27017/rednote' \
REDIS_HOST='127.0.0.1' \
RABBITMQ_HOST='127.0.0.1' \
mvn spring-boot:run
```

### 3. 宿主机模式说明

`gateway-api` 默认通过：

```text
http://host.docker.internal:8080
```

访问宿主机上的 Java 服务。Linux 环境下如果遇到容器无法访问宿主机 `8080`，通常需要额外放行防火墙，例如：

```bash
sudo ufw allow in on docker0 to any port 8080 proto tcp
```

## 配置与文档

- 网关 API 定义文件：[gateway.api](./services/gateway-api/gateway.api)
- 统一开放接口导出：[openapi.json](./openapi.json)
- Docker 编排文件：[docker-compose.yml](./docker-compose.yml)

## 后续演进方向

- 补充 Prometheus + Grafana 的混合微服务监控方案
- 引入 OpenTelemetry / Jaeger 做链路追踪
- 完善关键链路的单元测试与集成测试
- 为 MQ 异步链路补充死信、重试和对账修复机制
- 继续收敛互动相关展示字段的事实源
