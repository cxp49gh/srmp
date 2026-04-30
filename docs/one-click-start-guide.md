# SRMP One-Click Startup Guide

## Default Docker Startup

Run from the repository root:

```bash
./scripts/srmp-one-click-start.sh
```

The command starts PostGIS, Redis, and MinIO, initializes schemas and sample data, builds `srmp-backend` and `srmp-frontend`, starts both containers, and runs readiness checks.

Only Docker and Docker Compose are required for the default startup path.

## Access

- Frontend: http://localhost:5173
- Backend API: http://localhost:8080
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
```

## Logs

```bash
docker compose -f docker-compose.yml -f docker-compose.app.yml logs -f backend
docker compose -f docker-compose.yml -f docker-compose.app.yml logs -f frontend
docker compose logs -f postgres redis minio
```

Local development mode writes:

```text
logs/srmp-backend.log
logs/srmp-frontend.log
```

## Data Initialization

Default initialization is idempotent. It creates missing schema, dictionaries, admin data, AI tables, Trace tables, template tables, Phase35 sample templates, and G210/2026 demo business data.

`--reset-demo` deletes generated G210/2026 demo rows and recreates them. It does not delete unrelated routes or user-created data.

## Troubleshooting

Check Docker service state:

```bash
docker compose -f docker-compose.yml -f docker-compose.app.yml ps
```

Re-run readiness checks:

```bash
./scripts/srmp-one-click-start.sh --check-only
```

Rebuild application containers:

```bash
docker compose -f docker-compose.yml -f docker-compose.app.yml build backend frontend
```
