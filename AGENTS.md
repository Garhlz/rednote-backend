# AGENTS.md

This file provides guidance for agentic coding agents working in this repository.

## Project Overview

Hybrid microservices backend for a content community platform ("Sharely/分享派"). Combines Go services (gateway + RPC services) with a Java business service (platform-java), using multiple data stores (MySQL, MongoDB, Redis, Elasticsearch) and RabbitMQ for event-driven sync.

## Build and Test Commands

### Infrastructure and Services (Docker)
```bash
# Start all infrastructure and Go services
docker compose up -d --build

# Build all Go services
docker compose build

# Build individual Go service
cd services/<service-name>
go build .
```

### Java Service (Host Machine)
```bash
# Start Java service
cd services/platform-java
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run

# Build Java service
mvn clean package

# Run all Java tests
mvn test

# Run a single Java test class
mvn test -Dtest=PostControllerTest

# Run a single Java test method
mvn test -Dtest=PostControllerTest#getPostDetail
```

### Go Services
```bash
# Build individual Go service
cd services/<service-name>
go build .

# Run all Go tests in a service
cd services/<service-name>
go test ./...

# Run a single Go test by name
cd services/<service-name>
go test -run TestFunctionName ./...

# Run tests with verbose output
go test -v ./...

# Run tests with coverage
go test -cover ./...
```

### Code Generation (Go-Zero)
```bash
# Regenerate gateway API handlers/types from gateway.api
cd services/gateway-api
goctl api go -api gateway.api -go .

# Regenerate RPC service code from proto files
cd services/<rpc-service>
goctl rpc protoc ../../proto/<name>/<name>.proto --go_out=. --go-grpc_out=. --zrpc_out=. --style=go_zero
```

### Linting and Formatting
```bash
# Format Go code
cd services/<service-name>
go fmt ./...

# Run Go vet
cd services/<service-name>
go vet ./...

# Format Java code (if using spotless)
mvn spotless:apply
```

## Code Style Guidelines

### Go Services (gateway-api, user-rpc, interaction-rpc, search-rpc, sync-sidecar)

#### Imports
- Group imports in standard order: stdlib, external packages, internal packages
- Use blank lines to separate groups
- Prefer named imports for clarity when package names are ambiguous

```go
import (
    "context"
    
    "github.com/zeromicro/go-zero/core/logx"
    
    "gateway-api/internal/svc"
    "gateway-api/internal/types"
)
```

#### Naming Conventions
- Use camelCase for variables and functions
- Use PascalCase for exported types and functions
- Use snake_case for JSON tags and database columns
- Prefix internal packages with service name when necessary

#### Error Handling
- Always handle errors explicitly; never ignore with `_`
- Use `logx.Error()` or `logx.Errorf()` for logging errors
- Return errors to callers; don't swallow them
- Wrap errors with context when propagating

#### Structure Patterns
- Follow go-zero standard layout: `internal/{config,svc,logic,model,server,mq}`
- Use dependency injection via `svc.ServiceContext`
- Keep business logic in `logic` package, data access in `model`
- Use `logx.WithContext(ctx)` for contextual logging

#### Testing
- Place test files adjacent to implementation: `*_test.go`
- Use table-driven tests for multiple test cases
- Mock external dependencies using interfaces
- Use `testify` assertions if available in the codebase

### Java Service (platform-java)

#### Imports
- Use explicit imports; avoid wildcard imports (`*`)
- Group imports: `java.*`, `javax.*`, `org.*`, `com.*`, `cn.*`
- Use static imports for test assertions only

#### Naming Conventions
- Use camelCase for variables and methods
- Use PascalCase for classes and interfaces
- Use UPPER_SNAKE_CASE for constants
- Prefix DTO/VO classes appropriately: `PostCreateDTO`, `PostVO`

#### Annotations
- Use `@RestController` for REST endpoints
- Use `@Valid` for request body validation
- Use `@OperationLog` for audit logging
- Use `@Transactional` for database transactions in tests

#### Error Handling
- Use `Result<T>` wrapper for API responses
- Throw `BusinessException` for business logic errors
- Use `@ExceptionHandler` or `@ControllerAdvice` for global error handling
- Log errors with appropriate levels (ERROR, WARN, INFO)

#### Testing
- Use `@SpringBootTest` for integration tests
- Use `@AutoConfigureMockMvc` for controller tests
- Use `@Transactional` to rollback test data
- Use `@MockBean` to mock external dependencies
- Use `@Disabled` for tests that are temporarily skipped

#### Data Transfer Objects
- Use Lombok `@Data` for DTOs and VOs
- Use `@Valid` annotations for validation constraints
- Keep DTOs separate from entity classes
- Use Builder pattern for complex object construction

### General Guidelines

#### Documentation
- Add comments only when business logic is non-obvious
- Use JavaDoc for public APIs in Java
- Use GoDoc conventions for exported functions in Go

#### Configuration
- Use YAML files in `services/<name>/etc/` for configuration
- Use environment variables for deployment-specific values
- Use Docker service names for container communication

#### Database Access
- Go: Use repository pattern in `model` package
- Java: Use MyBatis-Plus for MySQL, Spring Data for MongoDB
- Always use parameterized queries to prevent SQL injection

#### API Design
- Follow RESTful conventions for endpoints
- Use consistent response format: `Result<T>` wrapper
- Use appropriate HTTP status codes
- Version APIs when making breaking changes

#### Git Workflow
- Create feature branches from `main`
- Use descriptive commit messages
- Run tests before committing
- Keep PRs focused on single features/fixes

## Important Files Reference

- `docker-compose.yml`: Full local development stack
- `scripts/init-users.sql`: MySQL schema initialization
- `gateway.api`: Source of truth for HTTP API definitions
- `proto/`: RPC interface definitions for Go services
- `CLAUDE.md`: Extended project documentation

## Service Ports

| Service | Port | Description |
|---------|------|-------------|
| gateway-api | 8090 | HTTP API gateway |
| interaction-rpc | 8081 | Like/collect/rate interactions |
| search-rpc | 8082 | Search functionality |
| user-rpc | 8083 | User authentication/profile |
| platform-java | 8080 | Business logic (posts, comments, notifications) |
| MySQL | 3306 | User data |
| MongoDB | 27017 | Posts, comments, notifications |
| Redis | 6379 | Cache, JWT whitelist |
| RabbitMQ | 5672 | Message queue |
| Elasticsearch | 9200 | Search index |
| Etcd | 2379 | Service discovery |
