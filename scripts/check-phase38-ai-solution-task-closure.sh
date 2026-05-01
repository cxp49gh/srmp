#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TASK_ID="${TASK_ID:-}"

if [ -z "$TASK_ID" ]; then
  echo "[INFO] TASK_ID 未指定，先取最新任务"
  TASK_ID="$(curl -fsS -X POST "$BASE_URL/api/ai/solution/tasks/list"     -H "Content-Type: application/json"     -d '{"routeCode":"G210","year":2026,"limit":1}' | python3 -c 'import json,sys; p=json.load(sys.stdin); d=p.get("data") or p; print((d[0] if d else {}).get("id",""))')"
fi

if [ -z "$TASK_ID" ]; then
  echo "[FAIL] 未找到方案任务，请先从一张图 AI 生成并保存方案草稿"
  exit 1
fi

echo "==> TASK_ID=$TASK_ID"

echo "==> 更新 AI 上下文"
curl -fsS -X POST "$BASE_URL/api/ai/solution/tasks/$TASK_ID/ai-context"   -H "Content-Type: application/json"   -d '{
    "aiTraceId": "phase38-check-trace",
    "aiAnswer": "这是 Phase38 验收写入的 AI 分析摘要。",
    "aiSources": [{"title":"验收知识来源","score":0.99}],
    "aiToolResults": [{"toolName":"knowledge.retrieve","summary":"验收工具调用"}],
    "aiEvidence": {"retrievalStrategy":"HYBRID","vectorUsed":true},
    "generationMode": "CHECK_PHASE38"
  }' | python3 -m json.tool

echo "==> 读取 AI 上下文"
CTX="$(curl -fsS "$BASE_URL/api/ai/solution/tasks/$TASK_ID/ai-context")"
echo "$CTX" | python3 -m json.tool
python3 - "$CTX" <<'PY'
import json,sys
p=json.loads(sys.argv[1])
d=p.get("data") or p
if d.get("generationMode") != "CHECK_PHASE38":
    raise SystemExit("[FAIL] AI 上下文未保存成功")
if "Phase38 验收" not in (d.get("aiAnswer") or ""):
    raise SystemExit("[FAIL] aiAnswer 未保存成功")
print("[OK] AI 上下文保存成功")
PY

echo "==> 状态时间线"
curl -fsS "$BASE_URL/api/ai/solution/tasks/$TASK_ID/status-timeline" | python3 -m json.tool

echo "==> Markdown V2 导出检查"
MD="$(curl -fsS "$BASE_URL/api/ai/solution/tasks/$TASK_ID/export/markdown-v2")"
echo "$MD" | sed -n '1,120p'
echo "$MD" | grep -q "AI 分析摘要" || { echo "[FAIL] 导出缺少 AI 分析摘要"; exit 1; }
echo "$MD" | grep -q "AI 依据摘要" || { echo "[FAIL] 导出缺少 AI 依据摘要"; exit 1; }

echo "[OK] Phase38 AI 方案任务闭环验收通过"
