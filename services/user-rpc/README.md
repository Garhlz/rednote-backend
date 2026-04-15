# user-rpc

`user-rpc` 是账号与用户资料中心，负责认证、令牌管理和用户基础资料维护。

## 服务职责

- 邮箱验证码发送与校验
- 注册、登录、刷新、登出
- JWT access / refresh token 签发
- refresh token 白名单和 blocked token 黑名单
- `tokenVersion` 驱动的即时失效
- 个人资料查询与更新
- 用户昵称/头像更新事件发布

## 接口规范

该服务主要暴露 gRPC，由 `gateway-api` 转成 HTTP。对应的核心 HTTP 接口如下。

| 网关路径 | 方法 | 说明 |
| --- | --- | --- |
| `/api/auth/send-code` | `POST` | 发送验证码 |
| `/api/auth/register` | `POST` | 注册 |
| `/api/auth/login` | `POST` | 登录 |
| `/api/auth/refresh` | `POST` | 刷新令牌 |
| `/api/auth/logout` | `POST` | 登出 |
| `/api/auth/password/reset` | `POST` | 重置密码 |
| `/api/user/profile` | `GET` | 获取当前用户资料 |
| `/api/user/profile` | `PUT` | 更新当前用户资料 |
| `/api/user/:userId/profile` | `GET` | 获取公开资料 |

调用示例：

```bash
curl -X POST 'http://localhost:8090/api/auth/send-code' \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@example.com"}'
```

```bash
curl -X PUT 'http://localhost:8090/api/user/profile' \
  -H 'Authorization: Bearer <access-token>' \
  -H 'Content-Type: application/json' \
  -d '{"nickname":"Elaine","bio":"Go 后端练习中"}'
```

## 技术实现

### 1. MySQL 作为用户主事实源

- 用户邮箱、密码、状态、角色、tokenVersion 存在 MySQL
- 适合承载强一致账号数据
- 使用 model 层封装常见查询和更新逻辑

### 2. Redis 作为会话控制层

- 验证码按场景存储，带 TTL
- 发送验证码前做 60 秒限流
- refresh token 使用 `userId + jti` 做白名单
- access / refresh token 可进入 blocked 黑名单
- 登录、刷新、登出时同步维护 `tokenVersion`

### 3. JWT 会话体系

- 登录成功后签发 access token 和 refresh token
- refresh token 轮换时，旧 jti 失效，新 jti 入白名单
- 网关会校验 access token 中的 `tokenVersion`
- 这样即便 token 还没自然过期，也能通过版本号强制失效

### 4. 资料变更事件

- 昵称或头像更新后，会发布 `user.update` 事件
- 由 sidecar 或其他消费者异步更新 Mongo / ES 中的冗余用户信息

## 技术亮点

- `JWT + refresh token 白名单 + tokenVersion` 组合，兼顾性能和可控失效
- 验证码限流、TTL 和场景隔离较完整，适合真实注册登录场景
- 用户主数据放 MySQL，用户冗余信息通过事件异步同步，边界清晰
- 如果 RabbitMQ 初始化失败，用户核心能力仍可运行，只是降级为不发用户更新事件

## 关键依赖

- `MySQL`
- `Redis`
- `RabbitMQ`
- `gateway-api`

## 本地运行

```bash
cd services/user-rpc
go build ./...
go test ./...
```

通过 Docker 启动：

```bash
docker compose up -d --build user-rpc
```
