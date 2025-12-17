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

## 🚀 快速开始 (待修改，Elastic search使用docker启动)

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

# --- 腾讯云 IM (新增) ---
TENCENT_IM_SDK_APPID=你的SDKAppID
TENCENT_IM_SECRET_KEY=你的密钥

# --- 内容安全 (新增) ---
# 是否开启帖子自动进入审核状态 (true=发帖后状态为0, false=直接发布)
POST_AUDIT_ENABLE=false
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

4.2 业务逻辑集成测试 (核心推荐)

这些测试模拟了真实的用户请求流程，且使用了 Mock 技术，不依赖外部第三方服务（如微信、OSS），非常适合开发时自测。

- API 综合流程测试: controller/ApiIntegrationTest.java

    - 内容: 模拟了“微信登录(Mock) -> 获取Token -> 修改个人资料”的全流程。

    - 特点: 验证最基础的用户鉴权与资料管理，自动回滚数据。

- 数据一致性测试 (架构核心): DataConsistencyTest.java

    - 内容: 模拟用户在 PostgreSQL 修改昵称/头像后，验证 MongoDB 中的冗余数据（帖子、评论、关注列表）是否通过异步事件正确同步。

    - 特点: 验证混合存储架构中最关键的“最终一致性”机制。

- 交互闭环测试: controller/InteractionIntegrationTest.java

    - 内容: 模拟用户对帖子进行“点赞 -> 取消点赞 -> 评分”的操作。

    - 特点: 验证 Redis 缓存（去重/计数）与 MongoDB 落库之间的异步配合是否正常。

- 搜索与分词测试: service/SearchIntegrationTest.java

    - 内容: 验证 Jieba 中文分词是否生效，以及 MongoDB 的全文索引能否正确召回数据。

    - 特点: 确保“搜关键词”能搜到对应的帖子，验证搜索引擎逻辑。

- 文件上传测试: controller/CommonControllerTest.java

    - 内容: 测试文件上传接口。

    - 特点: 自动使用本地 Mock 模式，文件会生成在 target/test-uploads 目录下，无需配置阿里云账号。

如果上述测试全部通过，说明你的后端环境及核心业务逻辑已经完美就绪！🎉