# user-rpc model 目录说明

这个目录采用“生成代码 + 扩展代码”分层方式，目的是让 `goctl` 重新生成 model 时，不会覆盖手写业务逻辑。

## 文件职责

- `usersmodel_gen.go`
  - 由 `goctl` 生成。
  - 放表结构对应的基础 CRUD。
  - 不要手动修改。

- `usersmodel.go`
  - 由 `goctl` 生成或维护的 scaffold 文件。
  - 主要保留 `UsersModel`、`customUsersModel`、`NewUsersModel` 这类基础结构。
  - 不建议手动加入自定义查询或业务适配逻辑。

- `usersmodel_ext.go`
  - 手写扩展文件。
  - 放不会由 `goctl` 生成的自定义能力。
  - 当前包括：
    - `FindByIds`
    - `InsertAndReturnID`
    - `UsersExtendedModel`
    - `NewUsersExtendedModel`

- `vars.go`
  - 通用错误定义等辅助内容。

## 使用约定

- 业务层如果需要稳定能力，优先依赖扩展接口 `UsersExtendedModel`。
- 不要在 logic 层直接依赖生成代码的返回值细节。
  - 例如，注册逻辑不要直接依赖 `Insert(...)` 的返回类型。
  - 应该调用扩展方法 `InsertAndReturnID(...)`。

## 为什么这样做

`goctl` 重新生成 model 时，可能会覆盖：

- `usersmodel.go`
- `usersmodel_gen.go`

如果把手写逻辑放进这两个文件，重新生成后就容易丢失，或者出现接口签名变化导致编译失败。

把自定义逻辑集中到 `usersmodel_ext.go` 后：

- 生成文件可以反复覆盖
- 手写扩展不会被覆盖
- 业务层依赖的是我们自己定义的稳定接口，而不是生成器的实现细节

## 重新生成 model 的推荐流程

1. 执行 `goctl` 重新生成：

```bash
goctl model mysql datasource \
  --url='root:123456@tcp(127.0.0.1:3306)/user_db' \
  --table='users' \
  --dir='./internal/model'
```

2. 确认以下文件仍然存在且未被覆盖：
   - `usersmodel_ext.go`
   - `vars.go`

3. 编译验证：

```bash
GOCACHE=/tmp/go-build-user-rpc go build ./...
```

4. 如果编译失败，优先检查：
   - `usersmodel_ext.go` 中调用的生成方法签名是否变化
   - `ServiceContext` 是否仍然使用 `UsersExtendedModel`
   - logic 层是否仍然只调用扩展方法

## 一条原则

以后不要再手改 `usersmodel_gen.go`。  
如果需要新增查询、包装插入返回值、做兼容适配，都放到 `usersmodel_ext.go`。
