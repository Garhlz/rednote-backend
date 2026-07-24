# Sharely：简历与面试速查

## 1. 项目定位

Sharely 是一个内容社区平台，支持注册登录、发帖、搜索、点赞收藏、评分、评论、关注、通知和后台审核。

项目最初是 Spring Boot 课程小组作业。课程结束后，我独立持续迭代，将用户、互动、搜索、评论和通知逐步拆为 Go RPC 服务，并补充统一网关、消息驱动同步、可观测性、测试和部署体系。配套前端只访问网关，不感知 Go 与 Java 并存。

项目重点：**在已有系统上进行渐进式架构演进，而不是为了微服务而重写。**

## 2. 简历写法

### 项目标题

**Sharely 内容社区｜Go、Java、go-zero、Spring Boot、Redis、MongoDB、Elasticsearch、RabbitMQ**

### 项目描述

- 在原 Spring Boot 项目上设计 Go BFF 网关与 Java 业务核心并存的渐进式微服务架构，将用户、互动、搜索、评论和通知拆分为独立 Go RPC 服务。
- 使用 Redis 构建点赞、收藏、评分等高频互动读模型，通过缓存预热、短锁、空值占位和 Bloom Filter 降低缓存击穿与穿透风险，并通过 RabbitMQ 异步维护 MongoDB、ES 派生数据。
- 设计评论发布失败回滚、根评论软删除、子评论硬删除和状态型通知 Upsert 去重规则，处理跨服务写入、重复事件和计数异常问题。
- 使用 OpenTelemetry 串联 HTTP、gRPC、MQ 链路，接入 Jaeger、Prometheus、Loki、Grafana，并建立 Go/Java 分层测试和 Docker Compose 集成环境。

不要写没有数据支撑的“性能提升百分比”“生产级高并发”或“完全保证消息不丢失”。

## 3. 面试介绍

### 30 秒版本

> Sharely 最初是一个 Spring Boot 课程项目。课程结束后，我继续独立维护，把它渐进式改造成 Go/Java 混合微服务：Java 保留帖子、关注和后台等成熟业务，Go 承担用户、互动、搜索、评论、通知和统一网关。项目主要实践了 Redis 高频读模型、RabbitMQ 最终一致性、MongoDB 到 Elasticsearch 的索引同步，以及跨 HTTP、gRPC、MQ 的可观测性。

### 2 分钟版本

> 我没有一次性重写原 Java 系统，而是先根据业务边界和访问特征拆分。帖子、关注和后台逻辑耦合较多，继续留在 Spring Boot；用户、互动、搜索、评论和通知边界清晰，更适合独立成 Go RPC 服务。网关负责 JWT 鉴权、用户上下文传递、RPC 调用、Java 反向代理和页面数据聚合，因此更接近 BFF。
>
> 数据层按职责划分：MySQL 保存账号和认证数据，MongoDB 保存内容文档，Redis 保存 Token 状态和高频互动读模型，Elasticsearch 负责搜索，RabbitMQ 解耦通知、计数和索引同步。MongoDB 是内容事实源，ES 和 Redis 是可重建的读模型。
>
> 重构中重点解决了 Redis 冷启动丢失历史互动、评论写入成功但 MQ 发布失败、评论树删除语义、重复通知，以及跨 HTTP、gRPC、MQ 难排障等问题。最后补充了 OpenTelemetry、分层测试、Docker Compose 和 CI，使项目形成可运行、可验证、可解释的闭环。

## 4. 架构速览

```text
Web 前端
   │ HTTP
   ▼
gateway-api（Go / BFF）
   ├── user-rpc            → MySQL / Redis
   ├── interaction-rpc     → Redis / MongoDB / RabbitMQ
   ├── search-rpc          → Elasticsearch / MongoDB / Redis
   ├── comment-rpc         → MongoDB / RabbitMQ
   ├── notification-rpc    → MongoDB
   └── platform-java       → 帖子、关注、后台、历史复杂业务

RabbitMQ
   ├── platform-java listeners
   └── sync-sidecar        → MongoDB → Elasticsearch
```

核心原则：

- 网关统一外部契约，屏蔽 Go/Java 分工。
- MySQL/MongoDB 保存事实数据，Redis/ES 承担读模型。
- 非关键副作用通过 MQ 异步执行，接受最终一致性。
- 不继续无目的拆服务，避免分布式复杂度超过收益。

## 5. 技术栈复习

### Go 与 go-zero

**项目用途：** 实现网关和 user、interaction、search、comment、notification RPC 服务。

**为什么选择：** Go 并发模型简单、部署产物轻，适合接口稳定、I/O 密集的服务；go-zero 提供 API/RPC 代码生成、配置、日志、服务发现和中间件基础设施。

**需要掌握：**

- Goroutine、Channel、Context 的取消与超时传播
- 接口、错误处理、依赖注入和包组织
- go-zero `handler → logic → svc` 分层
- RPC 超时、重试和幂等的关系
- 生成代码与手写代码的边界

**项目局限：** 服务数量增加后，配置、代码生成和联调成本也会上升；Go 不会自动解决分布式一致性。

### Java 与 Spring Boot

**项目用途：** 保留帖子主写、关注关系、后台管理和 MQ listener。

**为什么保留：** 原业务已经可用，全部重写风险高；Spring Boot 在复杂业务编排、数据访问、校验和后台接口开发上生态成熟。

**需要掌握：**

- IoC、依赖注入、Bean 生命周期
- `@Transactional` 的传播、回滚和自调用失效
- Spring MVC 请求链路、参数校验和统一异常处理
- MyBatis-Plus 与 Spring Data MongoDB 的职责
- `@SpringBootTest` 与普通单元测试的区别

**项目取舍：** 双技术栈提高了部署和接口维护成本，但比一次性重写更稳妥。

### gRPC 与 Protobuf

**项目用途：** 网关与 Go RPC 服务之间的内部通信，也为 Java 调用部分 Go 能力提供契约。

**特点：** Protobuf 是强类型 IDL，使用二进制编码；gRPC 基于 HTTP/2，支持多路复用和流式通信。

**需要掌握：**

- `.proto` 字段编号一旦发布不能随意复用
- 新增字段通常向后兼容，修改字段类型可能破坏兼容
- Deadline、metadata、状态码和拦截器
- 为什么外部前端仍使用 HTTP/JSON，内部服务使用 gRPC

### MySQL

**项目用途：** 用户账号、密码、角色、状态和 Token 版本。

**为什么使用：** 账号和认证需要唯一约束、事务和清晰的关系模型。

**需要掌握：**

- B+ 树索引、联合索引最左匹配、覆盖索引
- ACID、隔离级别、MVCC、间隙锁
- 唯一索引如何防止并发重复注册
- 慢查询分析和 `EXPLAIN`
- 事务只能保证单数据库内部一致性

### MongoDB

**项目用途：** 帖子、评论、通知、关注关系和互动明细。

**为什么使用：** 内容数据结构接近文档，字段演进和聚合读取较灵活。

**需要掌握：**

- 单文档更新原子性
- 普通索引、复合索引、唯一索引和部分索引
- `UpdateOne + upsert` 的语义
- 文档嵌入与引用的选择
- MongoDB 不是“无需设计 Schema”，索引和字段约定仍需治理

### Redis

**项目用途：** Refresh Token/JTI、Token 版本、验证码，以及点赞、收藏、评分读模型。

**数据结构：**

- Set：点赞、收藏用户集合
- Hash：用户评分
- String：验证码、Token 状态和锁
- Bloom Filter：快速判断目标是否可能存在互动数据

**需要掌握：**

- 缓存穿透、击穿、雪崩的区别
- TTL、空值缓存、互斥锁和 Bloom Filter 的适用场景
- Redis 与数据库双写为何会不一致
- Pipeline 减少网络往返，但不等于事务
- RDB、AOF 和 Redis 数据丢失后的恢复边界

### RabbitMQ

**项目用途：** 异步处理互动、评论、通知、用户更新和帖子索引同步。

**为什么选择：** 项目吞吐规模不需要 Kafka；RabbitMQ 路由灵活，适合业务事件和任务分发。

**需要掌握：**

- Exchange、Queue、Binding、Routing Key
- Publisher Confirm、持久化、手动 ACK
- 重试队列、死信队列、消息积压
- At-least-once 语义为什么要求消费者幂等
- 业务唯一键、条件更新和事件 ID 的幂等方案
- MQ 只能解耦流程，不能自动保证跨库事务

### Elasticsearch

**项目用途：** 帖子全文搜索、建议词、热度排序。

**为什么使用：** 倒排索引适合文本检索，相关性排序和高亮能力强于普通数据库模糊查询。

**需要掌握：**

- 倒排索引、分词器、Mapping
- `text` 与 `keyword` 的区别
- 查询与过滤、相关性评分、分页
- Refresh 带来的近实时特性
- 深分页问题及 `search_after`
- ES 是派生读模型，异常时可由 MongoDB 重建

### etcd

**项目用途：** Go RPC 服务注册与发现。

**需要掌握：**

- 服务注册、健康检查、租约和 Watch
- 强一致 KV 的基本定位
- 服务发现解决地址变化，不解决超时、熔断和业务一致性

### OpenTelemetry 与观测栈

**项目用途：** 通过 traceId/requestId 串联 HTTP、gRPC 和 MQ。

| 工具 | 职责 |
|---|---|
| OpenTelemetry | 统一埋点、上下文传播和数据导出 |
| Jaeger | Trace 和 Span 查询 |
| Prometheus | 指标采集与时间序列存储 |
| Loki | 日志聚合与查询 |
| Grafana | 指标和日志可视化 |

**需要掌握：** Trace、Span、指标、日志的区别；P50/P95/P99；RED 指标（请求率、错误率、耗时）；MQ header 和 gRPC metadata 中的上下文传播。

### Docker Compose

**项目用途：** 编排数据库、中间件、业务服务和观测组件；完整环境主要运行在 Linux x86 集成机。

**需要掌握：** 镜像与容器、网络、Volume、健康检查、环境变量、依赖顺序；`depends_on` 只表示启动依赖，业务是否真正可用仍需要 healthcheck 和重试。

## 6. 关键问题与解决方案

### Redis 冷启动丢失历史互动

- **问题：** Key 丢失后直接 `SADD`，Redis 中只剩当前用户，历史互动在读链路上消失。
- **处理：** 写前检查缓存；缺失时先从 MongoDB 完整预热，再执行修改；使用短锁、空值占位和 TTL。
- **可追问：** 锁失效或预热失败怎么办？MongoDB 与 Redis 谁是事实源？

### 评论成功但 MQ 发布失败

- **问题：** 评论已写入，但计数和通知没有触发。
- **处理：** 关键事件发布失败时删除刚写入的评论并返回错误。
- **取舍：** 评论结构与计数关联较强；点赞允许短暂不一致，因此不采用同样回滚策略。

### 评论树删除和计数异常

- **问题：** 根评论硬删导致子评论失去结构；重复删除可能把 `replyCount` 扣成负数。
- **处理：** 根评论软删并保留占位，子评论硬删；计数使用带条件的原子更新。

### Go 与 Java 的 MQ 反序列化不兼容

- **问题：** Go 发布标准 JSON 时没有 Java 类型头；类级 `@RabbitListener` + `@RabbitHandler` 将消息转换为 `LinkedHashMap`，找不到 `CommentEvent` 处理方法，删除事件被拒绝。
- **处理：** 改为方法级 `@RabbitListener`，利用明确的参数类型直接转换 JSON；使用 smoke 锁定“子评论删除后 replyCount 扣减”链路。
- **复盘：** 跨语言消息契约不能依赖框架私有类型元数据，应以 JSON Schema/字段约定、版本和消费者契约测试为准。

### 重复状态通知

- **问题：** 同一用户重复点赞同一帖子产生大量相同通知。
- **处理：** 以接收者、发送者、类型、目标组成业务唯一键，使用 MongoDB Upsert 更新时间并重置未读状态；评论通知仍逐条保存。

### MongoDB 索引冲突

- **问题：** Java 和 Go 对同一集合创建名称或选项不同的索引，引发 `IndexOptionsConflict`。
- **处理：** 统一索引字段、顺序和名称，增加索引审计脚本；历史脏数据通过迁移处理。

### 分布式链路难排查

- **问题：** 请求跨网关、RPC、MQ、Java listener，单看一个服务日志无法定位。
- **处理：** 在 HTTP header、gRPC metadata、MQ header 中传播上下文，日志统一记录 service、traceId、requestId、routingKey。

### Java 测试存在真实副作用

- **问题：** `mvn test` 可能发邮件、上传 OSS或要求整套中间件。
- **处理：** 使用 JUnit Tag 和 Maven Profile 分为默认、integration、external；外部测试增加环境变量安全开关。

## 7. 一致性边界

面试中应准确表述：

- MySQL 单库操作使用本地事务和唯一约束。
- MongoDB 单文档更新使用原子操作和条件更新。
- Redis、MongoDB、ES 之间采用最终一致性。
- 评论关键事件发布失败时进行业务回滚。
- 点赞等弱一致操作允许短暂偏差，依靠重试、重放或巡检恢复。
- MongoDB 是内容事实源，Redis 和 ES 可以重建。

不要说“项目完全保证分布式事务”或“消息绝不丢失”。当前没有实现通用 Outbox/Inbox，生产化还需要 Publisher Confirm、重试、死信监控和一致性巡检。

## 8. 高频追问

### 为什么不是全量 Go 重写？

已有 Java 业务可用，全部重写成本高且容易回归。先拆边界清晰、高频、可独立扩展的模块，以网关保持外部契约稳定。

### 为什么不用 Kafka？

项目吞吐量不需要 Kafka 的分区吞吐和长期事件日志。RabbitMQ 部署更轻、路由灵活，符合当前业务规模。

### 为什么不用 Kubernetes？

当前瓶颈是业务正确性、测试和一致性，不是大规模调度。Compose 足以支持单机集成环境，引入 K8s 的收益低于复杂度。

### MQ 宕机会怎样？

评论等强关联操作失败并回滚；点赞可能暂时停留在 Redis，需要在 MQ 恢复后通过重试或巡检收敛。更完整的生产方案应增加 Confirm、持久化重试和死信告警。

### Redis 宕机怎么办？

互动接口会受到影响；恢复后可从 MongoDB 重新预热读模型。生产环境还应使用高可用部署、持久化和降级策略。

### 微服务拆分带来了什么代价？

调用链、部署组件、接口契约和排障成本增加，数据一致性也从本地事务变成跨服务问题。因此项目后期停止继续拆分，转向测试、观测和交付。

## 9. 诚实边界

- 项目最初是小组作业，后续微服务重构和工程化建设由我独立完成。
- 后台 API 的 Go 声明仍有 Java 代理兼容占位。
- MQ 幂等和补偿按业务实现，尚未形成通用框架。
- 完整 smoke 已在 macOS + OrbStack 环境 5/5 通过；普通 CI 不启动全部中间件，Linux x86 仍建议再做一次可迁移性验证。
- 项目没有真实大规模流量，不能声称经过生产级高并发验证。
- 推荐流以热度和关注关系为主，没有复杂画像算法。

能说明限制和下一步方案，比继续堆技术更可信。

## 10. 收工清单

- [ ] 轮换曾进入 Git 历史的真实凭证
- [ ] GitHub Actions 全部通过
- [x] 在 macOS + OrbStack 环境执行 `scripts/smoke/run_all.sh`（2026-07-23，5/5）
- [ ] 在 Linux x86 环境再执行一次 smoke，验证跨平台部署
- [ ] 保存 smoke、Jaeger 和 Grafana 截图
- [ ] 前端走通登录、发帖、搜索、评论、点赞、通知
- [ ] README 补充前端仓库地址
- [ ] 熟练讲述 30 秒和 2 分钟版本
- [ ] 熟练回答第 5～8 节问题

完成以上内容后停止扩建，把时间投入基础知识复习、投递和新的项目。
