#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TASK_ID="${TASK_ID:-}"

if [ -z "$TASK_ID" ]; then
  echo "[FAIL] 请传入 TASK_ID，例如：TASK_ID=<方案任务ID> bash scripts/check-phase38-3-template-fallback-source-dedupe.sh"
  exit 1
fi

echo "==> 检查方案任务详情：$TASK_ID"
DETAIL="$(curl -fsS "$BASE_URL/api/ai/solution/tasks/$TASK_ID")"
echo "$DETAIL" | python3 -m json.tool | sed -n '1,180p'

python3 - "$DETAIL" <<'PY'
import json, sys
payload = json.loads(sys.argv[1])
data = payload.get("data") or payload
content = data.get("resultContent") or data.get("result_content") or data.get("markdown") or ""
if not str(content).strip():
    raise SystemExit("[FAIL] resultContent 为空，模板兜底仍未生效")
if "AI 方案草稿" in content or "系统兜底模板" in content:
    print("[OK] resultContent 非空，且包含系统兜底模板内容")
else:
    print("[OK] resultContent 非空")
PY

echo "==> 检查引用来源重复"
SOURCES="$(curl -fsS "$BASE_URL/api/ai/solution/tasks/$TASK_ID/sources")"
echo "$SOURCES" | python3 -m json.tool | sed -n '1,220p'

python3 - "$SOURCES" <<'PY'
import json, sys
payload = json.loads(sys.argv[1])
items = payload.get("data") or payload
fallback = []
for it in items:
    title = it.get("sourceTitle") or it.get("source_title") or it.get("title") or ""
    stype = it.get("sourceType") or it.get("source_type") or ""
    if "兜底模板" in title or "系统兜底" in title or stype == "SYSTEM_TEMPLATE":
        fallback.append(it)
if len(fallback) > 1:
    raise SystemExit(f"[FAIL] 系统兜底模板引用来源重复：{len(fallback)} 条")
print(f"[OK] 系统兜底模板引用来源数量：{len(fallback)}")
PY
