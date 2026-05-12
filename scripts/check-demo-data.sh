#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TENANT_ID="${TENANT_ID:-default}"
YEAR="${YEAR:-2026}"

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "[FAIL] 缺少命令：$1"
    exit 1
  }
}

require_cmd curl
require_cmd python3

echo "==> 检查演示数据状态：$BASE_URL tenant=$TENANT_ID year=$YEAR"

RESP="$(curl -s "$BASE_URL/api/demo/status?tenantId=$TENANT_ID&year=$YEAR" -H "X-Tenant-Id: $TENANT_ID")"

python3 - "$RESP" <<'PY'
import json, sys
payload = json.loads(sys.argv[1])
if payload.get("code") != 0:
    print("[FAIL] /api/demo/status code != 0")
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    sys.exit(1)

data = payload.get("data") or {}
tables = data.get("tables") or {}
health = data.get("health") or {}

print(json.dumps({"tables": tables, "health": health}, ensure_ascii=False, indent=2))

required = {
    "road_route": 0,
    "road_section_line": 0,
    "road_section_ledger": 0,
    "road_section_km": 0,
    "road_section_hm": 0,
    "assessment_result": 0,
    "disease_record": 0,
}
failed = []
for k, min_count in required.items():
    if int(tables.get(k) or 0) < min_count:
        failed.append(f"{k}={tables.get(k)} < {min_count}")

if failed:
    print("[FAIL] 演示数据未达标：" + "; ".join(failed))
    sys.exit(1)

print("[OK] 演示数据已达标")
PY