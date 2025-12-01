# 📱 社交平台小程序"映记" - 后端服务 (Afternoon_3 Group)

软件工程课程大作业 - 下午第三组

这是一个基于 Spring Boot 和微服务架构思想构建的后端系统，为我们的“类小红书”微信小程序及 Web 管理后台提供 API 支持。

- [业务代码规范](./business_codes.md)
- [apifox接口(openapi)](./openapi.json)

## 🛠 技术栈 (Tech Stack)

本项目采用现代化的 Java 后端技术栈，容器化部署。

    开发语言: Java 17 (LTS)

    核心框架: Spring Boot 3.x

    数据库: PostgreSQL 15 (支持 JSONB 与向量扩展)
    
    ORM 框架: MyBatis-Plus (高效的数据库操作与动态 SQL)

    缓存: Redis 7 (用于 Session 管理、验证码、高频计数)

    服务器：Aliyun

    对象存储: Aliyun OSS (存储图片、视频资源)

    反向代理: Nginx (端口转发、静态资源托管)

    容器化: Docker & Docker Compose

    API 文档: Apifox / Swagger / OpenAPI 3

## 🏗 系统架构 (Architecture)

系统采用典型的分层架构，并预留了 AI 模块的扩展接口。
```
graph TD
    User[小程序/Web端] --> Nginx[Nginx 网关 (80/443)]
    Nginx -->|/api/auth & /api/user| Boot[Spring Boot 后端 (8080)]
    Nginx -->|/| Static[Web 管理后台静态资源]
    
    Boot --> PG[(PostgreSQL)]
    Boot --> Redis[(Redis 缓存)]
    Boot --> OSS[阿里云 OSS]
    Boot -->|异步调用| AI[AI 服务 (Python/预留)]
```
## 核心模块划分

    Auth 模块: 处理微信一键登录、账号注册、邮件验证码、JWT 签发。

    User 模块: 用户信息管理、邮箱绑定、个人资料修改。

    Post 模块: 笔记发布、流媒体处理。

    Interaction 模块: 点赞、收藏、评论。

## 项目结构
```
graph TD
    Request[前端请求 JSON] --> DTO
    DTO --> Controller
    Controller --> Service
    
    subgraph 业务逻辑
    Service --> Utils
    Service --> Mapper
    end
    
    Mapper --> Entity
    Entity <--> DB[(PostgreSQL)]
    
    Service --> VO
    VO --> Result[统一响应: Result]
    Result --> Response[前端响应 JSON]
```

---

## 🚀 快速开始 (开发指南)

**写给组员的话：** 本项目是一个基于 **Spring Boot + PostgreSQL + MongoDB** 的混合架构后端。在运行代码前，请务必按照以下步骤配置你的本地环境。

### 1. 环境准备 (Prerequisites)

请确保你的电脑上安装了以下软件：

1.  **Java JDK 17** (必须是 17 版本)
    * 验证方式：终端输入 `java --version`，应显示 `version "17.0.x"`.
2.  **PostgreSQL** (关系型数据库，存用户数据)
3.  **MongoDB** (文档型数据库，存帖子/评论数据)
4.  **Maven** (项目构建工具)
    * *不用单独安装*，项目里自带了 `mvnw` (Maven Wrapper)，下面的命令会直接用到它。


### 2. 数据库配置 (Database Setup)

本项目使用了两个数据库，需要分别配置。

#### 2.1 PostgreSQL 配置 (用户表)
1.  打开你的数据库管理工具 (DBeaver / Navicat / pgAdmin)。
2.  创建一个新的数据库（Database），命名为 `platform_db` (与配置文件保持一致)。
3.  **导入建表语句**：
    * 找到项目根目录下的 `init-mongdb.sql` 文件 (注意：虽然文件名带 mongo，但这是给 SQL 用的)。
    * 在 `platform_db` 中运行该 SQL 文件脚本，创建 `users` 表。

#### 2.2 MongoDB 配置 (帖子内容)
MongoDB 不需要提前建表（它会自动创建），但为了方便可视化，建议手动初始化集合。
1.  打开终端或 MongoDB 客户端工具。
2.  创建一个数据库，命名为 `rednote`。
3.  运行以下脚本创建集合：
    ```javascript
    db.createCollection("posts")
    db.createCollection("comments")
    db.createCollection("post_likes")
    db.createCollection("post_collects")
    db.createCollection("user_follows")
    ```
#### 2.3 Redis 配置
本地启动一个 Redis 服务，默认端口 6379 即可。

Docker 快速启动: `docker run -d -p 6379:6379 --name rednote-redis redis`

#### 2.4 环境变量配置 (.env) [重要]
为了安全起见，我们不再直接修改 application-dev.yml 中的密码，而是通过根目录下的 .env 文件注入环境变量。

- 在项目根目录下创建一个名为 .env 的文件。

- 复制以下内容并修改为你本地的真实配置：

```Properties
# --- 数据库配置 ---
DB_URL=jdbc:postgresql://localhost:5432/platform_db?currentSchema=public
DB_USERNAME=postgres
DB_PASSWORD=你的数据库密码

# --- Redis 配置 ---
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=  # 如果本地没密码就留空

# --- 阿里云 OSS (开发环境可以使用 Mock 模式) ---
# 设置为 false 则开启本地模拟上传，文件存放在项目根目录/uploads下
ALIYUN_OSS_ENABLE=false
# 如果设置为 true，则必须填写下面的真实的 Key
ALIYUN_ACCESS_KEY_ID=
ALIYUN_ACCESS_KEY_SECRET=
ALIYUN_BUCKET_NAME=

# --- 微信小程序 ---
WECHAT_APPID=你的APPID
WECHAT_SECRET=你的SECRET

# --- 邮件服务 ---
MAIL_HOST=smtp.163.com
MAIL_USERNAME=你的邮箱@163.com
MAIL_PASSWORD=你的授权码
```
### 3. 如何运行项目 (Run)

本项目集成了 Maven Wrapper，不需要你手动配置 Maven 环境变量。

#### 在 Windows 上:
1. 打开 CMD 或 PowerShell，进入项目根目录。
2. 执行命令：
```Shell
.\mvnw.cmd spring-boot:run
```
#### 在 macOS / Linux 上:
1. 打开终端，进入项目根目录。
2. 赋予脚本执行权限 (仅第一次需要)：
```Bash
chmod +x mvnw
```
3. 执行命令：
```Bash
./mvnw spring-boot:run
```
**看到以下日志即表示启动成功：**
`Started PlatformApplication in x.xxx seconds`
### 4. 验证测试 (Testing)

我们在 src/test/java 下编写了多维度的测试用例，帮助你验证环境和业务逻辑。

在 IDEA 中找到对应的测试文件，点击类名旁边的绿色 ▶️ 按钮即可运行。

#### 4.1 基础环境连通性测试

- PostgreSQL 测试: UserTest.java

    - 作用: 测试能否向 users 表插入数据。
    
    - 成功标志: 输出 写入成功！User ID: xxxxx。

- MongoDB 测试: MongoTest.java

    - 作用: 测试能否向 posts 集合写入文档。

    - 成功标志: 输出 写入成功！生成的 ID 为: xxxxx。

- Redis 测试: RedisTest.java

    - 作用: 测试 Redis 的读写连通性。

#### 4.2 业务逻辑集成测试 (推荐)

这些测试模拟了真实的用户请求流程，且使用了 Mock 技术，不依赖外部第三方服务（如微信、OSS），非常适合开发时自测。

- API 综合测试: controller/ApiIntegrationTest.java

    - 内容: 模拟了“微信登录(Mock) -> 获取Token -> 修改个人资料”的全流程。

    - 特点: 会自动回滚数据库，不会产生脏数据。

- 文件上传测试: controller/CommonControllerTest.java

    - 内容: 测试文件上传接口。

    - 特点: 自动使用本地 Mock 模式，文件会生成在 target/test-uploads 目录下，无需配置阿里云账号。

如果上述测试全部通过，说明你的后端环境及核心业务逻辑已经完美就绪！🎉