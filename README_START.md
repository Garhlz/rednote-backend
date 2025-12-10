# 项目启动与运行指南

本指南将帮助你快速启动和运行 WebApp (前端) 和 Platform (后端) 项目。

## 一、 后端项目 (Platform)

后端项目基于 Spring Boot，并使用 Docker Compose 进行容器化部署。

### 1. 环境准备
确保你的电脑或服务器上安装了：
- **Docker** 和 **Docker Compose**
- **Java 17** (如果需要本地开发调试)

### 2. 配置文件 (.env)
我已经为你创建了 `z:\University\szu\Platform\.env` 文件，其中包含了数据库、Redis 以及腾讯 IM 的配置信息。
**注意**：`TENCENT_IM_SDK_APPID` 和 `SECRET_KEY` 已更新为你提供的值。

### 3. 启动服务
在 `z:\University\szu\Platform` 目录下，打开终端（CMD 或 PowerShell），运行以下命令：

```bash
docker-compose up -d
```

该命令会自动启动 PostgreSQL, MongoDB, Redis 以及后端应用。

- **后端接口文档**: `http://localhost:8080/doc.html` (本地运行)
- **注意**: 由于你配置了 Nginx 网关并去除了 8080 端口，前端代码中的请求地址已更新为 `http://8.148.145.178` (默认 80 端口)。

---

## 二、 前端项目 (WebApp)

前端项目是基于 uni-app 开发的微信小程序。

### 1. 环境准备
- **HBuilderX** (IDE)
- **微信开发者工具**

### 2. 启动步骤
1. 打开 **HBuilderX**。
2. 点击菜单栏 **文件** -> **打开目录**，选择 `z:\University\szu\WebApp`。
3. 打开 `manifest.json` 文件，点击 **微信小程序配置**，确保 **AppID** 已填写你自己的小程序 AppID (或者使用测试号)。
4. 点击菜单栏 **运行** -> **运行到小程序模拟器** -> **微信开发者工具**。

### 3. 配置说明
前端代码中所有请求后端 API 的地址已统一修改为：
`http://8.148.145.178` (去除了 8080 端口)

---

## 三、 系统监控

你已配置 Prometheus 和 Grafana 监控，访问信息如下：

- **监控地址**: [http://8.148.145.178/monitor/](http://8.148.145.178/monitor/)
- **账号**: `admin`
- **密码**: `235711`

---

## 四、 常见问题

1. **前端登录没反应？**
   - 检查微信开发者工具是否开启了“不校验合法域名” (详情 -> 本地设置 -> 不校验合法域名)。
   - 检查后端服务是否正常运行。

2. **后端启动失败？**
   - 运行 `docker-compose logs -f backend` 查看日志。
   - 确保 8080, 5432, 27017, 6379 端口没有被占用。
