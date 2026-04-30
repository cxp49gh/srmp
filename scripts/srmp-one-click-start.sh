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

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "[FAIL] missing command: $1"
    exit 1
  }
}

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
  require_cmd mvn
  require_cmd java
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
  require_cmd npm
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

require_cmd docker

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
  if [ "${#INIT_ARGS[@]}" -gt 0 ]; then
    ./scripts/srmp-init-demo.sh "${INIT_ARGS[@]}"
  else
    ./scripts/srmp-init-demo.sh
  fi
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
if [ "${#CHECK_ARGS[@]}" -gt 0 ]; then
  ./scripts/srmp-check-ready.sh "${CHECK_ARGS[@]}"
else
  ./scripts/srmp-check-ready.sh
fi

cat <<INFO

[OK] SRMP started
Frontend: http://localhost:${FRONTEND_PORT:-5173}
Backend:  http://localhost:${BACKEND_PORT:-8080}
Backend logs:  docker compose -f docker-compose.yml -f docker-compose.app.yml logs -f backend
Frontend logs: docker compose -f docker-compose.yml -f docker-compose.app.yml logs -f frontend
INFO
