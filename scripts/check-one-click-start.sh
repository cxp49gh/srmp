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
test -f srmp-admin/src/main/resources/db/srmp_full_init.sql
test -f scripts/srmp-check-ready.sh
test -f docs/one-click-start-guide.md

bash -n scripts/srmp-one-click-start.sh
bash -n scripts/srmp-init-demo.sh
bash -n scripts/srmp-check-ready.sh

grep -R "backend:" -n docker-compose.app.yml >/dev/null
grep -R "frontend:" -n docker-compose.app.yml >/dev/null
grep -R "srmp-backend" -n docker-compose.app.yml deploy/docker/backend/Dockerfile >/dev/null
grep -R "srmp-frontend" -n docker-compose.app.yml deploy/docker/frontend >/dev/null
grep -R "proxy_pass http://backend:8080/api/" -n deploy/docker/frontend/nginx.conf >/dev/null
grep -R "phase36_one_click_demo_seed" -n srmp-admin/src/main/resources/db/srmp_full_init.sql >/dev/null
grep -R "docker exec -i srmp-postgres psql" -n scripts/srmp-init-demo.sh >/dev/null
grep -R "docker compose -f docker-compose.yml -f docker-compose.app.yml" -n scripts/srmp-one-click-start.sh docs/one-click-start-guide.md README.md >/dev/null
grep -R "'road_route'" -n scripts/srmp-check-ready.sh >/dev/null
grep -R "'disease_record'" -n scripts/srmp-check-ready.sh >/dev/null
grep -R "road_section_line" -n srmp-admin/src/main/resources/db/srmp_full_init.sql >/dev/null

if command -v docker >/dev/null 2>&1; then
  docker compose -f docker-compose.yml -f docker-compose.app.yml config >/tmp/srmp-compose-config.yml
  grep -n "srmp-backend" /tmp/srmp-compose-config.yml >/dev/null
  grep -n "srmp-frontend" /tmp/srmp-compose-config.yml >/dev/null
fi

echo "[OK] one-click Docker startup files are wired"
