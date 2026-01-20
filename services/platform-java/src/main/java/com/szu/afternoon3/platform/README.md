## Springboot后端开发流程建议

### 1. 目录结构概览
```
src/main/java/com/szu/afternoon3/platform/
├── common/              # [核心] 公共组件 (全局响应、异常拦截、上下文)
├── config/              # [核心] 全局配置 (WebMVC, MyBatis, CORS, 拦截器注册)
├── controller/          # [入口] 控制层 (仅处理 HTTP 请求/响应，不写业务逻辑)
├── dto/                 # [输入] 数据传输对象 (前端 -> 后端，需配合 @Valid 校验)
├── entity/              # [数据] 数据库实体 (对应 SQL 表或 Mongo 文档)
│   └── mongo/           #        MongoDB 专用实体
├── exception/           # [异常] 自定义异常与错误码枚举
├── mapper/              # [DAO]  PostgreSQL 数据访问层 (MyBatis-Plus)
├── repository/          # [DAO]  MongoDB 数据访问层 (Spring Data Mongo)
├── service/             # [业务] 业务逻辑接口
│   └── impl/            #        业务逻辑实现
├── util/                # [工具] 第三方工具封装 (JWT, 微信API, OSS 等)
└── vo/                  # [输出] 视图对象 (后端 -> 前端，隐藏敏感数据)
```
### 2. 核心全局组件使用指南

为了保持代码整洁，我们在架构层面做了一些“自动化”处理，开发业务时请遵循以下规范。

- 全局异常处理 (Global Exception Handling)

我们不再需要在 Controller 或 Service 中编写大量的 try-catch 块。

位置: common/GlobalExceptionHandler.java

机制: 系统会自动捕获抛出的异常，并将其转换为标准的 JSON 格式返回给前端。

开发规范:

    当遇到业务错误时（如“用户不存在”、“密码错误”），请直接抛出 AppException。

    不要在 Controller 里捕获异常后只打印日志不抛出，否则前端会误以为请求成功。

示例代码:
```Java
// 错误示范 ❌
if (user == null) {
return Result.error(404, "用户找不到"); // 手动构建错误返回，不推荐
}

// 正确示范 ✅
if (user == null) {
// 直接抛出异常，GlobalExceptionHandler 会自动拦截并生成 JSON 响应
throw new AppException(ResultCode.USER_NOT_FOUND);
}
```

- 用户上下文 (UserContext)

我们使用 ThreadLocal 实现了请求隔离的用户信息存储。

位置: common/UserContext.java 和 config/TokenInterceptor.java

机制:

    请求进入后，TokenInterceptor 会自动解析 Header 里的 JWT Token。

    解析出的 userId 会被存入当前线程的 UserContext。

    请求结束时，拦截器会自动清理，防止内存泄漏。

开发规范:

    在 Controller 或 Service 中需要获取当前登录用户 ID 时，不要让前端在参数里传 userId（不安全，容易被篡改）。

    直接调用静态方法获取。

示例代码:
```Java

public void updateProfile(UserUpdateDTO dto) {
// ✅ 安全：从 Token 解析出来的 ID，绝对可信
Long currentUserId = UserContext.getUserId();

    // 业务逻辑...
}
```
- 统一响应体 (Result)

所有 API 接口的返回类型必须统一封装为 Result<T>。

位置: common/Result.java

结构: { code: 20000, message: "OK", data: { ... } }

示例代码:
```Java

// Controller 写法
@GetMapping("/detail")
public Result<UserVO> getUserDetail() {
UserVO vo = userService.getUser();
return Result.success(vo); // 自动封装为标准 JSON
}
```

- 参数校验 (Validation)

利用 Spring Validation 自动拦截非法参数。

位置: dto/*.java

使用方法:

    在 DTO 字段上加注解（@NotBlank, @Email, @Size 等）。

    在 Controller 参数前加 @Valid。

示例代码:
```Java

// 1. DTO 定义
public class LoginDTO {
@NotBlank(message = "邮箱不能为空")
@Email(message = "邮箱格式错误")
private String email;
}

// 2. Controller 使用
public Result<?> login(@RequestBody @Valid LoginDTO dto) { ... }
```
### 3. 开发流程建议

当你需要开发一个新的功能模块（例如：发布帖子）时，建议遵循以下顺序：

定义实体 (Entity): 确认数据库表结构或 Mongo 文档结构。

定义接口 (DTO/VO): 根据 openapi.json 确定前端传什么参数 (DTO)，后端回显什么数据 (VO)。

编写 DAO (Mapper/Repository): 确保持久层能查到数据。

编写 Service:

    处理业务逻辑。

    利用 UserContext 获取当前用户。

    遇到错误抛出 AppException。

编写 Controller:

    标记 @RestController。

    使用 @Valid 校验参数。

    调用 Service 并返回 Result.success()。

配置权限: 如果接口需要登录才能访问，确保它在 WebMvcConfig 的拦截路径内（默认 /api/** 都拦截，除非在 excludePathPatterns 中排除）。