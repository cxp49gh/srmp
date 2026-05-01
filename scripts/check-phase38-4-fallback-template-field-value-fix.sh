#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TASK_ID="${TASK_ID:-}"

if [ -z "$TASK_ID" ]; then
  echo "[FAIL] 请传入 TASK_ID，例如：TASK_ID=<方案任务ID> bash scripts/check-phase38-4-fallback-template-field-value-fix.sh"
  exit 1
fi

echo "==> Markdown V2 导出检查：$TASK_ID"
MD="$(curl -fsS "$BASE_URL/api/ai/solution/tasks/$TASK_ID/export/markdown-v2")"
echo "$MD" | sed -n '1,160p'

if echo "$MD" | grep -q "AI 方案草稿（系统兜底模板）"; then
  echo "$MD" | grep -q "| 路线编号 | - |" && { echo "[FAIL] 兜底模板路线编号仍为空"; exit 1; }
  echo "$MD" | grep -q "| 年度 | - |" && { echo "[FAIL] 兜底模板年度仍为空"; exit 1; }
  echo "$MD" | grep -q "| 对象类型 | - |" && { echo "[FAIL] 兜底模板对象类型仍为空"; exit 1; }
  echo "$MD" | grep -q "| 方案类型 | - |" && { echo "[FAIL] 兜底模板方案类型仍为空"; exit 1; }
  echo "[OK] 兜底模板基础字段已修复"
else
  echo "[OK] 当前任务不是兜底模板，导出正常"
fi

echo "==> 引用来源重复检查"
SOURCES="$(curl -fsS "$BASE_URL/api/ai/solution/tasks/$TASK_ID/sources")"
python3 - "$SOURCES" <<'PY'
import json, sys
payload = json.loads(sys.argv[1])
items = payload.get("data") or payload
fallback = []
for it in items:
    title = it.get("sourceTitle") or it.get("source_title") or it.get("title") or ""
    stype = it.get("sourceType") or it.get("source_type") or ""
    if "兜底模板" in title or stype == "SYSTEM_TEMPLATE":
        fallback.append(it)
if len(fallback) > 1:
    raise SystemExit(f"[FAIL] 系统兜底模板引用来源重复：{len(fallback)}")
print(f"[OK] 系统兜底模板引用来源数量：{len(fallback)}")
PY
