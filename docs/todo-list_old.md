# todo-list
1. 修改jwt过期时间
2. 打开审核开关


4. 网关层如何升级为微服务
5. 数据库如何进行分布式改造

搜索完成 -> 网关 -> 前端videcoding -> go IM服务

## 第一顺位：搜索服务 (Search Service) —— 完美契合 CQRS

为什么建议迁移？

    读写分离 (CQRS) 的最佳实践：

        你目前已经有了 sync-sidecar 负责把数据从 MongoDB 同步到 Elasticsearch（这是 写）。

        如果你把“搜索接口”迁移到 Go 微服务（这是 读），你就完成了一个标准的 CQRS（命令查询职责分离） 闭环。

        面试加分项：你可以画一张图，展示数据如何异步流入 ES，Go 服务如何通过 gRPC/HTTP 提供高性能检索，完全不干扰主业务库。

    IO 密集型与协议独立：

        搜索往往不需要复杂的业务逻辑（如事务），主要是组装 ES DSL 语句。Go 处理 JSON 和网络 IO 的性能极高。

        搜索服务通常是独立的，挂了不影响用户发帖和登录，非常适合作为微服务练手。

架构设计图：

    写入路径：Java (发帖) -> MongoDB -> sync-sidecar (Go) -> Elasticsearch

    读取路径：前端 -> Java网关 -> Search-RPC (Go) -> Elasticsearch

## 第二顺位：即时通讯服务 (IM / Chat) —— 简历的“王炸”

如果你的“红书”项目想要在简历上脱颖而出，IM 系统是比搜索更亮眼的存在，尤其是对于字节跳动（飞书/抖音私信）或腾讯（QQ/微信）这样的公司。

为什么建议迁移？

    Go 的统治区：

        Java 做 IM 通常需要 Netty，代码量大且重。

        Go 的 Goroutine 模型天生就是为 高并发长连接 (WebSocket) 设计的。单机轻松抗住几十万连接。

    技术深度极深：

        你可以通过这个模块展示：WebSocket 协议、消息推拉模式（Push/Pull）、消息存储（写扩散 vs 读扩散）、离线消息处理。

        这比单纯的 CRUD 增删改查要高出一个段位。

迁移建议： 不要把 IM 做在现在的 Java 里。直接起一个 im-service (Go)，专门处理 WebSocket 连接和消息路由。

## 第三顺位：网关层 (API Gateway) —— 架构的“总管”

目前你的 Java Controller 其实充当了“网关”的角色（鉴权、路由、聚合）。在成熟的微服务架构中，这层应该被剥离。

为什么建议迁移？

    使用 go-zero 自带的 API Gateway (go-zero API)。

    作用：前端只请求 go-zero 网关，网关负责鉴权（JWT 解析），然后根据路由把请求分发给 Java 服务（业务）或 Go 服务（互动/搜索）。

    好处：你的 Java 后端会变得非常纯粹，只处理业务，不再关心 HTTP 协议细节。

## 不建议迁移的模块 (避坑指南)

    用户/认证服务 (User/Auth)：

        原因：它是所有服务的依赖中心。如果你现在动它，需要修改 Java 代码里所有的 UserContext、JWT 拦截器、Feign/gRPC 调用链。工作量巨大且容易把系统搞崩，投入产出比低。

    内容/帖子服务 (Content/Post)：

        原因：这是业务逻辑最复杂的地方（状态机、审核流程、编辑锁）。Java 的 JPA/MyBatis 和 Spring 的事务管理在这里优势很大，没必要为了用 Go 而用 Go。

# 假设容器名为 mongo-local-dev
docker cp ~/Downloads/mongo_backup mongo-local-dev:/tmp/backup

# 进入容器并执行恢复

docker exec -it mongo-local-dev mongorestore --drop /tmp/backup


## user-rpc 待办

- 事件同步：user-rpc 目前没有 MQ 发布逻辑（代码里找不到 UserUpdateEvent/user.# 相关），所以 用户资料更新不会触发 sync-
sidecar 的 Mongo 冗余刷新。
    - 需要：在 UpdateProfile / BindEmail / ChangePassword / UpdateStatus 后发布 UserUpdateEvent 到
    platform.topic.exchange，routing key 建议 user.update 或 user.profile.update（保持 sidecar 的 QueueBindings）。
- 删除/注销事件：如果 UserDeleteEvent 需要触发 Mongo 匿名化，也要在 user-rpc 发布。
- 验证码/黑名单/refresh 的 Redis key 规范：建议确认是否满足你预期的过期与清理策略（目前没看到 MQ/事件相关逻辑）。

## gateway-api 待办

- Java 代理注入用户信息：目前 javaproxy 只透传原有 header，没有把 userId/role/nickname 注入到 Java。
    - 需要在 services/gateway-api/internal/handler/javaproxy/proxy.go 中从 context 读取 userId/role/nickname 并写入自
    定义 header（如 X-User-Id 等），让 Java 无需解析 JWT。
- 结构化返回：auth 已解决；但 用户信息接口仍为 Empty（如 GetProfile/GetPublicProfile/UpdateProfile），如果你打算前端直
- 从请求 Header 读取用户上下文（例如 X-User-Id/X-User-Role/X-User-Nickname），不再解析 JWT。
- 统一用户资料来源：
    - 若继续使用 Mongo 冗余字段，确保 user-rpc 发布事件 → sync-sidecar 更新；
    - 若改成强实时，Java BFF 可以 RPC 调用 user-rpc 并缓存。

## sync-sidecar 相关

- 你确认的路径：services/sync-sidecar/internal/handler/user_handler.go 已能处理 UserUpdateEvent/UserDeleteEvent 并写入
Mongo。
- 待办就是让 user-rpc 也发这些事件；目前事件发布只在 Java 侧（如果有）生效。

# 当前需要修改的api列表
我对比了旧的 rednote-用户端.openapi.json 和当前网关 services/gateway-api/gateway.api，你在 Apifox 需要做的改
动如下。

需要删除的接口

- /api/auth/login/wechat（微信登录移除）
- /api/auth/login/account（改为 /api/auth/login）
- /api/auth/test/register（仅开发用，已下线）
- /api/user/password/set（当前网关未提供）

需要新增的接口

- /api/auth/login（POST，email+password，返回 AuthResponse）
- /api/auth/register（POST，email+code+password+nickname，返回 AuthResponse）

需要修改的接口（路径不变但请求/响应结构已变）

- /api/auth/refresh
  现在返回 AuthResponse（包含 tokens/user/isNewUser/hasPassword），请求体是 { "refreshToken": "..." }
- /api/auth/send-code
  现在返回 { "nextRetrySeconds": int }
- /api/auth/password/reset
  请求体字段变为 { email, code, newPassword }
- /api/user/profile GET/PUT
  返回结构改为 UserProfile（字段更完整）；PUT 也返回 UserProfile
- /api/user/{userId}/profile
  返回结构改为 PublicUserProfile
- /api/user/password/change
  请求体为 { oldPassword, newPassword }
- /api/post/search
  返回改为 PageResultPost，PostVO 增加 collectCount/commentCount/isCollected/isFollowed 等字段
- /api/post/search/suggest
  返回改为 { suggestions: [] }
- /api/search/history
  返回改为 { keywords: [] }（不再是裸数组）

保持不变（只是后端实现已迁移）

- /api/interaction/* 路径不变（由 interaction-rpc 实现）
- /api/search/* 路径不变（由 search-rpc 实现）

认证头统一

- 所有需要登录的接口都用 Authorization: Bearer <accessToken>
- 不再依赖 Java 解析 JWT（但 Apifox 仍要加这个头）


# 2026-03-18修改
优化elastic search的同步问题，方案如下：
回到你实际的代码重构中，考虑到你不想引入太重的运维负担（比如再去搭一套 Debezium），你有两个非常务实的演进方向：

- 务实派：在现有的 Java 消费者里，加上 Spring Retry 注解和 RabbitMQ 死信队列，低成本兜底。
- 极客派：删掉 Java 里所有同步 ES 的代码，在 Go 或 Java 里写一个纯粹的 MongoDB Change Streams 监听器，感受一下“原生 CDC”的丝滑。
