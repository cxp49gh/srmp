#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

test -f docker-compose.yml
test -f docker-compose.app.yml
test -f docker-compose.langgraph.yml
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
grep -R "srmp-ai-orchestrator" -n docker-compose.langgraph.yml scripts/srmp-one-click-start.sh docs/one-click-start-guide.md README.md >/dev/null
grep -R "SRMP_AI_ORCHESTRATOR_PROVIDER" -n docker-compose.app.yml scripts/srmp-one-click-start.sh >/dev/null
grep -R "SRMP_LANGGRAPH_URL" -n docker-compose.app.yml scripts/srmp-one-click-start.sh >/dev/null
grep -R "srmp-backend" -n docker-compose.app.yml deploy/docker/backend/Dockerfile >/dev/null
grep -R "srmp-frontend" -n docker-compose.app.yml deploy/docker/frontend >/dev/null
grep -R "proxy_pass http://backend:8080/api/" -n deploy/docker/frontend/nginx.conf >/dev/null
grep -R "phase36_one_click_demo_seed.sql" -n scripts/srmp-init-demo.sh >/dev/null
grep -R "docker exec -i srmp-postgres psql" -n scripts/srmp-init-demo.sh >/dev/null
grep -R "docker compose -f docker-compose.yml -f docker-compose.app.yml -f docker-compose.langgraph.yml" -n scripts/srmp-one-click-start.sh docs/one-click-start-guide.md >/dev/null
grep -R "/agent/langgraph-ops" -n docs/one-click-start-guide.md README.md >/dev/null
grep -R "1000" -n srmp-admin/src/main/resources/db/phase36_one_click_demo_seed.sql scripts/srmp-check-ready.sh >/dev/null
grep -R "3000" -n srmp-admin/src/main/resources/db/phase36_one_click_demo_seed.sql scripts/srmp-check-ready.sh >/dev/null
grep -R "ai_solution_template" -n scripts/srmp-check-ready.sh >/dev/null
grep -R "ai_trace_log" -n scripts/srmp-check-ready.sh >/dev/null

if command -v docker >/dev/null 2>&1; then
  docker compose -f docker-compose.yml -f docker-compose.app.yml -f docker-compose.langgraph.yml config >/tmp/srmp-compose-config.yml
  grep -n "srmp-backend" /tmp/srmp-compose-config.yml >/dev/null
  grep -n "srmp-frontend" /tmp/srmp-compose-config.yml >/dev/null
  grep -n "srmp-ai-orchestrator" /tmp/srmp-compose-config.yml >/dev/null
fi

echo "[OK] one-click Docker startup files are wired"
