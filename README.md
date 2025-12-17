# 📱 社交平台小程序"映记" - 后端服务 (Afternoon_3 Group)

软件工程课程大作业 - 下午第三组

这是一个基于 Spring Boot 和微服务架构思想构建的后端系统，为我们的“类小红书”微信小程序及 Web 管理后台提供 API 支持。

- [业务代码规范](./business_codes.md)
- [apifox接口(openapi)](./openapi.json)

## 🛠 技术栈 (Tech Stack)

本项目采用现代化的 Java 后端技术栈，容器化部署。

    开发语言: Java 17 (LTS)

    核心框架: Spring Boot 3.x

    数据库: PostgreSQL + MongoDB
    
    ORM 框架: MyBatis-Plus (高效的数据库操作与动态 SQL)

    缓存: Redis 7 (用于 Session 管理、验证码、高频计数)

    服务器：Aliyun

    对象存储: Aliyun OSS (存储图片、视频资源)

    反向代理: Nginx (端口转发、静态资源托管)

    容器化: Docker & Docker Compose

    API 文档: Apifox / Swagger / OpenAPI 3

    搜索/分析: Elasticsearch 8.11 (IK分词 + Pinyin插件) + Kibana

    数据同步 (Sidecar): Go 1.21 (轻量级消费者，负责 MQ -> ES/Mongo 同步)

    消息队列: RabbitMQ 3.12 (异步解耦、流量削峰、死信处理)

    AI 能力: Qwen-VL (Spring AI 集成，支持多模态理解)

    即时通讯: Tencent Cloud IM (集成用户签名生成 UserSig)

## 🏗 系统架构 (Architecture)
```
graph TD
    User[小程序/Web端] --> Nginx[Nginx 网关 (80/443)]
    Nginx -->|/api| Boot[Spring Boot 后端 (8080)]
    
    Boot --> PostgreSQL[(PostgreSQL: 仅存储 User)]
    Boot --> MongoDB[(MongoDB: 存储 Post/Comment/交互数据)]
    Boot --> Redis[(Redis: 缓存/计数/Session)]
    Boot --> OSS[阿里云 OSS: 图片/视频]
    
    Boot --> MQ[RabbitMQ]
    MQ -->|异步削峰/解耦| Boot
    
    subgraph "异步同步层 (Sidecar Pattern)"
        MQ -->|订阅变更事件| GoSidecar[Go Sidecar (轻量级消费者)]
        GoSidecar -->|1. 回查数据| MongoDB
        GoSidecar -->|2. 写入/更新| ES[(Elasticsearch: 全文检索)]
    end
    
    Boot --> ES[(Elasticsearch: 全文检索/Tag聚合)]
    
    Boot -->|HTTP 请求| AI[大模型 API (QwenVL: 内容理解/生成)]
```

## 核心架构设计亮点
1. **混合存储策略 (Polyglot Persistence)**

- PostgreSQL: 作为 Source of Truth，存储核心用户账号、关系型强事务数据。

- MongoDB: 存储帖子(Post)、评论(Comment)及海量交互数据。利用其 Schema-free 特性冗余用户信息(Nickname/Avatar)，避免 N+1 查询问题。

2. **异步事件驱动与最终一致性 (Event-Driven Architecture)**

- 消息总线 (Message Bus): 引入 RabbitMQ (Topic Exchange) 作为全链路事件总线，实现核心业务的深度解耦。
    
- CQRS 索引同步: 帖子发布/更新写入 MongoDB 后，发送消息至 MQ，消费者异步将数据同步至 Elasticsearch，保证搜索数据的近实时 (NRT) 可见性。
    
- 最终一致性 (Eventual Consistency):

    - 关键业务数据（如用户信息修改）采用“发布-订阅”模式，异步触发 MongoDB 中冗余数据（Post/Comment 作者信息）的批量更新，避免分布式事务的性能损耗。

    - AI 异步介入: 也就是在发帖成功后，异步投递消息触发 AI 摘要生成与自动评论，不阻塞主线程，提升用户体验。

3. **多级缓存与高并发支撑 (Redis Strategy)**

- 写缓冲 (Write-Behind Pattern):
    - 高频互动: 点赞、收藏等操作直接写入 Redis Set (去重) 与 Hash (计数)，通过定时任务或阈值触发异步落库 MongoDB，削峰填谷，保护数据库。

    - 浏览量计数: 使用 Redis HyperLogLog (或原子计数器) 聚合高并发的帖子浏览量 (View Count)，批量定时同步至数据库。

- 安全与会话管理:

    - Token 黑名单: 用户登出或修改密码时，将旧 JWT 加入 Redis Set 黑名单（设置 TTL），实现无状态 Token 的即时失效机制。

    - 验证码缓存: 注册/登录验证码存入 String 类型并设置短时过期。

- 热点数据加速:

    - 热门标签: 对计算代价较高的“热门标签 (Hot Tags)”聚合结果进行缓存，降低 MongoDB 聚合查询压力。

    - 通用缓存: 使用 @Cacheable 对配置信息、用户信息等读多写少的数据进行缓存。

4. **高可用搜索架构 (Advanced Search)**

- Elasticsearch 8: 替代了原有的 MongoDB 正则搜索，性能提升显著。

- 智能分词与补全: 集成 IK 中文分词 与 Pinyin 拼音插件，支持“输入 szu 搜 深圳大学”的混合检索体验，并提供搜索建议 (Suggestion)。

- 混合排序算法: 实现了基于 Function Score 的综合热度排序（结合了 BM25 相关度 + 点赞数对数加权 + 高斯函数时间衰减）。

5. **AI Native 内容生态**

- 全链路 AI 介入: 帖子发布后自动触发 AI 管道。

- 智能生产: 自动生成内容摘要（Summary）、智能打标（Auto-Tagging）。

- 活跃气氛: 引入“AI 省流助手”角色，自动在评论区生成诙谐的总结评论。

- 安全风控: 内容发布前经过 AI 敏感词与合规性校验。

6. **Sidecar 异构微服务模式 (Polyglot Sidecar)**
- **架构决策**: 为了在低内存服务器（4GB）上维持高性能运行，我们将资源密集型的“数据同步消费者”从 Java 主进程中剥离。
- **Go Sidecar**: 使用 Go 语言编写独立的 Sidecar 容器，内存占用仅约 10MB。它专门负责监听 RabbitMQ 的业务变更消息（PostCreate/Update/Delete），并负责将数据从 MongoDB 同步至 Elasticsearch。
- **优势**: 实现了 **主应用（业务逻辑）** 与 **辅助应用（数据管道）** 的语言级解耦，既保留了 Java 生态的开发效率，又利用了 Go 在高并发和低内存下的运行时优势。

7. **低资源环境下的 CI/CD 最佳实践**
- **挑战**: 生产环境网络受限（无法流畅访问 Docker Hub/Github）且内存不足，导致直接在服务器上进行 `docker build` 或 `mvn package` 经常失败或 OOM。
- **解决方案**: 设计了 "Local Build, Remote Load" 的部署流水线。
    - **Java**: 本地编译 JAR 包 -> 增量传输 -> 服务器复用基础镜像运行。
    - **Go**: 本地构建全量 Docker 镜像 -> 导出为 tar.gz -> 传输至服务器 -> `docker load` 加载。
- **效果**: 将部署过程对服务器的 CPU/网络 消耗降至最低，确保了交付的稳定性。

## 核心模块划分

Auth 模块: 
- 处理微信一键登录、账号注册、邮件验证码、JWT 签发。

User 模块: 
- 用户信息管理、邮箱绑定、个人资料修改。

Post 模块:
- 支持图文/视频混合发布流。
- **搜索升级**: 基于 Elasticsearch 实现的综合热度排序（Function Score：结合时间衰减与互动权重）。

Interaction 模块:
- 实现了 点赞/收藏/评分(Rating) 的全套逻辑。
- 基于 Redis Set/Hash 实现去重与快速计数，保障高并发下的数据准确性。

Admin 模块 (后台管理):
- 用户治理: 支持用户列表查询、详情查看及账号封禁/解封操作。
- 内容审计:
    - 帖子管理：查询全站帖子、强制下架/删除违规内容。
    - 审核流：配合 AI 预审结果，对状态为“审核中”的帖子进行人工复核（通过/驳回）。
- 运维工具:
    - 操作日志 (Audit Log): 基于 Spring AOP 切面记录关键操作（如删除、封禁），持久化至 MongoDB 供审计查询。
    - 系统监控: 集成 Prometheus + Grafana 监控 JVM/系统指标，RabbitMQ 控制台监控队列积压，Kibana 可视化 ES 数据。

AI 模块:
- Content Service: 封装 LLM 调用，实现标签生成、摘要提取。
- Audit Service: 文本与图像的自动化审核。
- Agent Service: 模拟虚拟用户（如“课代表”）进行自动评论互动。

## 项目结构
```
graph TD
    Request[前端请求] --> DTO
    DTO --> Controller
    Controller --> Service
    
    subgraph 业务逻辑层
    Service --> Manager[通用业务处理]
    Service --> AI_Client[AI 服务调用]
    end
    
    subgraph 数据持久层
    Manager --> UserMapper[UserMapper (MyBatis)]
    Manager --> MongoRepo[PostRepository (MongoTemplate)]
    Manager --> ESRepo[PostEsRepository (Elasticsearch)]
    end
    
    UserMapper <--> PG[(PostgreSQL)]
    MongoRepo <--> Mongo[(MongoDB)]
    ESRepo <--> ES[(Elasticsearch)]
    
    Service --> VO
    VO --> Result[统一响应]
```

---

## 🐳 Docker 开发环境极速配置指南

为了确保大家开发环境一致，避免“在我这能跑，在你那报错”的玄学问题，我们使用 Docker Compose 一键启动所有依赖服务（Postgres, Mongo, Redis, RabbitMQ, ES, Kibana, Sidecar）。

你不需要在本地手动安装这些数据库，只需要安装 Docker。
1. 准备工作
🛠️ 安装 Docker Desktop

请根据你的操作系统下载并安装 Docker Desktop：

    Windows: (⚠️ 重要：安装时请勾选 Use WSL 2 based engine，这是 Windows 运行 Docker 的最佳实践)

    Mac (Intel/Apple Chip): 直接下载

    Linux: 直接安装 Docker Engine 和 Docker Compose Plugin。

⚙️ 调整内存限制 (重要!)

我们的环境包含 ES 和 Java 后端，比较吃内存。

    打开 Docker Desktop 设置 -> Resources。

    确保 Memory 至少分配 4GB (推荐 6GB+)。

    点击 "Apply & Restart"。

2. ⚠️ 启动前检查 (防坑必读)

在运行命令前，请务必停止你电脑上本地安装的同类服务，否则会端口冲突！

    如果你本地装了 MySQL/Postgres，请停止它 (占用 5432)。

    如果你本地装了 Redis，请停止它 (占用 6379)。

    如果你本地装了 Mongo，请停止它 (占用 27017)。

3. 🚀 一键启动

在项目根目录下打开终端（Terminal / CMD / PowerShell），运行：
```Bash

docker compose -f docker-compose-dev.yml up -d
```

    -f docker-compose-dev.yml: 指定使用开发环境的配置。

    -d: 后台运行 (Detached mode)，不会阻塞你的终端。

    首次运行需要下载镜像，可能需要几分钟，请耐心等待。

验证是否成功

运行以下命令查看容器状态：
```Bash

docker compose -f docker-compose-dev.yml ps
```

如果所有容器的状态 (STATUS) 都是 Up 或 Up (healthy)，恭喜你，环境搭建完成！🎉

4. 💻 如何连接? (开发必看)

容器启动后，端口已经映射到你的本机 (localhost)。在 IDEA / GoLand / Navicat 中直接配置如下：
| 服务 | 主机 (Host) | 端口 (Port) | 用户名 | 密码 | 备注 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| PostgreSQL | localhost | 5432 | postgres | postgres | 数据库名: platform_db |
| MongoDB | localhost | 27017 | (无) | (无) | 数据库名: rednote |
| Redis | localhost | 6379 | (无) | (无) | |
| RabbitMQ | localhost | 5672 | admin | admin | 消息队列端口 |
| Elasticsearch | localhost | 9200 | (无) | (无) | 开发环境关闭了安全认证 |

🌐 常用管理面板

启动后，你可以直接在浏览器访问这些好用的管理界面：

    RabbitMQ 控制台: http://localhost:15672 (账号: admin / admin)

    Kibana (ES 可视化): http://localhost:5601

5. 🛠️ 常用操作速查

停止所有服务 (下班关机前)：
```Bash
docker compose -f docker-compose-dev.yml down
```

查看某个服务的日志 (比如看 Sidecar 为什么报错)：
```Bash

# 查看实时日志 (Ctrl+C 退出)
docker logs -f sidecar-local-dev
# 或者
docker logs -f mq-local-dev
```
重启某个服务 (比如卡住了)：
```Bash

docker compose -f docker-compose-dev.yml restart sync-sidecar-dev
```
清理全部数据 (想重头再来)：
```Bash

# 慎用！这会清空数据库所有数据
docker compose -f docker-compose-dev.yml down -v
```

6. ❓ 常见问题 FAQ

Q: 启动报错 Bind for 0.0.0.0:5432 failed: port is already allocated A: 你的电脑上已经跑了一个 Postgres。请手动停止本地的 Postgres 服务，或者卸载它。

Q: Sidecar 启动报错 connection refused A: Sidecar 依赖 ES 和 MQ。如果这两个还没启动好（Healthcheck 未通过），Sidecar 可能会报错退出。Docker 会自动重启它，通常等待 30 秒左右就会变绿（Healthy）。

Q: ES 启动报错 exited with code 137 A: 内存不足。Docker 也就是被系统杀掉了。请参考第 1 步，调大 Docker Desktop 的内存限制。

Q: 我修改了 sync-sidecar 里的 Go 代码，怎么生效？ A: 我们配置了热挂载。只需重启容器即可生效：
```Bash
docker compose -f docker-compose-dev.yml restart sync-sidecar-dev
```

## 环境变量配置 (.env) [重要]
为了安全起见，我们不再直接修改 application-dev.yml 中的密码，而是通过根目录下的 .env 文件注入环境变量。

- 在项目根目录下创建一个名为 .env 的文件。

- 复制以下内容并修改为你本地的真实配置：

```Properties
# --- 数据库配置 ---
DB_URL=jdbc:postgresql://localhost:5432/platform_db?currentSchema=public
DB_USERNAME=postgres
DB_PASSWORD=postgres

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

# --- 腾讯云 IM (新增) ---
TENCENT_IM_SDK_APPID=你的SDKAppID
TENCENT_IM_SECRET_KEY=你的密钥

# --- 内容安全 (新增) ---
# 是否开启帖子自动进入审核状态 (true=发帖后状态为0, false=直接发布)
POST_AUDIT_ENABLE=false

CHATBOT_ID=276
CHATBOT_NICKNAME=AI省流助手

QWENVL_API_KEY=
```
