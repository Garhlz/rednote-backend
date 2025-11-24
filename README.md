📱 仿小红书小程序 - 后端服务 (Afternoon_3 Group)

    软件工程课程大作业 - 下午第三组

    这是一个基于 Spring Boot 和微服务架构思想构建的后端系统，为我们的“类小红书”微信小程序及 Web 管理后台提供 API 支持。

🛠 技术栈 (Tech Stack)

本项目采用现代化的 Java 后端技术栈，容器化部署。

    开发语言: Java 17 (LTS)

    核心框架: Spring Boot 3.x

    数据库: PostgreSQL 15 (支持 JSONB 与向量扩展)

    缓存: Redis 7 (用于 Session 管理、验证码、高频计数)

    对象存储: Aliyun OSS (存储图片、视频资源)

    反向代理: Nginx (端口转发、静态资源托管)

    容器化: Docker & Docker Compose

    API 文档: Apifox / Swagger / OpenAPI 3

🏗 系统架构 (Architecture)

系统采用典型的分层架构，并预留了 AI 模块的扩展接口。

graph TD
    User[小程序/Web端] --> Nginx[Nginx 网关 (80/443)]
    Nginx -->|/api/auth & /api/user| Boot[Spring Boot 后端 (8080)]
    Nginx -->|/| Static[Web 管理后台静态资源]
    
    Boot --> PG[(PostgreSQL)]
    Boot --> Redis[(Redis 缓存)]
    Boot --> OSS[阿里云 OSS]
    
    Boot -.->|异步调用| AI[AI 服务 (Python/预留)]

核心模块划分

    Auth 模块: 处理微信一键登录、账号注册、邮件验证码、JWT 签发。

    User 模块: 用户信息管理、邮箱绑定、个人资料修改。

    Post 模块: 笔记发布、流媒体处理。

    Interaction 模块: 点赞、收藏、评论。