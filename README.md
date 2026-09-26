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
- **Prometheus** on `http://localhost:9090`
- **Grafana** on `http://localhost:3000` (admin/admin)

### Verify

```bash
# Cluster status & node overview
curl http://localhost:8080/api/nodes

# Actuator Prometheus metrics
curl http://localhost:8080/actuator/prometheus

# Storage node health
curl http://localhost:9001/actuator/health

# Swagger UI
open http://localhost:8080/swagger-ui.html
```

### CLI Client Usage

The DFS CLI is located in `cli/dfs`. Make it available in your path or run it directly:

```bash
# Register a new account
./cli/dfs register alice alice@example.com Secret123!

# Login
./cli/dfs login alice Secret123!

# Check cluster status and storage nodes
./cli/dfs status
./cli/dfs nodes

# Upload a file (automatically chunked, placed, and replicated)
./cli/dfs put ./dataset.csv

# List files
./cli/dfs ls

# Download a file by ID (automatically reassembled from chunk replicas)
./cli/dfs get <file-id> ./downloaded_dataset.csv

# Delete a file
./cli/dfs rm <file-id>

# Logout
./cli/dfs logout
```

### Build & Run Tests Locally

```bash
# Build all modules
./mvnw clean package -DskipTests

# Run full automated test suite (52 tests across Master & Storage Node)
./mvnw test
```

## API Reference

### Authentication (`/api/auth`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/auth/register` | Register new user account |
| `POST` | `/api/auth/login` | Login and obtain JWT token |

### File Management (`/api/files`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/files` | Register new file metadata |
| `GET` | `/api/files` | List authenticated user's files (paginated) |
| `GET` | `/api/files/{id}` | Get detailed file metadata and chunks |
| `GET` | `/api/files/search?query=...` | Search files by name |
| `PATCH` | `/api/files/{id}/rename` | Rename file |
| `DELETE` | `/api/files/{id}` | Soft-delete file |
| `POST` | `/api/files/{id}/restore` | Restore soft-deleted file |

### Chunked Transfer (`/api/files`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/files/init-upload` | Initialize chunked upload session & node allocations |
| `POST` | `/api/files/{sessionId}/chunks/{index}` | Upload specific chunk binary with SHA-256 verification |
| `POST` | `/api/files/{sessionId}/complete` | Verify all chunks and activate file |
| `GET` | `/api/files/{id}/download` | Stream complete file reassembled from chunks |
| `GET` | `/api/files/{id}/manifest` | Get chunk download manifest |

### Cluster Nodes (`/api/nodes`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/nodes` | List active storage nodes and cluster capacity |
| `POST` | `/api/nodes/register` | Storage node registration (self-enrollment) |
| `POST` | `/internal/nodes/heartbeat` | Storage node periodic heartbeat |

## Configuration

All configuration is externalized via `application.yml` and environment variables:

| Property | Default | Description |
|----------|---------|-------------|
| `CHUNK_SIZE_BYTES` | `4194304` (4 MB) | Size of each file chunk |
| `REPLICATION_FACTOR` | `3` | Number of chunk replicas |
| `HEARTBEAT_INTERVAL_SECONDS` | `5` | Node heartbeat frequency |
| `NODE_FAILURE_TIMEOUT_SECONDS` | `15` | Time before marking node offline |
| `MAX_FILE_SIZE_BYTES` | `5368709120` (5 GB) | Maximum upload file size |

## Development Phases

- [x] **Phase 1** — Project foundation, Docker Compose, health checks
- [x] **Phase 2** — Authentication (JWT, roles, Spring Security)
- [x] **Phase 3** — File metadata, database schema, CRUD
- [x] **Phase 4** — Storage node chunk operations, master ↔ node communication
- [x] **Phase 5** — Chunked upload/download with streaming & placement
- [x] **Phase 6** — Replication, checksums, concurrent transfers
- [x] **Phase 7** — Heartbeats, failure detection, auto-recovery & self-healing
- [x] **Phase 8** — Redis caching, rate limiting, distributed locks
- [x] **Phase 9** — Observability (Actuator, Prometheus, Grafana)
- [x] **Phase 10** — Integration tests, GitHub Actions CI/CD pipeline
- [x] **Phase 11** — CLI client (`cli/dfs`) & Admin cluster monitoring

## License

MIT

