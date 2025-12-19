# ==========================================
# Stage 1: Builder (编译阶段)
# ==========================================
# 使用你指定的 Go 1.25 版本
FROM golang:1.25-alpine AS builder

WORKDIR /app

# 配置国内代理，加速依赖下载
ENV GOPROXY=https://goproxy.cn,direct

# 1. 缓存依赖
# 先拷贝 go.mod 和 go.sum，利用 Docker 缓存层
COPY go.mod go.sum ./
RUN go mod download

# 2. 拷贝源码
COPY . .

# 3. 编译
# 注意入口路径：./cmd/sidecar/main.go
RUN CGO_ENABLED=0 GOOS=linux go build -o sync-sidecar ./cmd/sidecar/main.go

# ==========================================
# Stage 2: Runner (运行阶段)
# ==========================================
FROM alpine:latest

WORKDIR /app

# 安装时区
# 关键：将官方源替换为阿里云镜像源
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories

# 这样安装速度会快非常多
RUN apk add --no-cache tzdata ca-certificates
ENV TZ=Asia/Shanghai

# 从编译阶段只拷贝二进制文件
COPY --from=builder /app/sync-sidecar .

# 赋予执行权限
RUN chmod +x ./sync-sidecar

CMD ["./sync-sidecar"]