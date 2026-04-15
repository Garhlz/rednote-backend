# AGENTS.md

This file provides the canonical guidance for coding agents working in this repository.

## Project Overview

This repository is a hybrid microservices backend for a content community platform ("Sharely/分享派").

- Architecture: Go gateway + Go RPC services + Java business service
- Gateway: `gateway-api` (Go, go-zero)
- Go RPCs: `user-rpc`, `interaction-rpc`, `search-rpc`, `comment-rpc`, `notification-rpc`
- Java service: `platform-java` (Spring Boot 3)
- Sidecar: `sync-sidecar` (Go) for MQ-driven async sync and operational tasks

## Core Architecture

```text
Client
  -> gateway-api (Go, 8090)
      -> user-rpc
      -> interaction-rpc
      -> search-rpc
      -> comment-rpc
      -> notification-rpc
      -> platform-java (HTTP proxy for remaining business capabilities)

RabbitMQ
  -> sync-sidecar
  -> platform-java event listeners
  -> Go services async publishers/consumers
```

## Data Stores

| Store | Primary Use |
|---|---|
| MySQL | User accounts, authentication, token versioning |
| MongoDB | Posts, comments, notifications, follows |
| Redis | JWT whitelist, interaction read model, verification code, cache |
| Elasticsearch | Post search, suggestions, ranking |
| RabbitMQ | Domain events and async decoupling |
| Etcd | Go service discovery |

## Service Ports

| Service | Port | Description |
|---|---:|---|
| gateway-api | 8090 | HTTP API gateway |
| platform-java | 8080 | Java business service |
| interaction-rpc | 8081 | Interaction RPC |
| search-rpc | 8082 | Search RPC |
| user-rpc | 8083 | User/auth RPC |
| notification-rpc | 8084 | Notification RPC |
| comment-rpc | 8085 | Comment RPC |
| sync-sidecar | 8088 | Sidecar admin / background sync |
| MySQL | 3306 | User DB |
| MongoDB | 27017 | Content DB |
| Redis | 6379 | Cache / whitelist |
| RabbitMQ | 5672 | AMQP |
| RabbitMQ UI | 15672 | Management UI |
| Elasticsearch | 9200 | Search index |
| Etcd | 2379 | Service discovery |
| Jaeger UI | 16686 | Trace query |
| Loki | 3100 | Log store |
| Grafana | 3001 | Dashboards |
| Prometheus | 9091 | Metrics |

## Build and Run

### Infrastructure and Go Services

```bash
# Start infrastructure and Go services
docker compose up -d --build

# Build all Go services through Docker
docker compose build

# Build a single Go service locally
cd services/<service-name>
go build .
```

### Java Service

```bash
cd services/platform-java
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

### Go Tests

```bash
cd services/<service-name>
go test ./...
go test -run TestFunctionName ./...
go test -v ./...
go test -cover ./...
```

### Java Tests

```bash
cd services/platform-java
mvn test
mvn test -Dtest=PostControllerTest
mvn test -Dtest=PostControllerTest#getPostDetail
```

### Code Generation

```bash
# Gateway handlers/types
cd services/gateway-api
goctl api go -api gateway.api -go .

# RPC stubs from proto
cd services/<rpc-service>
goctl rpc protoc ../../proto/<name>/<name>.proto --go_out=. --go-grpc_out=. --zrpc_out=. --style=go_zero
```

### Formatting and Linting

```bash
# Go
cd services/<service-name>
go fmt ./...
go vet ./...

# Java
cd services/platform-java
mvn spotless:apply
```

## Development Conventions

### Go Services

- Follow go-zero standard layout: `internal/{config,svc,logic,model,server,mq}`
- Keep business logic in `logic`, dependencies in `svc.ServiceContext`
- Use explicit error handling; never ignore real failures
- Use contextual logging with `logx.WithContext(ctx)`
- Prefer table-driven tests

### Java Service

- Use explicit imports, avoid wildcard imports
- Use `Result<T>` as the unified response wrapper
- Throw `BusinessException` for business failures
- Use `@Valid`, `@Transactional`, `@RestController` as appropriate
- Keep DTO / VO / entity separation clear

### Configuration

- Service configs live in `services/<name>/etc/`
- Use Docker service names for container-to-container calls
- Use environment variables for deployment-specific overrides
- For local mixed mode, gateway may proxy Java through `host.docker.internal`

## Key Runtime Patterns

### Gateway Aggregation

The gateway is not a pure proxy. For many endpoints it aggregates results from multiple services:

1. search-rpc returns candidate posts
2. interaction-rpc enriches with like/collect/rating stats
3. gateway-api merges into a unified `PostVO`

### Authentication

- JWT access token + refresh token
- Refresh token whitelist in Redis
- `tokenVersion` in MySQL enables immediate invalidation
- Gateway injects user context into downstream requests

### Interaction Read Model

Redis is used as the read model for high-frequency interaction queries:

- Likes: `post_like:{postId}`
- Collections: `post_collect:{postId}`
- Ratings: `post_rate:{postId}`

Patterns used:

- lazy warmup
- short-lived lock to avoid stampede
- dummy placeholders to avoid penetration
- Bloom filters for existence precheck

### Comment / Notification Split

- `comment-rpc` owns comment creation, deletion, root list, sub-list and related Mongo reads
- `notification-rpc` owns notification query and unread count RPC capability
- Java still handles remaining business flows and consumes MQ events for async side effects

### Async Event Flow

RabbitMQ is used for:

- post sync events
- user update propagation
- interaction events
- comment create/delete side effects
- logging / sidecar background processing

When changing event payloads or routing keys, verify both producers and consumers across Go and Java.

## Observability

### Metrics

- Prometheus scrapes service metrics endpoints
- Grafana dashboards visualize service health and latency

### Logs

- Loki + Promtail collect container and Java logs
- Structured logs should include, when available:
  - `service`
  - `traceId`
  - `requestId`
  - `routingKey`

### Tracing

- Go services export OTLP traces to Jaeger
- Java can be started with the OpenTelemetry javaagent
- Cross-service debugging should correlate:
  - HTTP trace
  - gRPC trace
  - MQ publish / consume trace context

## Business Error Conventions

The project uses a unified business-code response style:

```json
{
  "code": 10200,
  "message": "操作成功",
  "data": {}
}
```

- Typical pattern: `[HTTP status][sub code]`
- Keep HTTP status semantics and business code semantics aligned
- Do not return success if async side effects that define consistency have already failed and cannot be recovered

## Important Files

- `docker-compose.yml`: local full stack orchestration
- `services/gateway-api/gateway.api`: source of truth for HTTP API definitions
- `proto/`: RPC definitions
- `scripts/init-users.sql`: MySQL initialization
- `scripts/run-platform-java-with-otel.sh`: Java tracing startup helper
- `docs/todo-list.md`: development backlog / pending work

## Agent Guidance

- Treat this file as the canonical agent-facing project guide
- If `CLAUDE.md` or `GEMINI.md` exist, they are compatibility entrypoints and should not diverge from this file
- When adding new agent instructions, update `AGENTS.md` first
