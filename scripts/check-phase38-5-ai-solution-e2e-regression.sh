#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TASK_ID="${TASK_ID:-}"

echo "==> 1. LLM Health"
LLM_HEALTH="$(curl -fsS "$BASE_URL/api/ai/llm/health?probe=true")"
echo "$LLM_HEALTH" | python3 -m json.tool

python3 - "$LLM_HEALTH" <<'PY'
import json, sys
p=json.loads(sys.argv[1])
d=p.get("data") or p
if d.get("enabled") is True and d.get("available") is not True:
    raise SystemExit("[FAIL] LLM enabled=true 但 health probe 不可用：" + str(d))
print("[OK] LLM health checked")
PY

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

run_case() {
  local case_id="$1"
  local payload="$2"
  local expect_title="$3"
  shift 3
  local expected_words=("$@")

  echo "==> MapAgent 回归：${case_id}"
  local resp_file="$tmp_dir/${case_id}.json"
  curl -fsS -X POST "$BASE_URL/api/agent/map-agent/run" \
    -H "Content-Type: application/json" \
    -d "$payload" > "$resp_file"

  python3 - "$case_id" "$resp_file" "$expect_title" "${expected_words[@]}" <<'PY'
import json, sys
case_id=sys.argv[1]
path=sys.argv[2]
expect_title=sys.argv[3]
expected_words=sys.argv[4:]
payload=json.load(open(path, encoding="utf-8"))
data=payload.get("data") or payload
answer=data.get("answer") or payload.get("answer") or ""
tools=data.get("toolResults") or payload.get("toolResults") or []
sources=data.get("sources") or payload.get("sources") or []
trace=data.get("trace") or payload.get("trace") or {}

if expect_title not in answer:
    raise SystemExit(f"[FAIL] {case_id}: 缺少专项标题 {expect_title}")

for word in expected_words:
    if word and word not in answer:
        raise SystemExit(f"[FAIL] {case_id}: 缺少关键词 {word}")

tool_names=[t.get("toolName") or t.get("name") for t in tools if isinstance(t, dict)]
if "knowledge.retrieve" not in tool_names:
    raise SystemExit(f"[FAIL] {case_id}: 未调用 knowledge.retrieve")
if not sources:
    raise SystemExit(f"[FAIL] {case_id}: sources 为空")
if "solution.generateDraft" in tool_names:
    raise SystemExit(f"[FAIL] {case_id}: 普通分析不应自动调用 solution.generateDraft")
if "####" in answer:
    raise SystemExit(f"[FAIL] {case_id}: answer 仍包含 #### 标题")

steps=trace.get("steps") or []
llm_steps=[s for s in steps if s.get("name")=="llm_answer"]
if llm_steps:
    step=llm_steps[0]
    status=step.get("status")
    d=step.get("data") or {}
    if status == "SKIPPED" and d.get("enabled") is True:
        raise SystemExit(f"[FAIL] {case_id}: LLM enabled=true 不应显示 SKIPPED")
    if status == "FAILED" and not (d.get("errorMessage") or d.get("rawResponsePreview") or d.get("firstAttempt")):
        raise SystemExit(f"[FAIL] {case_id}: LLM FAILED 但缺少诊断")
print(f"[OK] {case_id}")
PY
}

run_case "disease-pothole" '{
  "message":"分析当前地图选中对象，说明主要问题、成因判断，并给出养护处置建议",
  "mapContext":{"mode":"OBJECT","routeCode":"G210","year":2026,"mapObject":{"objectType":"DISEASE","routeCode":"G210","year":2026,"startStake":57.523,"endStake":57.527,"diseaseName":"坑槽","diseaseType":"POTHOLE","severity":"MEDIUM","quantity":1.32,"measureUnit":"m2"}},
  "options":{"useKnowledge":true,"useBusinessData":true,"useTools":true,"topK":5}
}' "当前对象专项处置建议" "坑槽" "处置建议"

run_case "disease-subsidence" '{
  "message":"分析当前地图选中对象，说明主要问题、成因判断，并给出养护处置建议",
  "mapContext":{"mode":"OBJECT","routeCode":"G210","year":2026,"mapObject":{"objectType":"DISEASE","routeCode":"G210","year":2026,"startStake":78.132,"endStake":78.134,"diseaseName":"沉陷","diseaseType":"SUBSIDENCE","severity":"HEAVY","quantity":5.74,"measureUnit":"m2"}},
  "options":{"useKnowledge":true,"useBusinessData":true,"useTools":true,"topK":5}
}' "当前对象专项处置建议" "沉陷" "基层"

run_case "assessment-medium" '{
  "message":"分析当前地图选中对象，说明主要问题、成因判断，并给出养护处置建议",
  "mapContext":{"mode":"OBJECT","routeCode":"G210","year":2026,"mapObject":{"objectType":"ASSESSMENT_RESULT","routeCode":"G210","year":2026,"startStake":76.534,"endStake":77.334,"mqi":74.729,"pqi":70.059,"pci":70.525,"rqi":78.012,"rdi":93.233,"grade":"MEDIUM"}},
  "options":{"useKnowledge":true,"useBusinessData":true,"useTools":true,"topK":5}
}' "当前评定单元专项分析" "MQI" "PCI"

run_case "road-section" '{
  "message":"分析当前地图选中对象，说明主要问题、成因判断，并给出养护处置建议",
  "mapContext":{"mode":"OBJECT","routeCode":"G210","year":2026,"mapObject":{"objectType":"ROAD_SECTION","routeCode":"G210","sectionName":"G210 示例路段","startStake":70.000,"endStake":80.000,"lengthKm":10.0,"pavementType":"沥青混凝土","laneCount":2}},
  "options":{"useKnowledge":true,"useBusinessData":true,"useTools":true,"topK":5}
}' "当前路段专项分析" "路段" "养护处置建议"

run_case "road-route" '{
  "message":"分析当前地图选中对象，说明主要问题、成因判断，并给出养护处置建议",
  "mapContext":{"mode":"OBJECT","routeCode":"G210","year":2026,"mapObject":{"objectType":"ROAD_ROUTE","routeCode":"G210","routeName":"G210 测试路线","adminGrade":"国道","technicalGrade":"二级公路","startStake":0.000,"endStake":100.000,"lengthKm":100.0}},
  "options":{"useKnowledge":true,"useBusinessData":true,"useTools":true,"topK":5}
}' "当前路线专项分析" "路线级" "低分单元"

echo "[OK] MapAgent 对象回归通过"

echo "==> 方案任务闭环检查"
if [ -z "$TASK_ID" ]; then
  TASK_ID="$(curl -fsS -X POST "$BASE_URL/api/ai/solution/tasks/list" \
    -H "Content-Type: application/json" \
    -d '{"routeCode":"G210","year":2026,"limit":1}' \
    | python3 -c 'import json,sys; p=json.load(sys.stdin); d=p.get("data") or p; print((d[0] if d else {}).get("id",""))')"
fi

if [ -z "$TASK_ID" ]; then
  echo "[FAIL] 未找到方案任务。请先在 /gis/one-map 中保存方案任务，或传入 TASK_ID。"
  exit 1
fi

echo "[INFO] TASK_ID=$TASK_ID"

curl -fsS -X POST "$BASE_URL/api/ai/solution/tasks/$TASK_ID/ai-context" \
  -H "Content-Type: application/json" \
  -d '{
    "aiTraceId": "phase38-5-e2e",
    "aiAnswer": "Phase38.5 全链路回归写入的 AI 分析摘要。",
    "aiSources": [{"title":"Phase38.5 回归来源","score":0.99}],
    "aiToolResults": [{"toolName":"knowledge.retrieve","summary":"Phase38.5 回归工具调用"}],
    "aiEvidence": {"retrievalStrategy":"HYBRID","vectorUsed":true},
    "generationMode": "PHASE38_5_E2E"
  }' > "$tmp_dir/ai-context-update.json"

CTX="$(curl -fsS "$BASE_URL/api/ai/solution/tasks/$TASK_ID/ai-context")"
echo "$CTX" | python3 -m json.tool | sed -n '1,160p'
python3 - "$CTX" <<'PY'
import json,sys
p=json.loads(sys.argv[1])
d=p.get("data") or p
if d.get("generationMode") != "PHASE38_5_E2E":
    raise SystemExit("[FAIL] ai-context 未保存成功")
if "Phase38.5" not in (d.get("aiAnswer") or ""):
    raise SystemExit("[FAIL] aiAnswer 未保存成功")
print("[OK] ai-context 保存与读取通过")
PY

curl -fsS "$BASE_URL/api/ai/solution/tasks/$TASK_ID/status-timeline" | python3 -m json.tool | sed -n '1,120p'

MD="$(curl -fsS "$BASE_URL/api/ai/solution/tasks/$TASK_ID/export/markdown-v2")"
echo "$MD" | sed -n '1,120p'
echo "$MD" | grep -q "AI 分析摘要" || { echo "[FAIL] 导出缺少 AI 分析摘要"; exit 1; }
echo "$MD" | grep -q "AI 依据摘要" || { echo "[FAIL] 导出缺少 AI 依据摘要"; exit 1; }

if echo "$MD" | grep -q "AI 方案草稿（系统兜底模板）"; then
  echo "$MD" | grep -q "| 路线编号 | - |" && { echo "[FAIL] 兜底模板路线编号为空"; exit 1; }
  echo "$MD" | grep -q "| 年度 | - |" && { echo "[FAIL] 兜底模板年度为空"; exit 1; }
  echo "$MD" | grep -q "| 对象类型 | - |" && { echo "[FAIL] 兜底模板对象类型为空"; exit 1; }
fi

SOURCES="$(curl -fsS "$BASE_URL/api/ai/solution/tasks/$TASK_ID/sources")"
python3 - "$SOURCES" <<'PY'
import json,sys
p=json.loads(sys.argv[1])
items=p.get("data") or p
fallback=[]
for it in items:
    title=it.get("sourceTitle") or it.get("source_title") or it.get("title") or ""
    stype=it.get("sourceType") or it.get("source_type") or ""
    if "兜底模板" in title or stype=="SYSTEM_TEMPLATE":
        fallback.append(it)
if len(fallback)>1:
    raise SystemExit(f"[FAIL] 系统兜底模板引用来源重复：{len(fallback)}")
print(f"[OK] 系统兜底模板引用来源数量：{len(fallback)}")
PY

echo "[OK] Phase38.5 AI 方案闭环全链路回归通过"
