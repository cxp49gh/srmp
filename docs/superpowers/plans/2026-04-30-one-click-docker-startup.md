# One-Click Docker Startup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a one-command startup flow that runs database dependencies, initializes schemas and sample data, builds backend and frontend Docker images, starts both app containers, and verifies the system is ready.

**Architecture:** Keep the existing `docker-compose.yml` as the dependency layer and add `docker-compose.app.yml` for backend/frontend containers. Add one shell orchestration entry point that starts dependencies first, initializes data through containerized `psql`, then starts app containers and runs readiness checks. Preserve local Java/Node startup behind `--local-dev` so Docker remains the default path.

**Tech Stack:** Docker Compose, Bash, PostgreSQL/PostGIS SQL, Spring Boot 2.7 on Java 8, Vue 3/Vite, nginx.

---

## File Structure

### Docker Deployment

- Create `.dockerignore`
  - Keeps Docker build context small and avoids copying local logs, node modules, target folders, IDE files, and git metadata into images.
- Create `docker-compose.app.yml`
  - Defines `backend` and `frontend` services layered on top of the existing dependency Compose file.
- Create `deploy/docker/backend/Dockerfile`
  - Multi-stage Maven build for `srmp-admin`, runtime JRE image, Java non-proxy flags for Compose service names.
- Create `deploy/docker/frontend/Dockerfile`
  - Multi-stage Node build for `srmp-web-ui`, runtime nginx image.
- Create `deploy/docker/frontend/nginx.conf`
  - Serves the Vue SPA and proxies `/api/` to `backend:8080`.

### Startup Scripts

- Create `scripts/check-one-click-start.sh`
  - Static verification for required Docker files, scripts, documentation, and script syntax.
- Create `scripts/srmp-init-demo.sh`
  - Runs database schema, dictionaries, admin data, AI/Trace/template DDL, Phase35 sample templates, and G210/2026 sample business data.
- Create `scripts/srmp-check-ready.sh`
  - Verifies dependency containers, backend API, frontend page, demo data status, and template sample availability.
- Create `scripts/srmp-one-click-start.sh`
  - User-facing entry point for default Docker startup and optional local development startup.
- Modify `scripts/init-db.sh`
  - Delegates to `scripts/srmp-init-demo.sh` for compatibility.
- Modify `scripts/reset-demo.sh`
  - Delegates to `scripts/srmp-init-demo.sh --reset-demo` for compatibility.

### Database Seed

- Create `srmp-admin/src/main/resources/db/phase36_one_click_demo_seed.sql`
  - Idempotently upserts G210/2026 route, sections, 1000 evaluation units, 1000 assessments, 3000 disease records, and index results.
  - Uses deterministic IDs and `ON CONFLICT` so repeated default initialization does not create duplicates.

### Documentation

- Create `docs/one-click-start-guide.md`
  - Documents default Docker startup, reset mode, local development mode, ports, logs, default credentials, and troubleshooting.
- Modify `README.md`
  - Replaces the primary quick start with `./scripts/srmp-one-click-start.sh`.

---

## Task 1: Verification Skeleton

**Files:**
- Create: `scripts/check-one-click-start.sh`

- [ ] **Step 1: Create the failing verification script**

Create `scripts/check-one-click-start.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

test -f docker-compose.yml
test -f docker-compose.app.yml
test -f .dockerignore
test -f deploy/docker/backend/Dockerfile
test -f deploy/docker/frontend/Dockerfile
test -f deploy/docker/frontend/nginx.conf
test -f scripts/srmp-one-click-start.sh
test -f scripts/srmp-init-demo.sh
test -f scripts/srmp-check-ready.sh
test -f srmp-admin/src/main/resources/db/phase36_one_click_demo_seed.sql
test -f docs/one-click-start-guide.md

bash -n scripts/srmp-one-click-start.sh
bash -n scripts/srmp-init-demo.sh
bash -n scripts/srmp-check-ready.sh

grep -R "backend:" -n docker-compose.app.yml >/dev/null
grep -R "frontend:" -n docker-compose.app.yml >/dev/null
grep -R "srmp-backend" -n docker-compose.app.yml deploy/docker/backend/Dockerfile >/dev/null
grep -R "srmp-frontend" -n docker-compose.app.yml deploy/docker/frontend >/dev/null
grep -R "proxy_pass http://backend:8080/api/" -n deploy/docker/frontend/nginx.conf >/dev/null
grep -R "phase36_one_click_demo_seed.sql" -n scripts/srmp-init-demo.sh >/dev/null
grep -R "docker exec -i srmp-postgres psql" -n scripts/srmp-init-demo.sh >/dev/null
grep -R "docker compose -f docker-compose.yml -f docker-compose.app.yml" -n scripts/srmp-one-click-start.sh docs/one-click-start-guide.md README.md >/dev/null
grep -R "1000" -n srmp-admin/src/main/resources/db/phase36_one_click_demo_seed.sql scripts/srmp-check-ready.sh >/dev/null
grep -R "3000" -n srmp-admin/src/main/resources/db/phase36_one_click_demo_seed.sql scripts/srmp-check-ready.sh >/dev/null

if command -v docker >/dev/null 2>&1; then
  docker compose -f docker-compose.yml -f docker-compose.app.yml config >/tmp/srmp-compose-config.yml
  grep -n "srmp-backend" /tmp/srmp-compose-config.yml >/dev/null
  grep -n "srmp-frontend" /tmp/srmp-compose-config.yml >/dev/null
fi

echo "[OK] one-click Docker startup files are wired"
```

- [ ] **Step 2: Run the verification script and observe the expected failure**

Run:

```bash
bash scripts/check-one-click-start.sh
```

Expected result: it exits non-zero because `docker-compose.app.yml`, Dockerfiles, startup scripts, seed SQL, and docs have not been created yet.

- [ ] **Step 3: Commit the failing verification script**

Run:

```bash
git add scripts/check-one-click-start.sh
git commit -m "test: add one-click startup verification"
```

---

## Task 2: Docker Compose and Image Assets

**Files:**
- Create: `.dockerignore`
- Create: `docker-compose.app.yml`
- Create: `deploy/docker/backend/Dockerfile`
- Create: `deploy/docker/frontend/Dockerfile`
- Create: `deploy/docker/frontend/nginx.conf`

- [ ] **Step 1: Add `.dockerignore`**

Create `.dockerignore`:

```dockerignore
.git
.idea
.vscode
.DS_Store
logs
*.log
**/target
**/node_modules
srmp-web-ui/dist
srmp-web-ui/.vite
deploy/docker/**/*.tmp
docs/superpowers/plans
docs/superpowers/specs
```

- [ ] **Step 2: Add the application Compose layer**

Create `docker-compose.app.yml`:

```yaml
version: "3.8"

services:
  backend:
    image: ${BACKEND_IMAGE:-srmp-backend:latest}
    container_name: srmp-backend
    build:
      context: .
      dockerfile: deploy/docker/backend/Dockerfile
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-demo}
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: ${DB_NAME:-srmp}
      DB_USER: ${DB_USER:-srmp}
      DB_PASSWORD: ${DB_PASSWORD:-srmp123}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      MINIO_ENDPOINT: http://minio:9000
      MINIO_ACCESS_KEY: ${MINIO_ACCESS_KEY:-minioadmin}
      MINIO_SECRET_KEY: ${MINIO_SECRET_KEY:-minioadmin}
      MINIO_BUCKET: ${MINIO_BUCKET:-srmp}
      JAVA_OPTS: ${JAVA_OPTS:-}
    ports:
      - "${BACKEND_PORT:-8080}:8080"
    depends_on:
      - postgres
      - redis
      - minio
    restart: unless-stopped

  frontend:
    image: ${FRONTEND_IMAGE:-srmp-frontend:latest}
    container_name: srmp-frontend
    build:
      context: .
      dockerfile: deploy/docker/frontend/Dockerfile
      args:
        VITE_API_BASE_URL: ${VITE_API_BASE_URL:-}
        VITE_TENANT_ID: ${VITE_TENANT_ID:-default}
    ports:
      - "${FRONTEND_PORT:-5173}:80"
    depends_on:
      - backend
    restart: unless-stopped
```

- [ ] **Step 3: Add the backend Dockerfile**

Create `deploy/docker/backend/Dockerfile`:

```dockerfile
FROM maven:3.8.8-eclipse-temurin-8 AS builder

WORKDIR /workspace
COPY pom.xml .
COPY srmp-common/pom.xml srmp-common/pom.xml
COPY srmp-web/pom.xml srmp-web/pom.xml
COPY srmp-security/pom.xml srmp-security/pom.xml
COPY srmp-tenant/pom.xml srmp-tenant/pom.xml
COPY srmp-base/pom.xml srmp-base/pom.xml
COPY srmp-road-asset/pom.xml srmp-road-asset/pom.xml
COPY srmp-inspection/pom.xml srmp-inspection/pom.xml
COPY srmp-disease/pom.xml srmp-disease/pom.xml
COPY srmp-assessment/pom.xml srmp-assessment/pom.xml
COPY srmp-gis/pom.xml srmp-gis/pom.xml
COPY srmp-import/pom.xml srmp-import/pom.xml
COPY srmp-file/pom.xml srmp-file/pom.xml
COPY srmp-agent/pom.xml srmp-agent/pom.xml
COPY srmp-dashboard/pom.xml srmp-dashboard/pom.xml
COPY srmp-admin/pom.xml srmp-admin/pom.xml

RUN mvn -pl srmp-admin -am dependency:go-offline -DskipTests

COPY srmp-common srmp-common
COPY srmp-web srmp-web
COPY srmp-security srmp-security
COPY srmp-tenant srmp-tenant
COPY srmp-base srmp-base
COPY srmp-road-asset srmp-road-asset
COPY srmp-inspection srmp-inspection
COPY srmp-disease srmp-disease
COPY srmp-assessment srmp-assessment
COPY srmp-gis srmp-gis
COPY srmp-import srmp-import
COPY srmp-file srmp-file
COPY srmp-agent srmp-agent
COPY srmp-dashboard srmp-dashboard
COPY srmp-admin srmp-admin

RUN mvn -pl srmp-admin -am package -DskipTests

FROM eclipse-temurin:8-jre

WORKDIR /app
COPY --from=builder /workspace/srmp-admin/target/srmp-admin-1.0.0.jar /app/srmp-admin.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Djava.net.useSystemProxies=false -Dhttp.nonProxyHosts='localhost|127.*|[::1]|postgres|redis|minio|backend|frontend' -Dhttps.nonProxyHosts='localhost|127.*|[::1]|postgres|redis|minio|backend|frontend' -jar /app/srmp-admin.jar"]
```

- [ ] **Step 4: Add the frontend Dockerfile**

Create `deploy/docker/frontend/Dockerfile`:

```dockerfile
FROM node:20-alpine AS builder

WORKDIR /workspace/srmp-web-ui
ARG VITE_API_BASE_URL=
ARG VITE_TENANT_ID=default
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
ENV VITE_TENANT_ID=${VITE_TENANT_ID}

COPY srmp-web-ui/package.json srmp-web-ui/package-lock.json ./
RUN npm ci

COPY srmp-web-ui ./
RUN npm run build

FROM nginx:1.25-alpine

COPY deploy/docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /workspace/srmp-web-ui/dist /usr/share/nginx/html

EXPOSE 80
```

- [ ] **Step 5: Add nginx SPA and API proxy configuration**

Create `deploy/docker/frontend/nginx.conf`:

```nginx
server {
    listen 80;
    server_name _;

    root /usr/share/nginx/html;
    index index.html;

    client_max_body_size 50m;

    location /api/ {
        proxy_pass http://backend:8080/api/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 30s;
        proxy_send_timeout 180s;
        proxy_read_timeout 180s;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

- [ ] **Step 6: Verify Compose configuration passes**

Run:

```bash
docker compose -f docker-compose.yml -f docker-compose.app.yml config >/tmp/srmp-compose-config.yml
grep -n "srmp-backend" /tmp/srmp-compose-config.yml
grep -n "srmp-frontend" /tmp/srmp-compose-config.yml
```

Expected result: both `grep` commands print matching service/container lines.

- [ ] **Step 7: Commit Docker deployment assets**

Run:

```bash
git add .dockerignore docker-compose.app.yml deploy/docker/backend/Dockerfile deploy/docker/frontend/Dockerfile deploy/docker/frontend/nginx.conf
git commit -m "feat: add dockerized backend and frontend"
```

---

## Task 3: Idempotent Database Initialization and Demo Seed

**Files:**
- Create: `srmp-admin/src/main/resources/db/phase36_one_click_demo_seed.sql`
- Create: `scripts/srmp-init-demo.sh`

- [ ] **Step 1: Add deterministic G210/2026 demo seed SQL**

Create `srmp-admin/src/main/resources/db/phase36_one_click_demo_seed.sql`:

```sql
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO tenant(id, tenant_code, tenant_name, tenant_type, status, created_at, updated_at, deleted)
VALUES ('default', 'default', '默认租户', 'PLATFORM', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)
ON CONFLICT (tenant_code) DO UPDATE
SET tenant_name = EXCLUDED.tenant_name,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP,
    deleted = FALSE;

WITH route_shape AS (
  SELECT ST_GeomFromText('LINESTRING(106.0043335 25.7825803,106.0427856 25.9247018,106.2254333 25.8678737,106.1293030 25.7405291)', 4326) AS geom
)
INSERT INTO road_route(id, tenant_id, route_code, route_name, route_type, admin_grade, technical_grade, start_stake, end_stake, length_km, geom, remark, created_at, updated_at, deleted)
SELECT 'demo-route-g210', 'default', 'G210', 'G210 演示路线', 'NATIONAL_HIGHWAY', 'NATIONAL', 'FIRST_CLASS', 0, 100, 100, geom, 'one-click demo seed', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
FROM route_shape
ON CONFLICT (tenant_id, route_code) DO UPDATE
SET route_name = EXCLUDED.route_name,
    route_type = EXCLUDED.route_type,
    admin_grade = EXCLUDED.admin_grade,
    technical_grade = EXCLUDED.technical_grade,
    start_stake = EXCLUDED.start_stake,
    end_stake = EXCLUDED.end_stake,
    length_km = EXCLUDED.length_km,
    geom = EXCLUDED.geom,
    remark = EXCLUDED.remark,
    updated_at = CURRENT_TIMESTAMP,
    deleted = FALSE;

WITH route_row AS (
  SELECT id AS route_id, geom
  FROM road_route
  WHERE tenant_id = 'default' AND route_code = 'G210'
),
section_seed AS (
  SELECT gs AS section_no,
         (gs * 25)::numeric(12,3) AS start_stake,
         ((gs + 1) * 25)::numeric(12,3) AS end_stake,
         ST_MakeLine(
           ST_LineInterpolatePoint(route_row.geom, gs / 4.0),
           ST_LineInterpolatePoint(route_row.geom, (gs + 1) / 4.0)
         ) AS geom,
         route_row.route_id
  FROM route_row
  CROSS JOIN generate_series(0, 3) AS gs
)
INSERT INTO road_section(id, tenant_id, route_id, route_code, section_code, section_name, direction, start_stake, end_stake, length_km, pavement_type, technical_grade, geom, created_at, updated_at, deleted)
SELECT 'demo-section-g210-' || section_no,
       'default',
       route_id,
       'G210',
       'G210_K' || start_stake::int || '_K' || end_stake::int,
       'G210 K' || start_stake::int || '-K' || end_stake::int || ' 演示路段',
       'BOTH',
       start_stake,
       end_stake,
       25,
       'ASPHALT',
       'FIRST_CLASS',
       geom,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP,
       FALSE
FROM section_seed
ON CONFLICT (tenant_id, section_code) DO UPDATE
SET section_name = EXCLUDED.section_name,
    start_stake = EXCLUDED.start_stake,
    end_stake = EXCLUDED.end_stake,
    length_km = EXCLUDED.length_km,
    pavement_type = EXCLUDED.pavement_type,
    technical_grade = EXCLUDED.technical_grade,
    geom = EXCLUDED.geom,
    updated_at = CURRENT_TIMESTAMP,
    deleted = FALSE;

WITH route_row AS (
  SELECT id AS route_id, geom
  FROM road_route
  WHERE tenant_id = 'default' AND route_code = 'G210'
),
unit_seed AS (
  SELECT gs AS unit_no,
         (gs * 0.1)::numeric(12,3) AS start_stake,
         ((gs + 1) * 0.1)::numeric(12,3) AS end_stake,
         floor(gs / 250)::int AS section_no,
         ST_MakeLine(
           ST_LineInterpolatePoint(route_row.geom, gs / 1000.0),
           ST_LineInterpolatePoint(route_row.geom, (gs + 1) / 1000.0)
         ) AS geom,
         route_row.route_id
  FROM route_row
  CROSS JOIN generate_series(0, 999) AS gs
)
INSERT INTO road_evaluation_unit(id, tenant_id, route_id, section_id, route_code, unit_code, direction, lane_no, start_stake, end_stake, length_m, geom, center_point, pavement_type, technical_grade, road_width, created_at, updated_at, deleted)
SELECT 'demo-unit-g210-' || lpad(unit_no::text, 4, '0'),
       'default',
       unit_seed.route_id,
       section.id,
       'G210',
       'G210_BOTH_U' || lpad(unit_no::text, 4, '0'),
       'BOTH',
       1,
       unit_seed.start_stake,
       unit_seed.end_stake,
       100,
       unit_seed.geom,
       ST_LineInterpolatePoint(unit_seed.geom, 0.5),
       'ASPHALT',
       'FIRST_CLASS',
       12.00,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP,
       FALSE
FROM unit_seed
JOIN road_section section
  ON section.tenant_id = 'default'
 AND section.section_code = 'G210_K' || (unit_seed.section_no * 25) || '_K' || ((unit_seed.section_no + 1) * 25)
ON CONFLICT (tenant_id, unit_code) DO UPDATE
SET start_stake = EXCLUDED.start_stake,
    end_stake = EXCLUDED.end_stake,
    length_m = EXCLUDED.length_m,
    geom = EXCLUDED.geom,
    center_point = EXCLUDED.center_point,
    updated_at = CURRENT_TIMESTAMP,
    deleted = FALSE;

WITH units AS (
  SELECT id, route_id, section_id, route_code, direction, start_stake, end_stake,
         substring(unit_code from '[0-9]{4}$')::int AS unit_no
  FROM road_evaluation_unit
  WHERE tenant_id = 'default' AND route_code = 'G210' AND deleted = FALSE
)
INSERT INTO assessment_result(id, tenant_id, object_type, object_id, route_id, section_id, unit_id, route_code, direction, start_stake, end_stake, year, mqi, sci, pqi, bci, tci, pci, rqi, rdi, pbi, pwi, sri, pssi, grade, assessed_at, created_at, updated_at, deleted)
SELECT 'demo-assess-g210-' || lpad(unit_no::text, 4, '0'),
       'default',
       'EVALUATION_UNIT',
       id,
       route_id,
       section_id,
       id,
       route_code,
       direction,
       start_stake,
       end_stake,
       2026,
       (72 + (unit_no % 27))::numeric,
       (76 + (unit_no % 19))::numeric,
       (70 + (unit_no % 25))::numeric,
       (78 + (unit_no % 17))::numeric,
       (74 + (unit_no % 21))::numeric,
       (68 + (unit_no % 29))::numeric,
       (73 + (unit_no % 22))::numeric,
       (71 + (unit_no % 24))::numeric,
       (75 + (unit_no % 20))::numeric,
       (72 + (unit_no % 23))::numeric,
       (70 + (unit_no % 26))::numeric,
       (69 + (unit_no % 28))::numeric,
       CASE
         WHEN unit_no % 100 = 0 THEN 'BAD'
         WHEN unit_no % 17 = 0 THEN 'POOR'
         WHEN unit_no % 5 = 0 THEN 'MEDIUM'
         WHEN unit_no % 3 = 0 THEN 'GOOD'
         ELSE 'EXCELLENT'
       END,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP,
       FALSE
FROM units
ON CONFLICT (id) DO UPDATE
SET mqi = EXCLUDED.mqi,
    pqi = EXCLUDED.pqi,
    pci = EXCLUDED.pci,
    grade = EXCLUDED.grade,
    updated_at = CURRENT_TIMESTAMP,
    deleted = FALSE;

WITH units AS (
  SELECT id, route_id, section_id, route_code, direction, start_stake, end_stake, center_point,
         substring(unit_code from '[0-9]{4}$')::int AS unit_no
  FROM road_evaluation_unit
  WHERE tenant_id = 'default' AND route_code = 'G210' AND deleted = FALSE
),
disease_seed AS (
  SELECT units.*, slot,
         CASE slot WHEN 1 THEN 'CRACK' WHEN 2 THEN 'POTHOLE' ELSE 'SUBSIDENCE' END AS disease_type,
         CASE slot WHEN 1 THEN '裂缝' WHEN 2 THEN '坑槽' ELSE '沉陷' END AS disease_name,
         CASE
           WHEN units.unit_no % 11 = 0 THEN 'HEAVY'
           WHEN units.unit_no % 3 = 0 THEN 'MEDIUM'
           ELSE 'LIGHT'
         END AS severity
  FROM units
  CROSS JOIN generate_series(1, 3) AS slot
)
INSERT INTO disease_record(id, tenant_id, route_id, section_id, unit_id, route_code, direction, lane_no, start_stake, end_stake, disease_category, disease_type, disease_name, severity, quantity, measure_unit, damage_area, damage_length, source, confidence, geom, status, verified, created_at, updated_at, deleted)
SELECT 'demo-disease-g210-' || lpad(unit_no::text, 4, '0') || '-' || slot,
       'default',
       route_id,
       section_id,
       id,
       route_code,
       direction,
       1,
       start_stake,
       end_stake,
       'PAVEMENT',
       disease_type,
       disease_name,
       severity,
       CASE slot WHEN 1 THEN 8 + (unit_no % 40) WHEN 2 THEN 1 + (unit_no % 4) ELSE 2 + (unit_no % 9) END,
       CASE slot WHEN 2 THEN '处' ELSE 'm' END,
       CASE slot WHEN 2 THEN 1.5 + (unit_no % 7) ELSE NULL END,
       CASE slot WHEN 2 THEN NULL ELSE 6 + (unit_no % 80) END,
       'DEMO_DATA',
       0.9500,
       center_point,
       'VERIFIED',
       TRUE,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP,
       FALSE
FROM disease_seed
ON CONFLICT (id) DO UPDATE
SET severity = EXCLUDED.severity,
    quantity = EXCLUDED.quantity,
    geom = EXCLUDED.geom,
    updated_at = CURRENT_TIMESTAMP,
    deleted = FALSE;

INSERT INTO index_result(id, tenant_id, assessment_id, unit_id, route_id, section_id, route_code, direction, start_stake, end_stake, year, index_code, index_name, index_value, grade, created_at, updated_at, deleted)
SELECT 'demo-index-mqi-' || unit_id,
       tenant_id,
       id,
       unit_id,
       route_id,
       section_id,
       route_code,
       direction,
       start_stake,
       end_stake,
       year,
       'MQI',
       '公路技术状况指数',
       mqi,
       grade,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP,
       FALSE
FROM assessment_result
WHERE tenant_id = 'default' AND route_code = 'G210' AND year = 2026
ON CONFLICT (id) DO UPDATE
SET index_value = EXCLUDED.index_value,
    grade = EXCLUDED.grade,
    updated_at = CURRENT_TIMESTAMP,
    deleted = FALSE;
```

- [ ] **Step 2: Add the initialization script with Docker-first `psql`**

Create `scripts/srmp-init-demo.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-srmp}"
DB_USER="${DB_USER:-srmp}"
DB_PASSWORD="${DB_PASSWORD:-srmp123}"
PSQL_BIN="${PSQL_BIN:-}"
RESET_DEMO=0
LOCAL_DEV=0

while [ "$#" -gt 0 ]; do
  case "$1" in
    --reset-demo)
      RESET_DEMO=1
      shift
      ;;
    --local-dev)
      LOCAL_DEV=1
      shift
      ;;
    --help)
      echo "Usage: $0 [--reset-demo] [--local-dev]"
      exit 0
      ;;
    *)
      echo "[FAIL] unknown argument: $1"
      exit 1
      ;;
  esac
done

docker_psql_available() {
  command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' | grep -qx 'srmp-postgres'
}

run_sql_file() {
  local file="$1"
  local label="$2"
  test -f "$file" || {
    echo "[FAIL] SQL file missing: $file"
    exit 1
  }

  echo "==> $label"
  if [ -z "$PSQL_BIN" ] && [ "$LOCAL_DEV" = "0" ] && docker_psql_available; then
    docker exec -i srmp-postgres psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 < "$file"
  elif [ -z "$PSQL_BIN" ] && docker_psql_available; then
    docker exec -i srmp-postgres psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 < "$file"
  else
    local bin="${PSQL_BIN:-psql}"
    PGPASSWORD="$DB_PASSWORD" "$bin" -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -f "$file"
  fi
  echo "[OK] $label"
}

run_sql_text() {
  local label="$1"
  local sql="$2"
  echo "==> $label"
  if [ -z "$PSQL_BIN" ] && docker_psql_available; then
    printf '%s\n' "$sql" | docker exec -i srmp-postgres psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1
  else
    local bin="${PSQL_BIN:-psql}"
    printf '%s\n' "$sql" | PGPASSWORD="$DB_PASSWORD" "$bin" -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1
  fi
  echo "[OK] $label"
}

if [ "$RESET_DEMO" = "1" ]; then
  run_sql_text "reset G210 demo data" "
DELETE FROM index_result WHERE tenant_id = 'default' AND route_code = 'G210';
DELETE FROM assessment_result WHERE tenant_id = 'default' AND route_code = 'G210';
DELETE FROM disease_record WHERE tenant_id = 'default' AND route_code = 'G210';
DELETE FROM road_evaluation_unit WHERE tenant_id = 'default' AND route_code = 'G210';
DELETE FROM road_section WHERE tenant_id = 'default' AND route_code = 'G210';
DELETE FROM road_route WHERE tenant_id = 'default' AND route_code = 'G210';
"
fi

run_sql_file "srmp-admin/src/main/resources/db/schema.sql" "base schema"
run_sql_file "srmp-admin/src/main/resources/db/init_dict.sql" "dictionary data"
run_sql_file "srmp-admin/src/main/resources/db/init_admin.sql" "admin data"
run_sql_file "srmp-admin/src/main/resources/db/phase13_knowledge_outline.sql" "knowledge base schema and seed"
run_sql_file "srmp-admin/src/main/resources/db/phase17_outline_sync.sql" "outline sync schema"
run_sql_file "srmp-admin/src/main/resources/db/phase18_ai_demo_knowledge.sql" "AI demo knowledge"
run_sql_file "srmp-admin/src/main/resources/db/phase20_ai_solution_template.sql" "AI solution template schema"
run_sql_file "srmp-admin/src/main/resources/db/phase21_ai_solution_generate.sql" "AI solution generation schema"
run_sql_file "srmp-admin/src/main/resources/db/phase22_ai_trace_monitor.sql" "AI trace schema"
run_sql_file "srmp-admin/src/main/resources/db/phase33_ai_solution_draft_version.sql" "AI solution draft version schema"
run_sql_file "srmp-admin/src/main/resources/db/phase35_template_effectiveness.sql" "template effectiveness schema"
run_sql_file "srmp-admin/src/main/resources/db/phase35_sample_solution_templates.sql" "sample solution templates"
run_sql_file "srmp-admin/src/main/resources/db/phase36_one_click_demo_seed.sql" "G210 2026 demo business data"

echo "[OK] SRMP database and demo data initialized"
```

- [ ] **Step 3: Verify shell syntax and SQL references**

Run:

```bash
bash -n scripts/srmp-init-demo.sh
grep -n "phase36_one_click_demo_seed.sql" scripts/srmp-init-demo.sh
grep -n "generate_series(0, 999)" srmp-admin/src/main/resources/db/phase36_one_click_demo_seed.sql
grep -n "generate_series(1, 3)" srmp-admin/src/main/resources/db/phase36_one_click_demo_seed.sql
```

Expected result: syntax check passes and all three `grep` commands print matching lines.

- [ ] **Step 4: Commit database initialization work**

Run:

```bash
git add srmp-admin/src/main/resources/db/phase36_one_click_demo_seed.sql scripts/srmp-init-demo.sh
git commit -m "feat: add docker-first demo initialization"
```

---

## Task 4: One-Click Orchestration and Ready Check

**Files:**
- Create: `scripts/srmp-check-ready.sh`
- Create: `scripts/srmp-one-click-start.sh`

- [ ] **Step 1: Add ready-check script**

Create `scripts/srmp-check-ready.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

BACKEND_URL="${BACKEND_URL:-http://localhost:${BACKEND_PORT:-8080}}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:${FRONTEND_PORT:-5173}}"
TENANT_ID="${TENANT_ID:-default}"
YEAR="${YEAR:-2026}"
ROUTE_CODE="${ROUTE_CODE:-G210}"
BACKEND_ONLY=0
FRONTEND_ONLY=0

while [ "$#" -gt 0 ]; do
  case "$1" in
    --backend-only)
      BACKEND_ONLY=1
      shift
      ;;
    --frontend-only)
      FRONTEND_ONLY=1
      shift
      ;;
    --help)
      echo "Usage: $0 [--backend-only] [--frontend-only]"
      exit 0
      ;;
    *)
      echo "[FAIL] unknown argument: $1"
      exit 1
      ;;
  esac
done

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "[FAIL] missing command: $1"
    exit 1
  }
}

retry_url() {
  local label="$1"
  local url="$2"
  local header_name="${3:-}"
  local header_value="${4:-}"
  local i
  for i in $(seq 1 60); do
    if [ -n "$header_name" ]; then
      if curl -fsS "$url" -H "$header_name: $header_value" >/tmp/srmp-ready-response.json 2>/tmp/srmp-ready-error.log; then
        echo "[OK] $label"
        return 0
      fi
    else
      if curl -fsS "$url" >/tmp/srmp-ready-response.txt 2>/tmp/srmp-ready-error.log; then
        echo "[OK] $label"
        return 0
      fi
    fi
    sleep 2
  done
  echo "[FAIL] $label timeout: $url"
  cat /tmp/srmp-ready-error.log || true
  exit 1
}

require_cmd curl

if command -v docker >/dev/null 2>&1; then
  docker ps --format '{{.Names}}' | grep -qx 'srmp-postgres' && echo "[OK] srmp-postgres running"
  docker ps --format '{{.Names}}' | grep -qx 'srmp-redis' && echo "[OK] srmp-redis running"
  docker ps --format '{{.Names}}' | grep -qx 'srmp-minio' && echo "[OK] srmp-minio running"
fi

if [ "$FRONTEND_ONLY" = "0" ]; then
  retry_url "backend GIS route API" "$BACKEND_URL/api/gis/road-routes?routeCode=$ROUTE_CODE" "X-Tenant-Id" "$TENANT_ID"
  retry_url "demo status API" "$BACKEND_URL/api/demo/status?tenantId=$TENANT_ID&year=$YEAR" "X-Tenant-Id" "$TENANT_ID"
  python3 - <<'PY'
import json, sys
payload = json.load(open('/tmp/srmp-ready-response.json'))
data = payload.get('data') or {}
tables = data.get('tables') or {}
required = {
    'road_route': 1,
    'road_section': 4,
    'road_evaluation_unit': 1000,
    'assessment_result': 1000,
    'disease_record': 3000,
}
missing = []
for name, minimum in required.items():
    if int(tables.get(name) or 0) < minimum:
        missing.append(f"{name}={tables.get(name)} < {minimum}")
if missing:
    print('[FAIL] demo data below threshold: ' + '; '.join(missing))
    sys.exit(1)
print('[OK] demo data thresholds satisfied')
PY
fi

if [ "$BACKEND_ONLY" = "0" ]; then
  retry_url "frontend page" "$FRONTEND_URL/"
fi

echo "[OK] SRMP is ready"
```

- [ ] **Step 2: Add one-click startup script**

Create `scripts/srmp-one-click-start.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

RESET_DEMO=0
SKIP_BUILD=0
BACKEND_ONLY=0
FRONTEND_ONLY=0
NO_START=0
CHECK_ONLY=0
LOCAL_DEV=0

while [ "$#" -gt 0 ]; do
  case "$1" in
    --reset-demo)
      RESET_DEMO=1
      shift
      ;;
    --skip-build)
      SKIP_BUILD=1
      shift
      ;;
    --backend-only)
      BACKEND_ONLY=1
      shift
      ;;
    --frontend-only)
      FRONTEND_ONLY=1
      shift
      ;;
    --no-start)
      NO_START=1
      shift
      ;;
    --check-only)
      CHECK_ONLY=1
      shift
      ;;
    --local-dev)
      LOCAL_DEV=1
      shift
      ;;
    --help)
      cat <<'USAGE'
Usage: ./scripts/srmp-one-click-start.sh [options]

Options:
  --reset-demo       Reset generated G210/2026 demo data before seeding.
  --skip-build       Reuse existing backend/frontend Docker images.
  --backend-only     Start dependencies and backend only.
  --frontend-only    Start frontend only; skip database initialization.
  --no-start         Start dependencies and initialize database; do not start app services.
  --check-only       Run readiness checks only.
  --local-dev        Use local Java/Maven/Node processes instead of backend/frontend Docker containers.
  --help             Show this help.
USAGE
      exit 0
      ;;
    *)
      echo "[FAIL] unknown argument: $1"
      exit 1
      ;;
  esac
done

compose() {
  docker compose "$@"
}

wait_postgres() {
  local i
  for i in $(seq 1 60); do
    if docker exec srmp-postgres pg_isready -U "${DB_USER:-srmp}" -d "${DB_NAME:-srmp}" >/dev/null 2>&1; then
      echo "[OK] postgres is ready"
      return 0
    fi
    sleep 2
  done
  echo "[FAIL] postgres did not become ready"
  exit 1
}

stop_pid_file() {
  local pid_file="$1"
  if [ -f "$pid_file" ]; then
    local pid
    pid="$(cat "$pid_file")"
    if [ -n "$pid" ] && kill -0 "$pid" >/dev/null 2>&1; then
      kill "$pid"
      sleep 2
    fi
    rm -f "$pid_file"
  fi
}

start_local_backend() {
  mkdir -p logs
  stop_pid_file logs/srmp-backend.pid
  mvn -pl srmp-admin -am package -DskipTests
  SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-demo}" \
  nohup java \
    -Djava.net.useSystemProxies=false \
    -Dhttp.nonProxyHosts='localhost|127.*|[::1]' \
    -Dhttps.nonProxyHosts='localhost|127.*|[::1]' \
    -jar srmp-admin/target/srmp-admin-1.0.0.jar \
    > logs/srmp-backend.log 2>&1 &
  echo "$!" > logs/srmp-backend.pid
  echo "[OK] local backend started: logs/srmp-backend.log"
}

start_local_frontend() {
  mkdir -p logs
  stop_pid_file logs/srmp-frontend.pid
  npm --prefix srmp-web-ui install
  npm --prefix srmp-web-ui run build
  nohup npm --prefix srmp-web-ui run dev -- --host 0.0.0.0 --port "${FRONTEND_PORT:-5173}" \
    > logs/srmp-frontend.log 2>&1 &
  echo "$!" > logs/srmp-frontend.pid
  echo "[OK] local frontend started: logs/srmp-frontend.log"
}

if [ "$CHECK_ONLY" = "1" ]; then
  ./scripts/srmp-check-ready.sh
  exit 0
fi

if [ "$FRONTEND_ONLY" = "0" ]; then
  echo "==> starting dependency containers"
  compose up -d postgres redis minio
  wait_postgres

  INIT_ARGS=()
  if [ "$RESET_DEMO" = "1" ]; then
    INIT_ARGS+=(--reset-demo)
  fi
  if [ "$LOCAL_DEV" = "1" ]; then
    INIT_ARGS+=(--local-dev)
  fi
  ./scripts/srmp-init-demo.sh "${INIT_ARGS[@]}"
fi

if [ "$NO_START" = "1" ]; then
  echo "[OK] dependencies and data initialization completed"
  exit 0
fi

if [ "$LOCAL_DEV" = "1" ]; then
  if [ "$FRONTEND_ONLY" = "0" ]; then
    start_local_backend
  fi
  if [ "$BACKEND_ONLY" = "0" ]; then
    start_local_frontend
  fi
else
  if [ "$BACKEND_ONLY" = "1" ]; then
    if [ "$SKIP_BUILD" = "1" ]; then
      compose -f docker-compose.yml -f docker-compose.app.yml up -d backend
    else
      compose -f docker-compose.yml -f docker-compose.app.yml up -d --build backend
    fi
  elif [ "$FRONTEND_ONLY" = "1" ]; then
    if [ "$SKIP_BUILD" = "1" ]; then
      compose -f docker-compose.yml -f docker-compose.app.yml up -d frontend
    else
      compose -f docker-compose.yml -f docker-compose.app.yml up -d --build frontend
    fi
  else
    if [ "$SKIP_BUILD" = "1" ]; then
      compose -f docker-compose.yml -f docker-compose.app.yml up -d backend frontend
    else
      compose -f docker-compose.yml -f docker-compose.app.yml up -d --build backend frontend
    fi
  fi
fi

CHECK_ARGS=()
if [ "$BACKEND_ONLY" = "1" ]; then
  CHECK_ARGS+=(--backend-only)
fi
if [ "$FRONTEND_ONLY" = "1" ]; then
  CHECK_ARGS+=(--frontend-only)
fi
./scripts/srmp-check-ready.sh "${CHECK_ARGS[@]}"

cat <<INFO

[OK] SRMP started
Frontend: http://localhost:${FRONTEND_PORT:-5173}
Backend:  http://localhost:${BACKEND_PORT:-8080}
Backend logs:  docker compose -f docker-compose.yml -f docker-compose.app.yml logs -f backend
Frontend logs: docker compose -f docker-compose.yml -f docker-compose.app.yml logs -f frontend
INFO
```

- [ ] **Step 3: Verify script syntax**

Run:

```bash
bash -n scripts/srmp-one-click-start.sh
bash -n scripts/srmp-check-ready.sh
```

Expected result: both syntax checks pass.

- [ ] **Step 4: Commit orchestration scripts**

Run:

```bash
git add scripts/srmp-one-click-start.sh scripts/srmp-check-ready.sh
git commit -m "feat: add one-click docker startup script"
```

---

## Task 5: Compatibility Wrappers and Documentation

**Files:**
- Modify: `scripts/init-db.sh`
- Modify: `scripts/reset-demo.sh`
- Create: `docs/one-click-start-guide.md`
- Modify: `README.md`

- [ ] **Step 1: Make `init-db.sh` delegate to the unified initializer**

Replace `scripts/init-db.sh` with:

```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
exec "$ROOT_DIR/scripts/srmp-init-demo.sh" "$@"
```

- [ ] **Step 2: Make `reset-demo.sh` delegate to reset mode**

Replace `scripts/reset-demo.sh` with:

```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
exec "$ROOT_DIR/scripts/srmp-init-demo.sh" --reset-demo "$@"
```

- [ ] **Step 3: Add the one-click startup guide**

Create `docs/one-click-start-guide.md`:

````markdown
# SRMP One-Click Startup Guide

## Default Docker Startup

Run from the repository root:

```bash
./scripts/srmp-one-click-start.sh
```

The command starts PostGIS, Redis, and MinIO, initializes schemas and sample data, builds `srmp-backend` and `srmp-frontend`, starts both containers, and runs readiness checks.

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
````

- [ ] **Step 4: Update README quick start**

Replace the current `## 快速开始` section in `README.md` with this text:

````markdown
## 快速开始

### 环境要求

- Docker
- Docker Compose

默认启动不要求本机安装 Java、Maven、Node、npm 或 PostgreSQL 客户端。

### 一键启动

```bash
./scripts/srmp-one-click-start.sh
```

脚本会启动 PostGIS、Redis、MinIO，初始化数据库和样例数据，构建并运行后端和前端 Docker 容器。

访问地址：

- 前端：http://localhost:5173
- 后端：http://localhost:8080
- MinIO 控制台：http://localhost:9001

常用命令：

```bash
./scripts/srmp-one-click-start.sh --check-only
./scripts/srmp-one-click-start.sh --reset-demo
./scripts/srmp-one-click-start.sh --skip-build
```

本地 Java/Node 开发模式：

```bash
./scripts/srmp-one-click-start.sh --local-dev
```

详细说明见 `docs/one-click-start-guide.md`。
````

- [ ] **Step 5: Verify docs and wrappers**

Run:

```bash
bash -n scripts/init-db.sh
bash -n scripts/reset-demo.sh
grep -n "srmp-one-click-start.sh" README.md docs/one-click-start-guide.md
grep -n "Docker Compose" README.md docs/one-click-start-guide.md
```

Expected result: syntax checks pass and both `grep` commands print matching lines.

- [ ] **Step 6: Commit compatibility and docs**

Run:

```bash
git add scripts/init-db.sh scripts/reset-demo.sh docs/one-click-start-guide.md README.md
git commit -m "docs: document one-click docker startup"
```

---

## Task 6: End-to-End Verification

**Files:**
- Modify: `scripts/check-one-click-start.sh`

- [ ] **Step 1: Run static verification**

Run:

```bash
bash scripts/check-one-click-start.sh
```

Expected result:

```text
[OK] one-click Docker startup files are wired
```

- [ ] **Step 2: Run Compose configuration verification**

Run:

```bash
docker compose -f docker-compose.yml -f docker-compose.app.yml config >/tmp/srmp-compose-config.yml
grep -n "srmp-backend" /tmp/srmp-compose-config.yml
grep -n "srmp-frontend" /tmp/srmp-compose-config.yml
```

Expected result: both `grep` commands print lines from `/tmp/srmp-compose-config.yml`.

- [ ] **Step 3: Build backend and frontend images**

Run:

```bash
docker compose -f docker-compose.yml -f docker-compose.app.yml build backend frontend
```

Expected result: Docker builds both images without Maven, Java, Node, or npm installed on the host.

- [ ] **Step 4: Run dependency startup and data initialization only**

Run:

```bash
./scripts/srmp-one-click-start.sh --no-start
```

Expected result: PostGIS, Redis, and MinIO are running, and initialization ends with:

```text
[OK] dependencies and data initialization completed
```

- [ ] **Step 5: Run full Docker startup**

Run:

```bash
./scripts/srmp-one-click-start.sh --skip-build
```

Expected result:

```text
[OK] SRMP is ready
[OK] SRMP started
```

- [ ] **Step 6: Verify API and frontend manually with curl**

Run:

```bash
curl -fsS "http://localhost:8080/api/demo/status?tenantId=default&year=2026" -H "X-Tenant-Id: default" | python3 -m json.tool | head -80
curl -fsS "http://localhost:8080/api/gis/road-routes?routeCode=G210" -H "X-Tenant-Id: default" | python3 -m json.tool | head -80
curl -fsS "http://localhost:5173/" | head -20
```

Expected result: demo status shows at least 1 route, 4 sections, 1000 evaluation units, 1000 assessments, and 3000 disease records; GIS routes returns success; frontend returns the SPA HTML.

- [ ] **Step 7: Run template and region smoke checks**

Run:

```bash
bash scripts/check-phase35-template-effectiveness.sh
RUN_BUILD=0 RUN_API=1 bash scripts/phase31-acceptance.sh
```

Expected result: template hooks are present, demo data checks pass, GIS API checks pass, and AI map object checks pass.

- [ ] **Step 8: Commit verification adjustment if the script changed**

Run only if `scripts/check-one-click-start.sh` was changed during verification:

```bash
git add scripts/check-one-click-start.sh
git commit -m "test: finalize one-click startup checks"
```

- [ ] **Step 9: Review final diff**

Run:

```bash
git status --short
git log --oneline -8
```

Expected result: only unrelated pre-existing local files remain unstaged, and the one-click startup commits are visible in the recent log.

---

## Implementation Notes

- Do not stage `.claude/settings.local.json`, `srmp-admin/src/main/resources/application-demo.yml`, `srmp-admin/src/main/resources/application-dev.yml`, or `docs/phase35-template-ai-generation-principle.md` unless the user explicitly asks.
- Default Docker startup must work without host Java, Maven, Node, npm, or `psql`.
- `demo_data.sql` is intentionally not part of default initialization because it deletes G210 rows and only creates a tiny sample. Use `phase36_one_click_demo_seed.sql` for the one-click path.
- `--reset-demo` deletes only generated G210/2026 demo data and then runs the deterministic seed again.
- The frontend Docker build should keep `VITE_API_BASE_URL` empty so browser requests use same-origin `/api` through nginx.
- The backend container must use `SPRING_PROFILES_ACTIVE=demo` and Compose service hostnames: `postgres`, `redis`, `minio`.

## Spec Coverage Self-Review

- Docker backend/frontend default: Task 2 and Task 4.
- Database initialization and Docker `psql`: Task 3.
- Idempotent business data and template configuration: Task 3.
- Safe reset mode: Task 3 and Task 5.
- Ready check: Task 4 and Task 6.
- Documentation: Task 5.
- No local Java/Maven/Node requirement in default mode: Task 2, Task 4, Task 5, and Task 6.
