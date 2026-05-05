# SRMP One-Click Startup Guide

## Default Docker Startup

Run from the repository root:

```bash
./scripts/srmp-one-click-start.sh
```

The command starts PostGIS, Redis, MinIO, the Java backend, `srmp-ai-orchestrator`, and the frontend. It initializes schemas and sample data, builds `srmp-backend`, `srmp-ai-orchestrator`, and `srmp-frontend`, then runs readiness checks.

Only Docker and Docker Compose are required for the default startup path.

## Access

- Frontend: http://localhost:5173
- Backend API: http://localhost:8080
- LangGraph Runtime: http://localhost:18080
- LangGraph Ops: http://localhost:5173/agent/langgraph-ops
- MinIO console: http://localhost:9001
- Default tenant: `default`
- Default route/year: `G210 / 2026`

## Common Options

```bash
./scripts/srmp-one-click-start.sh --reset-demo
./scripts/srmp-one-click-start.sh --skip-build
./scripts/srmp-one-click-start.sh --backend-only
./scripts/srmp-one-click-start.sh --frontend-only
./scripts/srmp-one-click-start.sh --no-start
./scripts/srmp-one-click-start.sh --check-only
./scripts/srmp-one-click-start.sh --local-dev
./scripts/srmp-one-click-start.sh --no-orchestrator
```

`--no-orchestrator` starts the native Java AI path only and skips `srmp-ai-orchestrator` checks.

## Logs

```bash
docker compose -f docker-compose.yml -f docker-compose.app.yml logs -f backend
docker compose -f docker-compose.yml -f docker-compose.app.yml logs -f frontend
docker compose -f docker-compose.yml -f docker-compose.app.yml -f docker-compose.langgraph.yml logs -f srmp-ai-orchestrator
docker compose logs -f postgres redis minio
```

Local development mode writes:

```text
logs/srmp-backend.log
logs/srmp-frontend.log
```

## Data Initialization

Default initialization is idempotent. It creates missing schema, dictionaries, admin data, AI tables, Trace tables, template tables, Phase35 sample templates, and G210/2026 demo business data. The readiness gate also checks that enabled solution templates and template versions are present.

`--reset-demo` deletes generated G210/2026 demo rows and recreates them. It does not delete unrelated routes or user-created data.

## Troubleshooting

Check Docker service state:

```bash
docker compose -f docker-compose.yml -f docker-compose.app.yml ps
docker compose -f docker-compose.yml -f docker-compose.app.yml -f docker-compose.langgraph.yml ps
```

Re-run readiness checks:

```bash
./scripts/srmp-one-click-start.sh --check-only
```

Rebuild application containers:

```bash
docker compose -f docker-compose.yml -f docker-compose.app.yml build backend frontend
docker compose -f docker-compose.yml -f docker-compose.app.yml -f docker-compose.langgraph.yml build backend srmp-ai-orchestrator frontend
```

Check LangGraph directly:

```bash
curl http://localhost:18080/health
curl http://localhost:18080/ready -H 'X-Tenant-Id: default'
```
