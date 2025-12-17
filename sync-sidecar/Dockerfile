# sync-sidecar/Dockerfile

# 使用 Alpine
FROM alpine:latest

WORKDIR /app

# 安装时区 (本地构建网络没问题，这里能过)
RUN apk add --no-cache tzdata
ENV TZ=Asia/Shanghai

# 复制二进制文件 (注意：这里复制的是本地编译好的 linux 文件)
COPY sync-sidecar-linux /app/sync-sidecar

RUN chmod +x /app/sync-sidecar

CMD ["./sync-sidecar"]