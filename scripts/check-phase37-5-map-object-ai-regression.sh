#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

pass_count=0
total_count=0

run_case() {
  local case_id="$1"
  local payload="$2"
  local expect_title="$3"
  shift 3
  local expected_words=("$@")

  total_count=$((total_count + 1))
  echo "==> [${case_id}]"

  local resp_file="$tmp_dir/${case_id}.json"
  curl -fsS -X POST "$BASE_URL/api/agent/map-agent/run" \
    -H "Content-Type: application/json" \
    -d "$payload" > "$resp_file"

  python3 - "$case_id" "$resp_file" "$expect_title" "${expected_words[@]}" <<'PY'
import json, sys
case_id = sys.argv[1]
path = sys.argv[2]
expect_title = sys.argv[3]
expected_words = sys.argv[4:]

with open(path, encoding="utf-8") as f:
    payload = json.load(f)
data = payload.get("data") or payload
answer = data.get("answer") or payload.get("answer") or ""
tools = data.get("toolResults") or payload.get("toolResults") or []
sources = data.get("sources") or payload.get("sources") or []

if expect_title not in answer:
    raise SystemExit(f"[FAIL] {case_id}: answer 缺少专项标题：{expect_title}")

for w in expected_words:
    if w and w not in answer:
        raise SystemExit(f"[FAIL] {case_id}: answer 缺少关键词：{w}")

if "knowledge.retrieve" not in [(t.get("toolName") or t.get("name")) for t in tools if isinstance(t, dict)]:
    raise SystemExit(f"[FAIL] {case_id}: 未调用 knowledge.retrieve")

if not sources:
    raise SystemExit(f"[FAIL] {case_id}: sources 为空")

if "solution.generateDraft" in [(t.get("toolName") or t.get("name")) for t in tools if isinstance(t, dict)]:
    raise SystemExit(f"[FAIL] {case_id}: 普通分析不应自动调用 solution.generateDraft")

if "当前对象属于裂缝类病害" in answer and "裂缝" not in answer:
    raise SystemExit(f"[FAIL] {case_id}: 可能误判裂缝场景")

if "####" in answer:
    raise SystemExit(f"[FAIL] {case_id}: answer 仍包含 #### 标题")

print(f"[OK] {case_id}")
PY

  pass_count=$((pass_count + 1))
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

echo "[OK] Phase37.5 一张图对象 AI 回归通过：${pass_count}/${total_count}"
