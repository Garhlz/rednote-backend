# syntax=docker/dockerfile:1.5
# Stage 1: build
FROM golang:1.25-alpine AS builder

ARG GOPROXY=https://goproxy.cn,direct
ENV GOPROXY=${GOPROXY}

WORKDIR /app

COPY go.mod go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    go mod download

COPY . .
COPY etc ./etc

RUN --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o user-rpc user.go

# Stage 2: run
FROM alpine:latest

RUN apk --no-cache add ca-certificates tzdata
ENV TZ=Asia/Shanghai

WORKDIR /app

COPY --from=builder /app/user-rpc .
COPY --from=builder /app/etc/user.yaml ./etc/

EXPOSE 8080

CMD ["./user-rpc", "-f", "etc/user.yaml"]
