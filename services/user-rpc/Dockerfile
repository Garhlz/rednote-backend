# Stage 1: build
FROM golang:1.25-alpine AS builder

ENV GOPROXY=https://goproxy.cn,direct

WORKDIR /app

COPY go.mod go.sum ./
RUN go mod download

COPY . .
COPY etc ./etc

RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o user-rpc user.go

# Stage 2: run
FROM alpine:latest

RUN apk --no-cache add ca-certificates tzdata
ENV TZ=Asia/Shanghai

WORKDIR /app

COPY --from=builder /app/user-rpc .
COPY --from=builder /app/etc/user.yaml ./etc/

EXPOSE 8080

CMD ["./user-rpc", "-f", "etc/user.yaml"]
