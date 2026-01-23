# syntax=docker/dockerfile:1.5
# 阶段 1: 构建 (Builder)
FROM golang:1.25-alpine AS builder

# 开启 Go Modules 代理 (国内加速，也可配 GOPROXY 环境变量)
ARG GOPROXY=https://goproxy.cn,direct
ENV GOPROXY=${GOPROXY}

WORKDIR /app

# 先复制依赖文件，利用 Docker 缓存
COPY go.mod go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    go mod download

# 复制源码
COPY . .
COPY etc ./etc

# 编译 (CGO_ENABLED=0 生成静态二进制文件)
RUN --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o search-rpc search.go

# 阶段 2: 运行 (Runner)
FROM alpine:latest

# 安装时区数据和 ca 证书 (访问 HTTPS 或时间处理需要)
RUN apk --no-cache add ca-certificates tzdata
ENV TZ=Asia/Shanghai

WORKDIR /app

# 从 Builder 阶段把二进制文件和配置文件拷过来
COPY --from=builder /app/search-rpc .
COPY --from=builder /app/etc/search.yaml ./etc/

# gRPC 端口
EXPOSE 8080

CMD ["./search-rpc", "-f", "etc/search.yaml"]
