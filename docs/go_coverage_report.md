# Go 测试覆盖率报告

最近更新：2026-07-24

## 结论

7 个 Go Module 的完整测试均通过。按 `coverprofile` 中语句数加权计算，仓库原始覆盖率为
**13.5%（881 / 6521 statements）**。

这个总数包含 `main`、goctl handler/server 脚手架、Protobuf 生成代码、配置装配、
Telemetry 初始化等大量通常不直接单测的代码，因此不能单独用来评价业务测试质量。
核心业务包的覆盖率更有参考价值：

| 模块 | 核心包 | 核心包覆盖率 | 原始模块覆盖率 |
|---|---|---:|---:|
| `gateway-api` | proxy / middleware / ctxutil / response | 49.3%～82.1% | 16.5% |
| `user-rpc` | `internal/logic` | 31.9% | 10.1% |
| `interaction-rpc` | `internal/logic` | 12.4% | 6.1% |
| `search-rpc` | `internal/logic` | 41.4% | 12.8% |
| `comment-rpc` | logic / model | 62.3% / 100% | 20.9% |
| `notification-rpc` | logic / model | 61.3% / 96.3% | 14.8% |
| `sync-sidecar` | `internal/handler` | 30.7% | 18.9% |

其中网关关键边界包分别为：

- Java Proxy：77.0%
- 鉴权与请求中间件：49.3%
- Context 工具：82.1%
- 统一响应与错误映射：76.9%
- Auth DTO 映射所在包：18.2%
- Comment DTO 映射所在包：33.3%
- 帖子写代理所在包：22.9%

## 执行方式

每个服务是独立 Go Module，因此分别生成 profile：

```bash
cd services/<module>
go test ./... \
  -covermode=atomic \
  -coverprofile=../../build/coverage/<module>.out
```

本次执行模块：

```text
gateway-api
user-rpc
interaction-rpc
search-rpc
comment-rpc
notification-rpc
sync-sidecar
```

覆盖率产物位于 `build/coverage/`：

- `<module>.out`：Go coverage profile
- `<module>.txt`：函数级覆盖率
- `<module>.html`：可交互源码着色报告

## 已覆盖的高价值场景

- Gateway JWT/Cookie 鉴权、用户上下文、Java Proxy、请求头透传和错误映射。
- 用户 Token 类型、JTI、密码哈希、输入校验和存储 Key 契约。
- 互动 MQ JSON、Routing Key、缓存前缀、Bloom Key 和缓存分类契约。
- 搜索建议词、去重、摘要、高亮优先与 ES 异常响应。
- 评论创建失败回滚、权限判断、根评论软删、子评论硬删和分页映射。
- 通知创建/Upsert、未读数、批量已读、分页和类型转换。
- Sidecar 事件路由、ES 更新/删除、重建互斥与管理接口鉴权。
- 另有完整集群 Smoke 覆盖发帖到 ES、评论删除、通知和双鉴权；L3 不计入 Go 单测覆盖率。

## 覆盖缺口与 ROI 排序

### P0：`interaction-rpc` 业务操作

当前逻辑包已由 5.0% 提升到 12.4%，并使用 miniredis 真实验证点赞写入。已锁定：

- 重复点赞不重复发布事件；
- 新点赞移除 Dummy 节点并只发布一次 ADD 事件；
- MQ 失败时 Redis 状态保留、接口仍按弱一致性返回成功；
- LIKE/COLLECT/COMMENT_LIKE/RATE 的跨语言事件字段。

下一步仍需覆盖缓存真正缺失时从 Mongo 预热、取消操作、评分边界和 Bloom 更新。

### P0：`user-rpc` 认证状态机

当前逻辑包已由 17.6% 提升到 31.9%。已覆盖：

- 注册邮箱冲突、验证码错误、默认资料和验证码消费；
- 登录用户不存在、查询失败、密码错误、禁用账号和成功会话写入；
- Refresh Token 类型、JTI、版本、黑名单与轮换。

用户仓储已收敛为业务所需的小接口，测试使用 fake store + miniredis，不连接真实中间件。
下一步补 Redis 写入异常和修改密码后旧 Token 立即失效。

### P1：Gateway 聚合逻辑

大量生成 Handler 为 0%，但逐个测试价值有限。应优先覆盖真正的 BFF 聚合：

- 搜索结果与作者、互动统计合并；
- 下游部分失败时的降级或错误返回；
- 消息、个人主页和互动历史映射；
- Java 代理与 Go RPC 路由不会重复注册。

### P1：Sidecar 用户同步和全量重建

`internal/handler` 为 30.7%，但用户冗余字段同步、批量更新、清空索引及 Mongo 全量重建仍未覆盖。
建议通过 fake Mongo/ES 接口测试事件路由与幂等，不直接依赖真实数据。

### P2：搜索历史与通知清洗

- `search-rpc` 的 Search 主流程、搜索历史读取/删除仍缺逻辑级测试；
- `notification-rpc` 的重复通知清洗尚未覆盖；
- 这些功能重要性低于认证和互动一致性，可放在下一轮。

## 覆盖率目标

不建议立即设置全仓库 80% 门槛。更合理的阶段目标：

1. 将 `interaction-rpc/internal/logic` 从当前 12.4% 提升到 30%；
2. 将 `user-rpc/internal/logic` 从当前 31.9% 提升到 35%；
3. comment、notification 核心 logic 保持 60% 以上；
4. 新修复的真实缺陷必须附带回归测试；
5. CI 暂时记录覆盖率趋势，不因生成代码拉低总数而阻断构建。

后续可以按包设置差异化门槛，或在统计时排除 `*.pb.go`、生成 Handler 和启动装配代码。
