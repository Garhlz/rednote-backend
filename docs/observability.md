# 可观测性平台使用指南 (Observability Guide)

本指南介绍如何在 RedNote 的可观测性平台（Jaeger & Grafana/Loki）中查询日志和调用链路。

所有服务（Go RPC 核心服务、网关、Java 单体应用以及 Async/Sync Layer）均已统一接入 OpenTelemetry 和 Loki 收集栈。

## 统一日志与链路字段

在排查问题时，请优先使用以下统一字段（Uniform Fields）进行跨服务过滤：

- `service`: 服务名称（例如 `gateway-api`, `user-rpc`, `comment-rpc`, `platform-java`）
- `traceId`: 全局唯一的分布式追踪 ID，由网关生成并向后透传（HTTP Header, gRPC metadata, MQ properties）
- `requestId`: HTTP 请求级别的唯一标识（通常与 traceId 相同或为子集）
- `routingKey`: 针对 MQ 异步事件，使用的 RabbitMQ 路由键（例如 `post.create`, `comment.delete`）

## 1. 链路追踪 (Jaeger)

Jaeger 用于查看跨服务的请求调用链，分析延迟瓶颈和错误来源。
本地访问地址：[http://localhost:16686](http://localhost:16686)

### 常见查询场景

- **HTTP 接口性能分析**
  - Service: `gateway-api`
  - Operation: `HTTP GET /api/post/list` 或 `HTTP POST /api/comment/create`
  - Min Duration: 设置例如 `500ms`，查找慢请求。

- **gRPC 调用排查**
  - Service: 选择对应的下游服务（如 `comment-rpc`）
  - Operation: 查看具体的 gRPC 方法（如 `comment.CommentSvc/CreateComment`）。
  - Tags: 可以输入 `error=true` 过滤出发生异常的调用。

- **MQ 异步事件追踪**
  - 我们的系统通过 `sync-sidecar` 处理 RabbitMQ 消息。
  - 搜索 Tags: `routingKey=post.create` 可以查看从发帖到 ES 索引更新的完整异步链路。

## 2. 日志查询 (Grafana / Loki)

Loki 用于统一收集和查询结构化日志。
本地访问地址：[http://localhost:3000](http://localhost:3000)

在 Grafana 中，进入 **Explore** 页面，选择 `Loki` 作为数据源。使用 LogQL (Log Query Language) 进行检索。

### 基础过滤 (Log Stream Selector)

查找特定服务的全部日志：
```logql
{service="comment-rpc"}
```

查找 Java 服务的日志：
```logql
{service="platform-java"}
```

### 结合关键词检索 (Line Filter)

通过 `|=` (包含) 进行全文搜索。

按 `traceId` 查询单个请求的贯穿日志（例如 HTTP 入口 -> gRPC 调用 -> 数据库操作）：
```logql
{service=~".*"} |= "abc123traceidxyz"
```
*(注意：使用 `~".*"` 可跨所有服务查询该 traceId)*

查询某个特定的错误：
```logql
{service="gateway-api"} |= "level=error"
```

查询 MQ 消费日志：
```logql
{service="sync-sidecar"} |= "routingKey=comment.create"
```

### 高级提取与统计 (Metric Queries)

Loki 允许你从日志中提取字段并进行统计聚合，我们推荐使用 JSON 解析器提取字段。

计算过去 5 分钟内特定服务的错误率：
```logql
sum(rate({service="user-rpc"} |= "level=error" [5m]))
```

## 3. 面板配置 (Dashboards)

建议手动在 Grafana 中创建或导入一套标准的 Dashboard：

### 预置面板建议

1.  **全局错误总览 (Global Error Overview)**:
    - 查询: `sum(rate({service=~".*"} |= "level=error" [5m])) by (service)`
    - 类型: Time series

2.  **服务级别日志流 (Service Log Stream)**:
    - 查询: `{service="$service"}` (使用 Dashboard 变量动态切换)
    - 类型: Logs

3.  **Trace 瀑布流探查面板 (Trace Inspector)**:
    - 允许输入 `$traceId` 变量，结合 Loki 查出该链路的所有日志。
    - 查询: `{service=~".*"} |= "$traceId"`

### 设置方法

在 Grafana 首页，点击左侧菜单的 **Dashboards** -> **New Dashboard** -> **Add visualization**，选择 Loki 数据源，粘贴上述查询并保存即可。团队可将其导出为 JSON 文件固化到代码仓库中（如放入 `deploy/grafana/dashboards` 目录）。