下面给你一份可以直接照着配的 Grafana 面板配置清单。
你先做一个总览 Dashboard 就够了，名字建议叫：

Rednote Overview

———

先做一次基础设置

1. 打开 http://localhost:3001
2. 登录 admin / admin
3. 添加数据源
    - 类型：Prometheus
    - URL：http://prometheus:9090
4. 点 Save & test

———

# Dashboard 结构

建议分成 4 行：

1. Gateway
2. Interaction
3. User
4. Search

———

# 1. Gateway 行

## 面板 1：Gateway QPS

- 标题：Gateway QPS
- 图表类型：Time series
- PromQL：

sum(rate(gateway_http_requests_total[1m]))

- 含义：网关每秒请求数

## 面板 2：Gateway Status

- 标题：Gateway Requests By Status
- 图表类型：Bar chart
- PromQL：

sum by (status) (rate(gateway_http_requests_total[5m]))

- 含义：按状态码统计请求速率，重点看 200/401/404/500/504

## 面板 3：Gateway P95

- 标题：Gateway P95 Latency
- 图表类型：Time series
- 单位：seconds (s)
- PromQL：

histogram_quantile(0.95, sum by (le) (rate(gateway_http_request_duration_seconds_bucket[5m])))

- 含义：网关整体 95 分位耗时

## 面板 4：Java Proxy Errors

- 标题：Gateway Java Proxy Errors
- 图表类型：Bar chart
- PromQL：

sum by (kind) (rate(gateway_java_proxy_errors_total[5m]))

- 含义：网关代理 Java 出错的类型分布
    
## 面板 5：Java Proxy P95

- 标题：Gateway Java Proxy P95
- 图表类型：Time series
- 单位：seconds (s)
- PromQL：

histogram_quantile(0.95, sum by (le) (rate(gateway_java_proxy_duration_seconds_bucket[5m])))

- 含义：gateway -> platform-java 这条链的 95 分位耗时

———

# 2. Interaction 行

## 面板 6：Cache Warmups

- 标题：Interaction Cache Warmups
- 图表类型：Bar chart
- PromQL：

sum by (kind, result) (rate(interaction_cache_warm_total[5m]))

- 含义：互动缓存预热次数，按类型和结果拆分

## 面板 7：Cache Warmup P95

- 标题：Interaction Cache Warmup P95
- 图表类型：Time series
- 单位：seconds (s)
- PromQL：

histogram_quantile(0.95, sum by (kind, le) (rate(interaction_cache_warm_duration_seconds_bucket[5m])))

- 含义：互动缓存预热耗时

## 面板 8：Bloom Checks

- 标题：Interaction Bloom Checks
- 图表类型：Bar chart
- PromQL：

sum by (kind, result) (rate(interaction_bloom_checks_total[5m]))

- 含义：Bloom 命中、未命中、不可用情况

## 面板 9：Dummy Ops

- 标题：Interaction Dummy Ops
- 图表类型：Bar chart
- PromQL：

sum by (kind, action) (rate(interaction_dummy_ops_total[5m]))

- 含义：Dummy 占位节点填充/移除次数

## 面板 10：MQ Publish

- 标题：Interaction MQ Publish
- 图表类型：Bar chart
- PromQL：

sum by (routing_key, result) (rate(interaction_mq_publish_total[5m]))

- 含义：互动事件发 MQ 的成功/失败情况

———

# 3. User 行

## 面板 11：Auth Requests

- 标题：User Auth Requests
- 图表类型：Bar chart
- PromQL：

sum by (action, result) (rate(user_auth_requests_total[5m]))

- 含义：登录、注册、刷新 token 的请求结果分布

## 面板 12：Auth P95

- 标题：User Auth P95
- 图表类型：Time series
- 单位：seconds (s)
- PromQL：

histogram_quantile(0.95, sum by (action, le) (rate(user_auth_request_duration_seconds_bucket[5m])))

- 含义：登录/注册/刷新耗时

## 面板 13：Email Code Requests

- 标题：Email Code Requests
- 图表类型：Bar chart
- PromQL：

sum by (scene, result) (rate(user_email_code_requests_total[5m]))

- 含义：验证码发送成功、限流、邮件失败等情况

———

# 4. Search 行

## 面板 14：Search Requests

- 标题：Search RPC Requests
- 图表类型：Bar chart
- PromQL：

sum by (action, result) (rate(search_rpc_requests_total[5m]))

- 含义：搜索、建议、历史相关请求量和结果分布

## 面板 15：Search P95

- 标题：Search RPC P95
- 图表类型：Time series
- 单位：seconds (s)
- PromQL：

histogram_quantile(0.95, sum by (action, le) (rate(search_rpc_request_duration_seconds_bucket[5m])))

- 含义：搜索服务整体耗时

## 面板 16：ES Query P95

- 标题：Search ES Query P95
- 图表类型：Time series
- 单位：seconds (s)
- PromQL：

histogram_quantile(0.95, sum by (action, le) (rate(search_es_query_duration_seconds_bucket[5m])))

- 含义：ES 查询耗时，判断慢点是在 ES 还是服务层

———

# 推荐的可视化选择

你可以直接按这个选：

- QPS / 延迟：Time series
- 状态分布 / 错误分布 / 分类统计：Bar chart
- 单个关键数字：Stat

———

# 建议加的几个变量

如果你后面想稍微高级一点，可以加 Dashboard Variables：

## 变量 1：时间范围

这个 Grafana 自带，不用自己配。

## 变量 2：状态码

如果以后想筛选网关状态码，再加变量。
现在先不用，避免复杂化。

———

# 新手最实用的操作方式

每个 panel 都按这个流程：

1. Add visualization
2. 选数据源 Prometheus
3. 粘贴 PromQL
4. 右侧选图表类型
5. 改标题
6. Apply

———

# 最小可用版本

如果你不想一次做 16 个，先做这 6 个最有价值：

1. Gateway QPS
2. Gateway P95
3. Interaction Cache Warmups
4. Interaction MQ Publish
5. User Auth Requests
6. Search ES Query P95

这 6 个已经够你演示项目监控能力了。

———

# 可能遇到的问题

## 图上没数据

先看 Prometheus：

- 打开 http://localhost:9090/targets
- 确认 target 是 UP

## Grafana 里查不到 metric

去 Prometheus 的 Graph 页面直接搜 metric 名，比如：

gateway_http_requests_total

## 某些图是空的

通常只是因为最近没有相关流量。

———

如果你要，我下一步可以继续给你一份：

- Grafana 面板创建步骤截图式说明
  或者
- 一份 dashboard JSON 结构草稿思路
  让你更容易照着搭。
 
