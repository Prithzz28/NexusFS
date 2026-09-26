# Distributed File Storage System

A production-grade distributed file storage platform built with Java 21, Spring Boot 3, PostgreSQL, and Redis. Implements chunked file storage, replication, fault tolerance, and automatic recovery — inspired by HDFS and object-storage architectures.

## Architecture

```
                         ┌─────────────────────┐
                         │       Client         │
                         │  REST / CLI / Web UI │
                         └──────────┬───────────┘
                                    │
                                    ▼
                         ┌─────────────────────┐
                         │   Master Server      │
                         │                      │
                         │  Authentication      │
                         │  File Management     │
                         │  Metadata            │
                         │  Chunk Placement     │
                         │  Replication         │
                         │  Failure Detection   │
                         └──────┬──────┬────────┘
                                │      │
                    ┌───────────┘      └────────────┐
                    ▼                                ▼
           ┌────────────────┐              ┌────────────────┐
           │ Storage Node 1 │              │ Storage Node 2 │
           │  Chunk Storage │              │  Chunk Storage │
           └────────────────┘              └────────────────┘
                    │
                    ▼
           ┌────────────────┐
           │ Storage Node 3 │
           │  Chunk Storage │
           └────────────────┘

                 Master
                   │
           ┌───────┴────────┐
           ▼                ▼
      PostgreSQL          Redis
      (Metadata)          (Cache)
```

## Features

- **Chunked uploads** — Large files split into configurable chunks (default 4 MB)
- **Replication** — Each chunk replicated across multiple storage nodes (default factor: 3)
- **Fault tolerance** — Automatic detection of failed nodes via heartbeats
- **Auto-recovery** — Under-replicated chunks automatically re-replicated
- **Data integrity** — SHA-256 checksums on every chunk
- **Concurrent transfers** — Parallel chunk upload/download with bounded thread pools
- **JWT authentication** — Secure user access with role-based authorization
- **Rate limiting** — Redis-backed API rate limiting
- **Observability** — Prometheus metrics, Grafana dashboards, structured logging
- **Full Docker Compose stack** — One command to run everything

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 LTS |
| Framework | Spring Boot 3.3.x |
| Build | Maven (multi-module) |
| Database | PostgreSQL 16 |
| Cache | Redis 7 |
| Security | Spring Security + JWT |
| API Docs | OpenAPI / Swagger UI |
| Monitoring | Actuator + Micrometer + Prometheus |
| Containers | Docker + Docker Compose |
| Testing | JUnit 5 + Mockito + Testcontainers |

## Project Structure

```
distributed-file-storage/
├── common/          # Shared DTOs, enums, constants, exceptions
├── master/          # Coordinator: metadata, orchestration, API gateway
├── storage-node/    # Chunk storage service (filesystem-based)
├── docker/          # Docker support files
├── docker-compose.yml
└── pom.xml          # Parent POM
```

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.9+
- Docker & Docker Compose

### Run with Docker Compose

```bash
docker compose up --build
```

This starts:
- **Master** on `http://localhost:8080`
- **PostgreSQL** on `localhost:5432`
- **Redis** on `localhost:6379`
- **Storage Node 1** on `http://localhost:9001`
- **Storage Node 2** on `http://localhost:9002`
- **Storage Node 3** on `http://localhost:9003`

### Verify

```bash
# Master status
curl http://localhost:8080/api/status

# Actuator health
curl http://localhost:8080/actuator/health

# Storage node health
curl http://localhost:9001/internal/health

# Swagger UI
open http://localhost:8080/swagger-ui.html
```

### Build Locally

```bash
./mvnw clean package -DskipTests
```

### Run Tests

```bash
./mvnw test
```

## Configuration

All configuration is externalized via `application.yml` and environment variables:

| Property | Default | Description |
|----------|---------|-------------|
| `CHUNK_SIZE_BYTES` | `4194304` (4 MB) | Size of each file chunk |
| `REPLICATION_FACTOR` | `3` | Number of chunk replicas |
| `HEARTBEAT_INTERVAL_SECONDS` | `5` | Node heartbeat frequency |
| `NODE_FAILURE_TIMEOUT_SECONDS` | `15` | Time before marking node offline |
| `MAX_FILE_SIZE_BYTES` | `5368709120` (5 GB) | Maximum upload file size |

See `.env.example` for the complete list.

## Development Phases

- [x] **Phase 1** — Project foundation, Docker Compose, health checks
- [ ] **Phase 2** — Authentication (JWT, roles, Spring Security)
- [ ] **Phase 3** — File metadata, database schema, CRUD
- [ ] **Phase 4** — Storage node chunk operations, master ↔ node communication
- [ ] **Phase 5** — Chunked upload/download with streaming
- [ ] **Phase 6** — Replication, checksums, concurrent transfers
- [ ] **Phase 7** — Heartbeats, failure detection, auto-recovery
- [ ] **Phase 8** — Redis caching, rate limiting, distributed locks
- [ ] **Phase 9** — Observability (Actuator, Prometheus, Grafana)
- [ ] **Phase 10** — Integration tests, CI/CD
- [ ] **Phase 11** — CLI client, optional React dashboard

## License

MIT
