# 📱 社交平台小程序"映记" - 后端服务 (Afternoon_3 Group)

软件工程课程大作业 - 下午第三组

这是一个基于 Spring Boot 和微服务架构思想构建的后端系统，为我们的“类小红书”微信小程序及 Web 管理后台提供 API 支持。

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
    ```

#### 2.3 修改项目配置
打开 `src/main/resources/application-dev.yml` (或者 `application.yml`)，找到以下部分并修改为你本地的账号密码：

```yaml
spring:
  datasource:
    # 修改 PostgreSQL 的账号密码
    username: your_postgres_username (默认通常是 postgres)
    password: your_postgres_password
  data:
    mongodb:
      # 确认 MongoDB 地址 (通常不用改，除非你有密码)
      uri: mongodb://localhost:27017/rednote
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

为了确认你的环境（Postgres 和 Mongo）都配置正确了，请运行我们写好的两个测试文件。

在 IDEA 中找到以下文件，点击左侧绿色的 ▶️ 按钮运行：

1. PostgreSQL 测试: src/test/java/.../UserTest.java

- 作用: 测试能否向 Postgres 的 users 表插入数据。

- 成功标志: 控制台输出 写入成功！User ID: xxxxx。

2. MongoDB 测试: src/test/java/.../MongoTest.java

- 作用: 测试能否向 Mongo 的 posts 集合写入文档。

- 成功标志: 控制台输出 写入成功！生成的 ID 为: xxxxx。

如果两个测试都通过，说明你的本地开发环境已经完美就绪！🎉
