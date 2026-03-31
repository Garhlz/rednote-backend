# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a hybrid microservices backend for a content community platform ("Sharely/分享派"). The architecture combines Go services (gateway + RPC services) with a Java business service (platform-java), using multiple data stores (MySQL, MongoDB, Redis, Elasticsearch) and RabbitMQ for event-driven sync.

## Common Commands

### Start Infrastructure and Go Services (Docker)
```bash
docker compose up -d --build
```

### Start Java Service (Host Machine)
```bash
cd services/platform-java
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

### Build Go Services
```bash
# Build individual service
cd services/<service-name>
go build .

# Build all Go services via Docker
docker compose build
```

### Regenerate Go-Zero Code from API/Proto Definitions
```bash
# Gateway API handlers/types (from gateway.api)
cd services/gateway-api
goctl api go -api gateway.api -go .

# RPC service code (from proto files)
cd services/<rpc-service>
goctl rpc protoc ../../proto/<name>/<name>.proto --go_out=. --go-grpc_out=. --zrpc_out=. --style=go_zero
```

### Run Go Tests
```bash
cd services/<service-name>
go test ./...
```

### Run Java Tests
```bash
cd services/platform-java
mvn test
```

## Architecture Summary

```
Client → gateway-api (Go, port 8090)
         ├─→ user-rpc (Go, port 8083) → MySQL + Redis
         ├─→ interaction-rpc (Go, port 8081) → Redis + MongoDB + RabbitMQ
         ├─→ search-rpc (Go, port 8082) → Elasticsearch + MongoDB
         └─→ platform-java (Java, port 8080) → MongoDB + Redis + RabbitMQ + ES

RabbitMQ → sync-sidecar (Go) → MongoDB + Elasticsearch (async sync)
```

**Key request flows:**
- Login: gateway → user-rpc → MySQL validation → JWT issuance → Redis whitelist
- Post list/search: gateway → search-rpc (ES query) → interaction-rpc (batch stats) → aggregated PostVO
- Post detail: gateway → platform-java (content) → interaction-rpc (live stats) → aggregation
- Interactions (like/collect/rate): gateway → interaction-rpc → Redis read model → RabbitMQ event

## Service Structure

### Go RPC Services (user-rpc, interaction-rpc, search-rpc)
Each follows go-zero's standard layout:
```
services/<name>/
├── <name>.go              # Entry point
├── etc/<name>.yaml        # Configuration
├── internal/
│   ├── config/            # Config struct
│   ├── svc/               # ServiceContext (DI container)
│   ├── logic/             # Business logic per RPC method
│   ├── model/             # Data models/repository
│   ├── server/            # gRPC server implementation
│   └── mq/                # RabbitMQ publishers (if applicable)
├── <proto-name>/          # Generated proto code
└── <service-name>/        # Generated service client
```

### Gateway API (gateway-api)
```
services/gateway-api/
├── gateway.api            # API definition (routes, types)
├── gateway.go             # Entry point
├── etc/gateway.yaml       # Configuration (RPC endpoints, Redis, JWT)
├── internal/
│   ├── config/            # Config struct
│   ├── svc/               # ServiceContext with RPC clients
│   ├── handler/           # HTTP handlers (grouped by route prefix)
│   ├── logic/             # Business logic per endpoint
│   ├── middleware/        # JWT auth, context injection
│   ├── pkg/               # Utilities (Java proxy, response wrapper)
│   └── types/             # Generated request/response types
```

### Java Service (platform-java)
Standard Spring Boot 3 + Maven structure. Uses MyBatis-Plus (MySQL), Spring Data MongoDB, Spring AMQP (RabbitMQ), and Spring Data Elasticsearch. Contains posts, comments, notifications, and admin functionality.

## Proto Definitions

Proto files define RPC interfaces for Go services:
```
proto/
├── user/user.proto           # Auth, profile, user queries
├── interaction/interaction.proto  # Like, collect, rate, batch stats
└── search/search.proto       # Search, suggest, history
```

## Configuration

Service configs are YAML files in `services/<name>/etc/`:
- Use Docker service names (e.g., `redis`, `etcd`, `mongo`) for container-to-container communication
- Gateway uses `host.docker.internal:8080` to reach Java on host machine
- Environment variables can override defaults (e.g., `${JAVA_API_BASE_URL:...}`)

## Data Stores

| Store | Primary Use |
|-------|-------------|
| MySQL | User accounts, authentication (user-rpc) |
| MongoDB | Posts, comments, notifications, follow relationships |
| Redis | JWT whitelist, verification codes, interaction read models (sets/hashes), Bloom filters |
| Elasticsearch | Post search, suggestions, search history |
| RabbitMQ | Async events: post created, user updated, interaction events → sync-sidecar |

## JWT Authentication

- Access token + refresh token pair
- Refresh tokens tracked in Redis whitelist (by `jti`)
- `tokenVersion` in MySQL enables immediate invalidation (logout, password change)
- Gateway validates JWT and injects user context (`X-User-Id`, `X-User-Role`) for downstream services

## Key Patterns

### Interaction Read Model (Redis)
- Likes: `post_like:{postId}` (Set of userIds)
- Collections: `post_collect:{postId}` (Set)
- Ratings: `post_rate:{postId}` (Hash: userId → score)
- Cold start: lazy warmup with short lock to prevent cache stampede
- Dummy placeholders prevent cache penetration
- Bloom filters reduce unnecessary warmups

### Gateway Aggregation
Post list/detail endpoints aggregate data from multiple services:
1. search-rpc returns post documents
2. interaction-rpc adds `isLiked`, `isCollected`, `likeCount`, `collectCount`, `ratingAverage`
3. Gateway merges into unified `PostVO` response

### Service Communication
- Go services use zRPC (go-zero's gRPC wrapper) with Etcd discovery
- Gateway proxies to Java via HTTP, passing user context headers
- Java can call user-rpc via gRPC (grpc-client-spring-boot-starter)

## Service Ports

| Service | Port |
|---------|------|
| gateway-api | 8090 |
| interaction-rpc | 8081 |
| search-rpc | 8082 |
| user-rpc | 8083 |
| platform-java | 8080 |
| MySQL | 3306 |
| MongoDB | 27017 |
| Redis | 6379 |
| RabbitMQ | 5672 (AMQP), 15672 (Management UI) |
| Elasticsearch | 9200 |
| Etcd | 2379 |

## Important Files

- `docker-compose.yml`: Full local development stack
- `scripts/init-users.sql`: MySQL schema initialization (runs on first container start)
- `openapi.json`: Exported API documentation
- `gateway.api`: Source of truth for HTTP API definitions