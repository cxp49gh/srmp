#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PYTHON_BIN="${PYTHON_BIN:-$ROOT/srmp-ai-orchestrator/.venv/bin/python}"
if [ ! -x "$PYTHON_BIN" ]; then
  PYTHON_BIN="$(command -v python3 || true)"
fi
if [ -z "$PYTHON_BIN" ]; then
  echo "[FAIL] python not found; run scripts/run-langgraph-orchestrator-dev.sh once to create srmp-ai-orchestrator/.venv" >&2
  exit 1
fi

PYTHONPATH="$ROOT/srmp-ai-orchestrator" "$PYTHON_BIN" - <<'PY'
import asyncio
import warnings

warnings.filterwarnings("ignore", message="urllib3 v2 only supports OpenSSL")

from app.intent import recognize_intent
from app.planner import plan_tools
from app.answer_enhancers import enhance_answer
from app.schemas import MapAiAgentRequest, MapAiContext, ToolResult
from app.workflow import LangGraphWorkflow


def request(message, mode=None, obj=None, region=None, route="G210", year=2026, options=None):
    return MapAiAgentRequest(
        message=message,
        mapContext=MapAiContext(
            tenantId="default",
            mode=mode,
            routeCode=route,
            year=year,
            mapObject=obj,
            regionSummary=region,
            selectedLayers=["ROAD_SECTION", "DISEASE", "ASSESSMENT_RESULT"],
            userQuestion=message,
        ),
        mapObject=obj,
        options=options or {"topK": 3, "useKnowledge": True},
    )


DISEASE_OBJ = {
    "objectType": "DISEASE",
    "objectId": "demo-disease-1",
    "routeCode": "G210",
    "year": 2026,
    "diseaseName": "坑槽",
    "severity": "HEAVY",
    "startStake": 100.0,
    "endStake": 100.2,
    "quantity": 12.5,
    "measureUnit": "m2",
}

ASSESSMENT_OBJ = {
    "objectType": "ASSESSMENT_RESULT",
    "objectId": "demo-assessment-1",
    "routeCode": "G210",
    "year": 2026,
    "startStake": 97.4,
    "endStake": 98.2,
    "mqi": 72.5,
    "pqi": 70.2,
    "pci": 68.9,
    "rqi": 82.0,
    "rdi": 88.0,
    "grade": "中",
}

ROUTE_OBJ = {"objectType": "ROAD_ROUTE", "objectId": "route-g210", "routeCode": "G210", "year": 2026}
SECTION_OBJ = {"objectType": "ROAD_SECTION", "objectId": "section-1", "routeCode": "G210", "startStake": 95.0, "endStake": 101.0}
REGION_SUMMARY = {"routeCount": 1, "sectionCount": 4, "unitCount": 23, "diseaseSummary": {"disease_count": 59, "heavy_count": 12}, "assessmentSummary": {"avg_mqi": 79.6}}

CASES = [
    ("ordinary", request("你好，帮我看看当前地图可以怎么分析"), "GENERAL_CHAT", ["knowledge.retrieve"], []),
    ("knowledge", request("裂缝处置规范和现场复核依据是什么"), "KNOWLEDGE_QA", ["knowledge.retrieve"], []),
    ("disease", request("分析当前选中病害并给出处置建议", "OBJECT", DISEASE_OBJ), "OBJECT_ANALYSIS", ["gis.queryNearbyObjects", "knowledge.retrieve"], ["当前对象专项处置建议", "坑槽"]),
    ("assessment", request("分析当前评定结果低分原因", "OBJECT", ASSESSMENT_OBJ), "OBJECT_ANALYSIS", ["gis.queryAssessmentResults", "gis.queryDiseasesByStakeRange", "knowledge.retrieve"], ["当前评定单元专项分析", "MQI", "PCI"]),
    ("route", request("分析当前路线整体路况", "OBJECT", ROUTE_OBJ), "OBJECT_ANALYSIS", ["gis.queryAssessmentResults", "gis.queryDiseases", "knowledge.retrieve"], ["当前路线专项分析"]),
    ("section", request("分析当前路段养护重点", "OBJECT", SECTION_OBJ), "OBJECT_ANALYSIS", ["gis.queryAssessmentResults", "gis.queryDiseases", "gis.queryDiseasesByStakeRange", "knowledge.retrieve"], ["当前路段专项分析"]),
    ("nearby", request("查看附近相邻病害和评定结果", "OBJECT", DISEASE_OBJ), "NEARBY_ANALYSIS", ["gis.queryNearbyObjects", "knowledge.retrieve"], []),
    ("region", request("分析框选区域养护风险", "REGION", None, REGION_SUMMARY), "REGION_ANALYSIS", ["gis.queryRegionSummary", "knowledge.retrieve"], ["当前框选区域专项分析"]),
    ("template", request("当前方案模板是否生效，变量有没有缺失", "ROUTE", ROUTE_OBJ), "TEMPLATE_VERIFY", ["template.match", "knowledge.retrieve"], []),
    ("solution", request("基于当前对象生成方案草稿", "OBJECT", DISEASE_OBJ), "SOLUTION_GENERATE", ["gis.queryNearbyObjects", "solution.generateDraft", "knowledge.retrieve"], ["当前对象专项处置建议"]),
]


def tool_names(calls):
    return [item.toolName for item in calls]


for name, req, expected_intent, expected_tools, required_answer_parts in CASES:
    intent, detail = recognize_intent(req)
    if intent != expected_intent:
        raise SystemExit(f"[FAIL] {name}: expected intent {expected_intent}, got {intent} detail={detail}")
    planned = plan_tools(req, intent, detail)
    names = tool_names(planned)
    for tool in expected_tools:
        if tool not in names:
            raise SystemExit(f"[FAIL] {name}: missing planned tool {tool}, got {names}")
    answer = enhance_answer(
        "基础分析",
        req,
        intent,
        detail,
        [
            ToolResult(toolName="gis.queryDiseases", success=True, summary="查询到 2 条病害记录", data={"summary": {"diseaseTypes": {"坑槽": 2}, "severities": {"HEAVY": 1}}}, count=2),
            ToolResult(toolName="gis.queryAssessmentResults", success=True, summary="查询到 1 条评定结果", data={"items": [ASSESSMENT_OBJ]}, count=1),
            ToolResult(toolName="gis.queryDiseasesByStakeRange", success=True, summary="查询到评定单元内病害 2 条", data={"summary": {"diseaseTypes": {"裂缝": 1, "坑槽": 1}, "severities": {"HEAVY": 1}}}, count=2),
        ],
        [{"title": "公路养护规范", "sectionTitle": "裂缝处置", "content": "灌缝、封缝、封层、雨水下渗防治"}],
    )
    for part in required_answer_parts:
        if part not in answer:
            raise SystemExit(f"[FAIL] {name}: enhanced answer missing {part}: {answer[:300]}")


class FakeGateway:
    async def execute_tool(self, call, request, tenant_id, trace_id):
        if call.toolName == "knowledge.retrieve":
            data = {"hits": [{"chunkId": "chunk-1", "documentId": "doc-1", "title": "养护规范", "sectionTitle": "处置", "sourceType": "KNOWLEDGE", "sourceId": "kg-1", "content": "裂缝可灌缝封缝，坑槽应切割清理压实。", "score": 0.9}]}
        elif call.toolName == "gis.queryNearbyObjects":
            data = {"diseases": [DISEASE_OBJ], "assessments": [ASSESSMENT_OBJ]}
        elif call.toolName == "gis.queryAssessmentResults":
            data = {"items": [ASSESSMENT_OBJ], "count": 1}
        elif call.toolName == "gis.queryDiseasesByStakeRange":
            data = {"items": [DISEASE_OBJ], "summary": {"diseaseTypes": {"坑槽": 1}, "severities": {"HEAVY": 1}}}
        elif call.toolName == "gis.queryRegionSummary":
            data = REGION_SUMMARY
        elif call.toolName == "gis.queryDiseases":
            data = {"items": [DISEASE_OBJ], "summary": {"diseaseTypes": {"坑槽": 1}, "severities": {"HEAVY": 1}}}
        elif call.toolName == "template.match":
            data = {"available": True, "note": "模板匹配工具可用"}
        elif call.toolName == "solution.generateDraft":
            data = {"message": "方案生成工具已注册"}
        else:
            data = {"items": []}
        return ToolResult(toolName=call.toolName, success=True, summary="fake " + call.toolName, data=data, count=1, costMs=1)


class FakeLlmClient:
    async def chat(self, prompt):
        return ""


async def workflow_smoke():
    workflow = LangGraphWorkflow(gateway=FakeGateway(), llm_client=FakeLlmClient())
    req = request("分析当前选中病害并给出处置建议", "OBJECT", DISEASE_OBJ)
    response = await workflow.run(req, tenant_id="default", trace_id="phase50-15-parity")
    if response.intent != "OBJECT_ANALYSIS":
        raise SystemExit(f"[FAIL] workflow intent mismatch: {response.intent}")
    if not response.data.get("toolPlan"):
        raise SystemExit("[FAIL] workflow did not expose data.toolPlan")
    if not response.toolResults:
        raise SystemExit("[FAIL] workflow did not return toolResults")
    if not response.sources:
        raise SystemExit("[FAIL] workflow did not return sources")
    if "当前对象专项处置建议" not in response.answer:
        raise SystemExit("[FAIL] workflow answer missing disease enhancer")


asyncio.run(workflow_smoke())
print("[PASS] Phase50.15 local LangGraph native parity checks passed")
PY

if [ "${SRMP_PHASE50_15_LIVE:-0}" = "1" ]; then
  BODY='{"message":"分析当前选中病害并给出处置建议","mapContext":{"tenantId":"default","mode":"OBJECT","routeCode":"G210","year":2026,"mapObject":{"objectType":"DISEASE","objectId":"demo-disease-1","routeCode":"G210","year":2026,"diseaseName":"坑槽","severity":"HEAVY","startStake":100.0,"endStake":100.2},"selectedLayers":["DISEASE","ASSESSMENT_RESULT"],"userQuestion":"分析当前选中病害并给出处置建议"},"options":{"useKnowledge":true,"useTools":true,"topK":3,"traceId":"phase50-15-live"}}'
  curl -fsS --max-time 180 -X POST "${SRMP_JAVA_URL:-http://localhost:8080}/api/agent/map-agent/chat" \
    -H 'Content-Type: application/json' -H 'X-Tenant-Id: default' --data "$BODY" >/tmp/srmp-phase50-15-java.json
  PYTHONPATH="$ROOT/srmp-ai-orchestrator" "$PYTHON_BIN" - <<'PY'
import json
payload = json.load(open("/tmp/srmp-phase50-15-java.json", encoding="utf-8"))
data = payload.get("data") or {}
inner = data.get("data") or {}
if inner.get("orchestratorProvider") != "langgraph":
    raise SystemExit("[FAIL] Java chat did not use LangGraph")
if inner.get("orchestratorFallback") is not False:
    raise SystemExit("[FAIL] Java chat fell back to native")
if not data.get("toolResults"):
    raise SystemExit("[FAIL] Java chat did not return toolResults")
if not data.get("trace", {}).get("steps"):
    raise SystemExit("[FAIL] Java chat did not return trace steps")
print("[PASS] Phase50.15 live Java LangGraph parity smoke passed")
PY
fi
