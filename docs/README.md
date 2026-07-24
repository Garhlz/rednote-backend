# Sharely 文档导航

文档按用途分为项目说明、开发运维、协议规范和面试复习四类。根目录 [README](../README.md) 只保留项目概览和快速启动，详细内容以本目录为准。

## 项目与进度

- [开发 TODO](todo-list.md)：功能、测试和验证状态
- [简历与面试速查](resume_project_summary.md)：项目介绍、技术选型、问题复盘和面试问答
- [Go 测试覆盖率报告](go_coverage_report.md)：模块/核心包覆盖率、缺口与提升优先级

## 开发与运维

- [可观测性](observability.md)：Trace、日志、指标和常用排障路径
- [Grafana 面板](grafana.md)：PromQL 与面板配置
- [各服务 README](../services/)：服务职责、依赖和本地构建方式
- [Agent 开发约定](../AGENTS.md)：供代码代理使用的仓库规范，以根目录版本为唯一事实源

## 协议与数据

- [业务错误码](business_codes.md)：统一响应和业务码约定
- [用户事件契约](user_events.md)：用户更新/删除事件及消费者
- [`proto/`](../proto/)：gRPC / Protobuf 接口定义

## 通用复习

- [后端实习复习](intern.md)：Go、操作系统、数据库和网络知识

`intern_prepare.md` 是早期项目面试材料的兼容入口，内容已合并到 `resume_project_summary.md`，不再单独维护。
