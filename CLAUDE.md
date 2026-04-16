# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

总是使用简体中文进行回答和注释
## Commands

- **Start Infrastructure and Go Services**: `docker compose up -d --build` (this starts all dependencies like Redis, MongoDB, Elasticsearch, RabbitMQ, Etcd, and the Go services).
- **Run Java Service (Local)**: `./scripts/run-platform-java-with-otel.sh` (runs the Spring Boot application with OpenTelemetry enabled).
- **Build Go Services**: Run `go build ./...` inside any specific Go service directory (e.g., `cd services/gateway-api && go build ./...`).
- **Run Go Tests**: Run `go test ./...` inside a specific Go service directory to run tests for that service.

## Project Architecture

This is a hybrid microservices backend for a content community (like a "Sharely" app) using a **Go + Java** dual-stack approach. The architecture follows a progressive migration strategy, where a core Java monolithic application is gradually broken down by extracting high-frequency, read-heavy, and high-concurrency domains into dedicated Go services.

### Core Components

1.  **API Gateway (`services/gateway-api`)**:
    *   Acts as the unified HTTP entry point (BFF - Backend for Frontend).
    *   Handles JWT parsing, authentication (compatible with both strict auth and anonymous access with partial user context), and request routing.
    *   Routes requests either to Go RPC services or proxies them to the Java service.
    *   Aggregates data from multiple services (e.g., combining search results with user interaction states).

2.  **Domain Services (Go/gRPC using go-zero)**:
    *   **`user-rpc`**: Manages accounts, login/logout, tokens (Access/Refresh), email verification, and profile updates.
    *   **`interaction-rpc`**: Handles high-frequency interactions like likes, collections, and ratings. Uses Redis heavily as a read model for fast state checks.
    *   **`search-rpc`**: Manages post searching, autocomplete suggestions, and search history via Elasticsearch.
    *   **`comment-rpc`**: Manages the comment tree (creation, deletion, top-level/sub-comment lists).
    *   **`notification-rpc`**: Manages the notification center, unread counts, and read states.

3.  **Java Core (`services/platform-java`)**:
    *   Built with Spring Boot.
    *   Maintains the primary write paths for core entities (like creating posts), the admin backend, and complex business orchestrations that haven't been migrated yet.
    *   Represents the "legacy" core in this progressive migration architecture.

4.  **Async/Sync Layer (`services/sync-sidecar`)**:
    *   A Go sidecar service that listens to RabbitMQ events.
    *   Responsible for updating the Elasticsearch index and handling logging side-effects based on events emitted by the main services (e.g., when a post is created in Java, it emits an event, and this sidecar updates ES for the `search-rpc`).
    *   Decouples the main write transaction from secondary data store updates (Event-Driven Architecture / Eventual Consistency).

### Data Storage Strategy

*   **MySQL**: Core relational data requiring strong transactions (User accounts, auth data).
*   **MongoDB**: Document store for posts, comments, notifications, and interaction details.
*   **Redis**: Caching, session management (JWT invalidation via `jti`/versions), and crucially, as a **Read Model** for high-frequency interaction states (likes/collections).
*   **Elasticsearch**: Full-text search, relevance/hotness sorting, and suggestions.
*   **RabbitMQ**: Event bus for asynchronous decoupling (e.g., post creation -> search index update, comment creation -> notification).
*   **Etcd**: Service discovery for Go RPC services.

### Observability

The project has a unified local observability stack configured via Docker Compose:
*   **Jaeger**: Distributed tracing (OpenTelemetry).
*   **Loki + Promtail + Grafana**: Structured logging.
*   **Prometheus**: Metrics collection.
All services (both Go and Java) are configured to export traces, logs, and metrics to this stack, using standardized fields (`traceId`, `requestId`, `service`) to correlate requests across HTTP, gRPC, and MQ boundaries.
