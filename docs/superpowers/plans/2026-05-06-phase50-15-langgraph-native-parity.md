# Phase50.15 LangGraph Native Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `srmp-ai-orchestrator` implement the native one-map AI agent behavior closely enough that Java native fallback can later be removed.

**Architecture:** Keep Java as the only business Tool Gateway and make Python LangGraph own intent recognition, planning, evidence fusion, deterministic answer enhancement, and parity verification. Split current `workflow.py` rules into focused Python modules so parity logic is testable without FastAPI or Docker.

**Tech Stack:** Python 3.11, FastAPI, LangGraph, Pydantic, Bash acceptance checks, Java 8 Spring Boot Tool Gateway, Vue frontend consuming existing response fields.

---

## File Structure

- Create `srmp-ai-orchestrator/app/intent.py`: native-compatible intent recognition and intent detail extraction.
- Create `srmp-ai-orchestrator/app/planner.py`: native-compatible tool planning that returns `ToolCall` objects.
- Create `srmp-ai-orchestrator/app/evidence.py`: normalized tool/source helpers shared by prompt, fallback, enhancers, and tests.
- Create `srmp-ai-orchestrator/app/answer_enhancers.py`: deterministic business answer enhancements for disease, assessment, route, section, and region contexts.
- Modify `srmp-ai-orchestrator/app/workflow.py`: delegate intent, planner, evidence, answer enhancement, and parity metadata to the new modules.
- Modify `srmp-ai-orchestrator/app/prompt.py`: make fallback answer use normalized evidence summaries and native-compatible intent names.
- Modify `srmp-ai-orchestrator/app/config.py`: include the read-only `solution.generateDraft` capability tool in the default allowlist.
- Create `scripts/check-phase50-15-langgraph-native-parity.sh`: deterministic local parity gate plus optional live Java/Vite proxy smoke.
- Modify `scripts/check-phase50-12-langgraph-closure.sh`: add a lightweight guard that the new parity modules exist and workflow imports them.
- Optionally modify `docs/phase50_12_langgraph_closure.md` only if a verification command list needs to reference the new Phase50.15 script. Do not edit business docs in this phase.

## Task 1: Add The Failing Parity Acceptance Gate

**Files:**
- Create: `scripts/check-phase50-15-langgraph-native-parity.sh`

- [ ] **Step 1: Write the failing acceptance script**

Create `scripts/check-phase50-15-langgraph-native-parity.sh` with this content:

```bash
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

from app.answer_enhancers import enhance_answer
from app.intent import recognize_intent
from app.planner import plan_tools
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

CRACK_OBJ = dict(DISEASE_OBJ, diseaseName="裂缝", severity="MEDIUM")

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
    ("template", request("当前方案模板是否生效，变量有没有缺失"), "ROUTE", ROUTE_OBJ), "TEMPLATE_VERIFY", ["template.match", "knowledge.retrieve"], []),
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
```

- [ ] **Step 2: Run the script and verify it fails for the right reason**

Run:

```bash
bash scripts/check-phase50-15-langgraph-native-parity.sh
```

Expected result before implementation:

```text
ModuleNotFoundError: No module named 'app.answer_enhancers'
```

- [ ] **Step 3: Commit the failing gate**

```bash
chmod +x scripts/check-phase50-15-langgraph-native-parity.sh
git add scripts/check-phase50-15-langgraph-native-parity.sh
git commit -m "test: add LangGraph native parity gate"
```

## Task 2: Add Native-Compatible Intent Recognition

**Files:**
- Create: `srmp-ai-orchestrator/app/intent.py`
- Modify: `srmp-ai-orchestrator/app/workflow.py`

- [ ] **Step 1: Implement `intent.py`**

Create `srmp-ai-orchestrator/app/intent.py`:

```python
from typing import Any, Dict, Optional, Tuple

from .schemas import MapAiAgentRequest

NATIVE_INTENTS = {
    "OBJECT_ANALYSIS",
    "REGION_ANALYSIS",
    "NEARBY_ANALYSIS",
    "KNOWLEDGE_QA",
    "SOLUTION_GENERATE",
    "TEMPLATE_VERIFY",
    "TASK_SAVE",
    "GENERAL_CHAT",
}


def recognize_intent(request: MapAiAgentRequest) -> Tuple[str, Dict[str, Any]]:
    message = (request.message or "").strip()
    ctx = request.mapContext
    obj = ctx.mapObject if ctx and ctx.mapObject else request.mapObject or {}
    mode = str(ctx.mode or "").upper() if ctx and ctx.mode else ""
    joined = " ".join([message, mode, str(obj), str(ctx.selectedLayers if ctx else "")]).lower()
    object_type = normalize_object_type(first_string(obj, "objectType", "object_type", "type", "layerType"))
    detail = {
        "mode": mode,
        "objectType": object_type,
        "hasMapObject": bool(obj),
        "hasRegionSummary": bool(ctx and ctx.regionSummary),
        "hasGeometry": bool(ctx and ctx.geometry),
        "solutionDraftRequested": is_explicit_solution_draft_request(message),
        "saveRequested": contains_any(message, "保存", "归档", "确认方案"),
    }

    if detail["saveRequested"]:
        return "TASK_SAVE", detail
    if contains_any(message, "模板", "是否生效", "变量"):
        return "TEMPLATE_VERIFY", detail
    if detail["solutionDraftRequested"]:
        return "SOLUTION_GENERATE", detail
    if contains_any(message, "周边", "附近", "相邻", "集中区"):
        return "NEARBY_ANALYSIS", detail
    if contains_any(message, "区域", "框选", "范围") or mode in {"REGION", "BOX", "POLYGON", "SELECTION"} or detail["hasRegionSummary"] or detail["hasGeometry"]:
        return "REGION_ANALYSIS", detail
    if contains_any(message, "规范", "标准", "怎么处理", "工艺", "依据"):
        return "KNOWLEDGE_QA", detail
    if obj or mode == "OBJECT":
        return "OBJECT_ANALYSIS", detail
    return "GENERAL_CHAT", detail


def normalize_object_type(value: Optional[str]) -> str:
    raw = (value or "").strip().upper()
    aliases = {
        "ROUTE": "ROAD_ROUTE",
        "ROADROUTE": "ROAD_ROUTE",
        "SECTION": "ROAD_SECTION",
        "ROAD_SEGMENT": "ROAD_SECTION",
        "SEGMENT": "ROAD_SECTION",
        "ASSESSMENT": "ASSESSMENT_RESULT",
        "ASSESSMENT_RESULT_RECORD": "ASSESSMENT_RESULT",
        "EVALUATION_UNIT": "ASSESSMENT_RESULT",
        "DISEASE_RECORD": "DISEASE",
    }
    return aliases.get(raw, raw)


def is_explicit_solution_draft_request(message: str) -> bool:
    return contains_any(
        message,
        "生成方案",
        "方案草稿",
        "生成草稿",
        "保存方案",
        "保存为方案",
        "保存为方案任务",
        "形成方案",
        "生成任务",
        "生成养护方案",
        "保存任务",
        "创建方案任务",
    )


def contains_any(text: str, *words: str) -> bool:
    value = text or ""
    return any(word and word in value for word in words)


def first_string(data: Dict[str, Any], *keys: str) -> Optional[str]:
    if not isinstance(data, dict):
        return None
    for key in keys:
        value = data.get(key)
        if value not in (None, ""):
            return str(value).strip()
    raw = data.get("raw")
    if isinstance(raw, dict):
        return first_string(raw, *keys)
    return None
```

- [ ] **Step 2: Wire workflow intent recognition**

Modify `srmp-ai-orchestrator/app/workflow.py` imports:

```python
from .intent import recognize_intent
```

Replace `_intent_recognize` with:

```python
    async def _intent_recognize(self, state: AgentState) -> Dict[str, Any]:
        intent, detail = recognize_intent(state["request"])
        self._step(state, "intent_recognize", "识别用户意图", {"intent": intent, "intentDetail": detail})
        return {"intent": intent, "intent_detail": detail}
```

Add this key to `AgentState`:

```python
    intent_detail: Dict[str, Any]
```

- [ ] **Step 3: Run parity script and confirm it advances to planner failure**

Run:

```bash
bash scripts/check-phase50-15-langgraph-native-parity.sh
```

Expected result:

```text
ModuleNotFoundError: No module named 'app.planner'
```

- [ ] **Step 4: Commit intent recognition**

```bash
git add srmp-ai-orchestrator/app/intent.py srmp-ai-orchestrator/app/workflow.py
git commit -m "feat: add native-compatible LangGraph intent recognition"
```

## Task 3: Add Native-Compatible Tool Planning

**Files:**
- Create: `srmp-ai-orchestrator/app/planner.py`
- Modify: `srmp-ai-orchestrator/app/workflow.py`
- Modify: `srmp-ai-orchestrator/app/config.py`

- [ ] **Step 1: Implement `planner.py`**

Create `srmp-ai-orchestrator/app/planner.py`:

```python
from typing import Any, Dict, List, Optional

from .config import settings
from .intent import is_explicit_solution_draft_request, normalize_object_type
from .schemas import MapAiAgentRequest, MapAiContext, ToolCall


def plan_tools(request: MapAiAgentRequest, intent: str, intent_detail: Optional[Dict[str, Any]] = None) -> List[ToolCall]:
    ctx = request.mapContext
    obj = ctx.mapObject if ctx and ctx.mapObject else request.mapObject or {}
    calls: List[ToolCall] = []

    if intent == "NEARBY_ANALYSIS":
        add(calls, "gis.queryNearbyObjects", context_args(ctx, {"limit": 20}), "查询当前对象周边病害和评定结果")
    if intent == "REGION_ANALYSIS":
        add(calls, "gis.queryRegionSummary", region_args(ctx, {"limit": 50}), "查询框选区域或路线统计摘要")
    if intent in {"OBJECT_ANALYSIS", "SOLUTION_GENERATE"}:
        plan_object_tools(calls, ctx, obj)
    if intent == "TEMPLATE_VERIFY":
        add(calls, "template.match", {"intent": intent, "routeCode": ctx.routeCode if ctx else None, "year": ctx.year if ctx else None}, "检查方案模板匹配情况")
    if intent == "SOLUTION_GENERATE" and is_explicit_solution_draft_request(request.message or ""):
        add(calls, "solution.generateDraft", {"intent": intent, "routeCode": ctx.routeCode if ctx else None, "year": ctx.year if ctx else None}, "生成方案草稿能力预检查")
    if should_use_knowledge(request.options, intent):
        add(calls, "knowledge.retrieve", {"query": build_query(request, intent), "topK": option_int(request, "topK", 5)}, "知识库检索处置规则")
    if not calls:
        add(calls, "knowledge.retrieve", {"query": build_query(request, intent), "topK": option_int(request, "topK", 5)}, "默认知识库问答")
    return dedupe_and_limit(calls)


def plan_object_tools(calls: List[ToolCall], ctx: Optional[MapAiContext], obj: Dict[str, Any]) -> None:
    object_type = normalize_object_type(first(obj, "objectType", "object_type", "type", "layerType"))
    if object_type == "ROAD_ROUTE":
        add(calls, "gis.queryAssessmentResults", context_args(ctx, {"limit": 20}), "路线评定结果查询")
        add(calls, "gis.queryDiseases", context_args(ctx, {"limit": 50}), "路线病害查询")
        return
    if object_type == "ROAD_SECTION":
        add(calls, "gis.queryAssessmentResults", context_args(ctx, {"limit": 20}), "路段评定结果查询")
        add(calls, "gis.queryDiseases", context_args(ctx, {"limit": 50}), "路段病害查询")
        add(calls, "gis.queryDiseasesByStakeRange", stake_args(obj, {"limit": 50}), "路段桩号范围内病害查询")
        return
    if object_type == "DISEASE":
        add(calls, "gis.queryNearbyObjects", context_args(ctx, {"limit": 20}), "当前病害周边对象查询")
        return
    if object_type == "ASSESSMENT_RESULT":
        add(calls, "gis.queryAssessmentResults", context_args(ctx, {"limit": 20}), "当前评定结果查询")
        add(calls, "gis.queryDiseasesByStakeRange", stake_args(obj, {"limit": 50}), "评定单元内病害查询")


def should_use_knowledge(options: Dict[str, Any], intent: str) -> bool:
    if options and "useKnowledge" in options:
        return str(options.get("useKnowledge")).lower() not in {"false", "0", "no", "off"}
    return intent in {"KNOWLEDGE_QA", "SOLUTION_GENERATE", "OBJECT_ANALYSIS", "REGION_ANALYSIS", "TEMPLATE_VERIFY", "GENERAL_CHAT", "NEARBY_ANALYSIS"}


def add(calls: List[ToolCall], tool_name: str, args: Dict[str, Any], reason: str) -> None:
    if settings.allowed_tools and tool_name not in settings.allowed_tools:
        return
    calls.append(ToolCall(toolName=tool_name, args=compact(args), reason=reason))


def dedupe_and_limit(calls: List[ToolCall]) -> List[ToolCall]:
    result: List[ToolCall] = []
    seen = set()
    for call in calls:
        key = (call.toolName, str(sorted((call.args or {}).items())))
        if key in seen:
            continue
        seen.add(key)
        result.append(call)
        if len(result) >= settings.max_tool_calls:
            break
    return result


def context_args(ctx: Optional[MapAiContext], extra: Dict[str, Any]) -> Dict[str, Any]:
    base = {"tenantId": ctx.tenantId if ctx else None, "routeCode": ctx.routeCode if ctx else None, "year": ctx.year if ctx else None}
    base.update(extra)
    return base


def region_args(ctx: Optional[MapAiContext], extra: Dict[str, Any]) -> Dict[str, Any]:
    base = context_args(ctx, extra)
    if ctx:
        base.update({"mode": ctx.mode, "geometry": ctx.geometry, "viewport": ctx.viewport, "regionSummary": ctx.regionSummary})
    return base


def stake_args(obj: Dict[str, Any], extra: Dict[str, Any]) -> Dict[str, Any]:
    base = {
        "routeCode": first(obj, "routeCode", "route_code", "route", "routeNo", "route_no"),
        "stakeStart": first(obj, "stakeStart", "startStake", "startStakeNo", "start_stake", "startMileage"),
        "stakeEnd": first(obj, "stakeEnd", "endStake", "endStakeNo", "end_stake", "endMileage"),
    }
    base.update(extra)
    return base


def build_query(request: MapAiAgentRequest, intent: str) -> str:
    ctx = request.mapContext
    parts = [request.message or "", intent]
    if ctx:
        parts.extend([str(ctx.routeCode or ""), str(ctx.year or ""), str(ctx.mode or "")])
        obj = ctx.mapObject or {}
        parts.extend([str(first(obj, "diseaseName", "disease_name", "diseaseType", "disease_type", "name") or ""), str(first(obj, "severity", "grade") or "")])
    parts.extend(["道路养护", "处置建议", "评定规则"])
    return " ".join([part for part in parts if part])


def option_int(request: MapAiAgentRequest, key: str, default: int) -> int:
    try:
        return int((request.options or {}).get(key) or default)
    except Exception:
        return default


def compact(data: Dict[str, Any]) -> Dict[str, Any]:
    return {key: value for key, value in data.items() if value not in (None, "")}


def first(data: Dict[str, Any], *keys: str) -> Optional[str]:
    if not isinstance(data, dict):
        return None
    for key in keys:
        value = data.get(key)
        if value not in (None, ""):
            return str(value).strip()
    raw = data.get("raw")
    if isinstance(raw, dict):
        return first(raw, *keys)
    return None
```

- [ ] **Step 2: Allow the read-only solution draft capability tool**

Modify `srmp-ai-orchestrator/app/config.py` default `SRMP_LANGGRAPH_ALLOWED_TOOLS` list to:

```python
"knowledge.retrieve,gis.queryDiseases,gis.queryAssessmentResults,gis.queryDiseasesByStakeRange,gis.queryRegionSummary,gis.queryNearbyObjects,template.match,solution.generateDraft",
```

- [ ] **Step 3: Wire workflow planning**

Modify `srmp-ai-orchestrator/app/workflow.py` imports:

```python
from .planner import plan_tools
```

Replace `_tool_plan` with:

```python
    async def _tool_plan(self, state: AgentState) -> Dict[str, Any]:
        calls = plan_tools(state["request"], state["intent"], state.get("intent_detail", {}))
        self._step(state, "tool_plan", "规划只读工具", {"strategy": settings.strategy_version, "tools": [item.model_dump() for item in calls]})
        return {"tool_plan": calls}
```

- [ ] **Step 4: Run the parity script and confirm it advances to answer enhancer failure**

Run:

```bash
bash scripts/check-phase50-15-langgraph-native-parity.sh
```

Expected result:

```text
ModuleNotFoundError: No module named 'app.answer_enhancers'
```

- [ ] **Step 5: Commit tool planning**

```bash
git add srmp-ai-orchestrator/app/planner.py srmp-ai-orchestrator/app/config.py srmp-ai-orchestrator/app/workflow.py
git commit -m "feat: align LangGraph tool planning with native agent"
```

## Task 4: Add Evidence Helpers And Deterministic Answer Enhancers

**Files:**
- Create: `srmp-ai-orchestrator/app/evidence.py`
- Create: `srmp-ai-orchestrator/app/answer_enhancers.py`
- Modify: `srmp-ai-orchestrator/app/prompt.py`
- Modify: `srmp-ai-orchestrator/app/workflow.py`

- [ ] **Step 1: Implement `evidence.py`**

Create `srmp-ai-orchestrator/app/evidence.py`:

```python
from typing import Any, Dict, Iterable, List

from .schemas import ToolResult


def tool_items(tool_results: Iterable[ToolResult], tool_name: str) -> List[Dict[str, Any]]:
    for result in tool_results or []:
        if result.toolName != tool_name or not result.success:
            continue
        return extract_items(result.data)
    return []


def extract_items(data: Any) -> List[Dict[str, Any]]:
    if isinstance(data, list):
        return [item for item in data if isinstance(item, dict)]
    if not isinstance(data, dict):
        return []
    for key in ("items", "records", "list", "data", "diseases", "assessments", "hits", "sources"):
        value = data.get(key)
        if isinstance(value, list):
            return [item for item in value if isinstance(item, dict)]
    nested = data.get("data")
    if isinstance(nested, dict):
        return extract_items(nested)
    return []


def tool_summary(tool_results: Iterable[ToolResult]) -> Dict[str, Any]:
    summary = {"assessmentCount": 0, "diseaseCount": 0, "heavyDiseaseCount": 0, "toolCount": 0}
    disease_types: Dict[str, int] = {}
    severities: Dict[str, int] = {}
    for result in tool_results or []:
        if not result.success:
            continue
        summary["toolCount"] += 1
        if result.toolName in {"gis.queryDiseases", "gis.queryDiseasesByStakeRange"}:
            count = result.count if isinstance(result.count, int) else len(extract_items(result.data))
            summary["diseaseCount"] += count
            merge_counts(disease_types, nested_counts(result.data, "diseaseTypes"))
            merge_counts(severities, nested_counts(result.data, "severities"))
        if result.toolName == "gis.queryAssessmentResults":
            summary["assessmentCount"] += result.count if isinstance(result.count, int) else len(extract_items(result.data))
    summary["diseaseTypes"] = disease_types
    summary["severities"] = severities
    summary["heavyDiseaseCount"] = severities.get("HEAVY", 0) + severities.get("重", 0) + severities.get("严重", 0)
    return summary


def nested_counts(data: Any, key: str) -> Dict[str, int]:
    if not isinstance(data, dict):
        return {}
    summary = data.get("summary")
    if isinstance(summary, dict) and isinstance(summary.get(key), dict):
        return {str(k): int(v) for k, v in summary.get(key).items()}
    value = data.get(key)
    if isinstance(value, dict):
        return {str(k): int(v) for k, v in value.items()}
    return {}


def merge_counts(target: Dict[str, int], source: Dict[str, int]) -> None:
    for key, value in source.items():
        target[key] = target.get(key, 0) + int(value)


def first(data: Dict[str, Any], *keys: str) -> str:
    if not isinstance(data, dict):
        return ""
    for key in keys:
        value = data.get(key)
        if value not in (None, ""):
            return str(value).strip()
    raw = data.get("raw")
    if isinstance(raw, dict):
        return first(raw, *keys)
    return ""
```

- [ ] **Step 2: Implement `answer_enhancers.py`**

Create `srmp-ai-orchestrator/app/answer_enhancers.py`:

```python
from typing import Any, Dict, List

from .evidence import first, tool_summary
from .intent import normalize_object_type
from .schemas import MapAiAgentRequest, ToolResult


def enhance_answer(answer: str, request: MapAiAgentRequest, intent: str, intent_detail: Dict[str, Any], tool_results: List[ToolResult], sources: List[Dict[str, Any]]) -> str:
    value = (answer or "").strip()
    ctx = request.mapContext
    obj = ctx.mapObject if ctx and ctx.mapObject else request.mapObject or {}
    object_type = normalize_object_type(first(obj, "objectType", "object_type", "type", "layerType"))
    if intent == "REGION_ANALYSIS":
        value = append_once(value, "当前框选区域专项分析", region_advice(ctx.regionSummary if ctx else {}, tool_results))
    if object_type == "DISEASE" or first(obj, "diseaseName", "disease_name", "diseaseType", "disease_type"):
        value = append_once(value, "当前对象专项处置建议", disease_advice(obj, tool_results, sources))
    if object_type == "ASSESSMENT_RESULT":
        value = append_once(value, "当前评定单元专项分析", assessment_advice(obj, tool_results))
    if object_type == "ROAD_ROUTE":
        value = append_once(value, "当前路线专项分析", route_advice(obj, tool_results))
    if object_type == "ROAD_SECTION":
        value = append_once(value, "当前路段专项分析", section_advice(obj, tool_results))
    value = enrich_crack_terms(value, request.message or "", obj, sources)
    return polish(value)


def disease_advice(obj: Dict[str, Any], tool_results: List[ToolResult], sources: List[Dict[str, Any]]) -> str:
    disease_name = first(obj, "diseaseName", "disease_name", "diseaseType", "disease_type") or "道路病害"
    severity = first(obj, "severity", "grade", "level")
    route = first(obj, "routeCode", "route_code") or "-"
    stake = stake_range(obj)
    priority = priority_by_severity(severity)
    lines = [
        "### 当前对象专项处置建议",
        f"- 当前对象：{route}{(' ' + stake) if stake else ''}，病害为{disease_name}{('，严重程度为 ' + severity) if severity else ''}。",
        f"- 建议优先级：{priority}。",
        "",
    ]
    if contains_any(disease_name, "坑槽", "POTHOLE"):
        lines.extend(["一、主要问题", "坑槽影响行车安全和舒适性，通常与水损害、松散、裂缝发展、基层破坏或局部材料脱落有关。", "", "二、现场复核重点", "- 复核坑槽深度、边界松散范围、基层是否破坏和是否存在积水；", "- 检查周边是否有裂缝、松散、沉陷或重复修补痕迹。", "", "三、养护处置建议", "- 规则切割病害边界，清理松散材料，保持基面干净干燥；", "- 采用热拌料或冷补料填补，并分层摊铺、充分压实；", "- 若基层已破坏，应先处理基层和排水，再恢复面层。"])
    elif contains_any(disease_name, "沉陷", "SUBSIDENCE"):
        lines.extend(["一、主要问题", "沉陷可能涉及基层松散、路基不均匀变形、含水软化或排水不良。", "", "二、现场复核重点", "- 复核沉陷深度、范围、发展趋势、周边裂缝和积水痕迹；", "- 检查基层稳定性、路基局部变形、含水情况和排水条件。", "", "三、养护处置建议", "- 若仅为面层局部沉陷，可局部铣刨、找平、重新摊铺并压实；", "- 若基层松散或含水，应先处理基层和排水；", "- 若涉及路基不均匀沉降，应进行局部结构修复或路基加固。"])
    elif contains_any(disease_name, "裂缝", "开裂", "CRACK"):
        lines.extend(["一、主要问题", "裂缝类病害需重点防止雨水渗水和下渗诱发基层水损害。", "", "二、现场复核重点", "- 复核裂缝宽度、长度、密度、发展方向和是否渗水；", "- 判断是否为横向裂缝、纵向裂缝、网裂或块裂。", "", "三、养护处置建议", "- 轻中度裂缝可采用灌缝、开槽灌缝或封缝；", "- 裂缝较密集或表层老化时，可结合封层、薄层罩面或雾封层；", "- 重度网裂或块裂应进一步判断结构层承载能力。"])
    else:
        lines.extend(["一、主要问题", "该对象属于道路病害，应结合严重程度、范围、周边病害和评定指标综合判断。", "", "二、现场复核重点", "- 复核病害范围、深度、面积、发展趋势和排水条件；", "- 检查是否与低分单元、同类病害聚集区或结构性问题相关。", "", "三、养护处置建议", "- 轻度病害可纳入日常养护；", "- 中度病害建议近期处置；", "- 重度病害建议优先复核并制定专项处置方案。"])
    if sources:
        lines.extend(["", "四、参考依据", "- " + "；".join([str(item.get("title") or "知识片段") for item in sources[:3]])])
    return "\n".join(lines)


def assessment_advice(obj: Dict[str, Any], tool_results: List[ToolResult]) -> str:
    summary = tool_summary(tool_results)
    route = first(obj, "routeCode", "route_code") or "-"
    return "\n".join([
        "### 当前评定单元专项分析",
        f"- 当前对象：{route} {stake_range(obj)}，年度：{first(obj, 'year') or '-'}。",
        f"- 评定等级：{first(obj, 'grade') or '-'}。",
        f"- 指标值：MQI={first(obj, 'mqi') or '-'}，PQI={first(obj, 'pqi') or '-'}，PCI={first(obj, 'pci') or '-'}，RQI={first(obj, 'rqi') or '-'}，RDI={first(obj, 'rdi') or '-'}。",
        f"- 单元内病害：已查询 {summary.get('diseaseCount', 0)} 条，重点关注 PCI/PQI 偏低与病害集中分布的关联。",
        "",
        "一、主要问题",
        "该对象为道路技术状况评定结果单元，应结合 MQI/PQI/PCI 判断综合技术状况、路面使用性能和路面损坏状况。",
        "",
        "二、现场复核重点",
        "- 复核单元范围内裂缝、坑槽、沉陷、松散和修补损坏的数量、范围和严重程度；",
        "- 对比相邻评定单元，判断低分是否为局部异常还是连续区间问题。",
        "",
        "三、养护处置建议",
        "- PCI 偏低时优先处置路面破损类病害；",
        "- PQI 偏低且病害连续时，可考虑封层、薄层罩面、局部铣刨重铺或中修方案；",
        "- 若存在基层松散、含水软化或排水不良，应先处理基层和排水，再恢复面层。",
    ])


def route_advice(obj: Dict[str, Any], tool_results: List[ToolResult]) -> str:
    summary = tool_summary(tool_results)
    return "\n".join(["### 当前路线专项分析", f"- 当前路线：{first(obj, 'routeCode', 'route_code') or '-'}。", f"- 系统已查询评定结果 {summary.get('assessmentCount', 0)} 条、病害 {summary.get('diseaseCount', 0)} 条。", "一、主要问题", "路线级分析应重点识别低分单元、病害热点、连续破损区间和重复修补区间。", "", "二、养护处置建议", "- 先按 MQI/PQI/PCI 对路线分段排序，识别优先处置区间；", "- 对点状病害采用局部修补、裂缝灌缝、坑槽修补；", "- 对连续低分或病害聚集区间采用封层、薄层罩面、局部铣刨重铺或中修。"])


def section_advice(obj: Dict[str, Any], tool_results: List[ToolResult]) -> str:
    summary = tool_summary(tool_results)
    return "\n".join(["### 当前路段专项分析", f"- 当前路段：{first(obj, 'routeCode', 'route_code') or '-'} {stake_range(obj)}。", f"- 系统已查询评定结果 {summary.get('assessmentCount', 0)} 条、病害 {summary.get('diseaseCount', 0)} 条。", "一、主要问题", "路段级分析应关注病害是否沿桩号连续分布、是否与排水、基层或重复修补有关。", "", "二、养护处置建议", "- 病害点状分布时采用局部修补、坑槽修补、裂缝灌缝；", "- 病害连续分布或评定指标偏低时采用封层、薄层罩面、局部铣刨重铺或中修；", "- 若存在沉陷、基层松散或排水问题，应先处理基层和排水。"])


def region_advice(region_summary: Dict[str, Any], tool_results: List[ToolResult]) -> str:
    disease = region_summary.get("diseaseSummary") if isinstance(region_summary, dict) else {}
    assessment = region_summary.get("assessmentSummary") if isinstance(region_summary, dict) else {}
    return "\n".join(["### 当前框选区域专项分析", f"- 区域路段数：{region_summary.get('sectionCount', '-') if isinstance(region_summary, dict) else '-'}，评定单元数：{region_summary.get('unitCount', '-') if isinstance(region_summary, dict) else '-'}。", f"- 病害数量：{disease.get('disease_count', disease.get('diseaseCount', '-')) if isinstance(disease, dict) else '-'}，重度病害：{disease.get('heavy_count', disease.get('heavyCount', '-')) if isinstance(disease, dict) else '-'}。", f"- 平均 MQI：{assessment.get('avg_mqi', assessment.get('avgMqi', '-')) if isinstance(assessment, dict) else '-'}。", "", "一、区域风险判断", "优先关注重度病害集中区、低分评定单元和病害热点重叠范围。", "", "二、区域养护建议", "- 对热点区间安排现场复核，确认病害边界和工程量；", "- 对连续低分或病害聚集区间形成区段化养护建议；", "- 对零散轻中度病害纳入日常养护和预防性养护计划。"])


def enrich_crack_terms(answer: str, message: str, obj: Dict[str, Any], sources: List[Dict[str, Any]]) -> str:
    disease_name = first(obj, "diseaseName", "disease_name", "diseaseType", "disease_type")
    if not contains_any(message + disease_name, "裂缝", "开裂", "灌缝", "封缝"):
        return answer
    compact = answer.replace(" ", "")
    if contains_any(compact, "灌缝", "封缝") and contains_any(compact, "封层", "雾封层", "薄层罩面") and contains_any(compact, "渗水", "下渗", "水损害"):
        return answer
    return answer + "\n\n### 知识库处置要点补充\n结合裂缝类病害处置资料，应重点关注雨水渗水和下渗风险，防止基层水损害扩大；处置上可采用开槽灌缝或封缝，裂缝较密集时结合封层、薄层罩面或雾封层。"


def append_once(answer: str, marker: str, addition: str) -> str:
    if marker in answer:
        return answer
    return (answer.rstrip() + "\n\n" + addition).strip() if answer else addition.strip()


def stake_range(obj: Dict[str, Any]) -> str:
    start = first(obj, "stakeStart", "startStake", "start_stake", "startMileage")
    end = first(obj, "stakeEnd", "endStake", "end_stake", "endMileage")
    if start and end:
        return f"K{start}-K{end}"
    if start:
        return f"K{start}"
    return ""


def priority_by_severity(severity: str) -> str:
    if contains_any(severity, "HEAVY", "重", "严重"):
        return "P1（优先复核和近期处置）"
    if contains_any(severity, "MEDIUM", "中"):
        return "P2（近期计划处置）"
    if contains_any(severity, "LIGHT", "轻"):
        return "P3（日常养护跟踪）"
    return "结合现场复核结果确定"


def contains_any(text: str, *words: str) -> bool:
    value = text or ""
    return any(word and word in value for word in words)


def polish(answer: str) -> str:
    return "\n".join([line.rstrip() for line in (answer or "").splitlines()]).strip()
```

- [ ] **Step 3: Wire answer enhancement in workflow**

Modify `srmp-ai-orchestrator/app/workflow.py` imports:

```python
from .answer_enhancers import enhance_answer
```

In `_answer_generate`, after fallback/LLM answer is selected and before the step is recorded, add:

```python
        sources = extract_knowledge_sources(tool_results)
        answer = enhance_answer(answer, request, intent, state.get("intent_detail", {}), tool_results, sources)
```

- [ ] **Step 4: Run the parity script and verify local parity passes**

Run:

```bash
bash scripts/check-phase50-15-langgraph-native-parity.sh
```

Expected result:

```text
[PASS] Phase50.15 local LangGraph native parity checks passed
```

- [ ] **Step 5: Commit evidence and answer parity**

```bash
git add srmp-ai-orchestrator/app/evidence.py srmp-ai-orchestrator/app/answer_enhancers.py srmp-ai-orchestrator/app/workflow.py srmp-ai-orchestrator/app/prompt.py
git commit -m "feat: add deterministic LangGraph answer parity"
```

## Task 5: Strengthen Workflow Contract And Trace Metadata

**Files:**
- Modify: `srmp-ai-orchestrator/app/workflow.py`
- Modify: `srmp-ai-orchestrator/app/main.py`
- Modify: `scripts/check-phase50-12-langgraph-closure.sh`

- [ ] **Step 1: Add parity metadata to response data**

In `srmp-ai-orchestrator/app/workflow.py` `_to_response`, add these keys to `data`:

```python
                "intentDetail": state.get("intent_detail", {}),
                "parityVersion": "phase50.15-native-parity",
                "orchestratorFallback": False,
```

- [ ] **Step 2: Add status/count fields to trace steps**

Replace `_step` in `srmp-ai-orchestrator/app/workflow.py` with:

```python
    def _step(self, state: AgentState, node: str, message: str, data: Optional[Dict[str, Any]] = None, status: str = "SUCCESS", count: Optional[int] = None, error: Optional[str] = None) -> None:
        steps = state.setdefault("steps", [])
        item = {
            "node": node,
            "name": node,
            "message": message,
            "status": status,
            "data": data or {},
            "elapsedMs": int((time.perf_counter() - state.get("started_at", time.perf_counter())) * 1000),
        }
        if count is not None:
            item["count"] = count
        if error:
            item["error"] = error
            item["errorMessage"] = error
        steps.append(item)
```

In `_tool_execute`, update the final `_step` call:

```python
        self._step(
            state,
            "tool_execute",
            "执行只读工具",
            {
                "success": sum(1 for item in results if item.success),
                "failed": sum(1 for item in results if not item.success),
                "tools": [item.toolName for item in results],
            },
            status="SUCCESS" if any(item.success for item in results) else "FAILED",
            count=len(results),
        )
```

- [ ] **Step 3: Update static closure guard**

Append these checks to `scripts/check-phase50-12-langgraph-closure.sh` after the existing workflow checks:

```bash
grep -Fq 'from .intent import recognize_intent' "$WORKFLOW_FILE" \
  || fail "LangGraph workflow should delegate native-compatible intent recognition"
grep -Fq 'from .planner import plan_tools' "$WORKFLOW_FILE" \
  || fail "LangGraph workflow should delegate native-compatible tool planning"
grep -Fq 'from .answer_enhancers import enhance_answer' "$WORKFLOW_FILE" \
  || fail "LangGraph workflow should apply native-parity answer enhancement"
```

- [ ] **Step 4: Run closure and parity checks**

Run:

```bash
bash scripts/check-phase50-12-langgraph-closure.sh
bash scripts/check-phase50-15-langgraph-native-parity.sh
```

Expected result:

```text
[PASS] Phase50.12 LangGraph closure static checks passed
[PASS] Phase50.15 local LangGraph native parity checks passed
```

- [ ] **Step 5: Commit workflow contract metadata**

```bash
git add srmp-ai-orchestrator/app/workflow.py srmp-ai-orchestrator/app/main.py scripts/check-phase50-12-langgraph-closure.sh
git commit -m "feat: expose LangGraph parity metadata"
```

## Task 6: Run Live LangGraph Parity Against Java And Vite Proxy

**Files:**
- Modify only if live checks reveal a contract bug:
  - `srmp-ai-orchestrator/app/*.py`
  - `srmp-agent/src/main/java/com/smartroad/srmp/agent/**/*.java`
  - `srmp-web-ui/src/views/gis/components/AgentChatFloat.vue`

- [ ] **Step 1: Rebuild backend and orchestrator containers**

Run:

```bash
docker compose -f docker-compose.yml -f docker-compose.app.yml -f docker-compose.langgraph.yml up -d --build backend srmp-ai-orchestrator
```

Expected result:

```text
Container srmp-ai-orchestrator Started
Container srmp-backend Started
```

- [ ] **Step 2: Wait for readiness**

Run:

```bash
srmp-ai-orchestrator/.venv/bin/python - <<'PY'
import time
import urllib.request

for url in ["http://localhost:18080/ready", "http://localhost:8080/api/agent/orchestrator/ops/summary"]:
    last = None
    for _ in range(60):
        try:
            with urllib.request.urlopen(url, timeout=3) as response:
                text = response.read().decode("utf-8", errors="replace")
                print(url, response.status, text[:120])
                break
        except Exception as exc:
            last = exc
            time.sleep(2)
    else:
        raise SystemExit(f"{url} not ready: {last}")
PY
```

Expected result: both URLs return HTTP `200`, and ops summary contains `"provider":"langgraph"`.

- [ ] **Step 3: Run live parity through Java**

Run:

```bash
SRMP_PHASE50_15_LIVE=1 bash scripts/check-phase50-15-langgraph-native-parity.sh
```

Expected result:

```text
[PASS] Phase50.15 local LangGraph native parity checks passed
[PASS] Phase50.15 live Java LangGraph parity smoke passed
```

- [ ] **Step 4: Run live parity through the Vite proxy**

Run:

```bash
BODY='{"message":"分析当前选中病害并给出处置建议","mapContext":{"tenantId":"default","mode":"OBJECT","routeCode":"G210","year":2026,"mapObject":{"objectType":"DISEASE","objectId":"demo-disease-1","routeCode":"G210","year":2026,"diseaseName":"坑槽","severity":"HEAVY","startStake":100.0,"endStake":100.2},"selectedLayers":["DISEASE","ASSESSMENT_RESULT"],"userQuestion":"分析当前选中病害并给出处置建议"},"options":{"useKnowledge":true,"useTools":true,"topK":3,"traceId":"phase50-15-vite"}}'
curl -fsS --max-time 180 -X POST http://localhost:5173/api/agent/map-agent/chat \
  -H 'Content-Type: application/json' -H 'X-Tenant-Id: default' --data "$BODY" >/tmp/srmp-phase50-15-vite.json
srmp-ai-orchestrator/.venv/bin/python - <<'PY'
import json
payload = json.load(open("/tmp/srmp-phase50-15-vite.json", encoding="utf-8"))
data = payload.get("data") or {}
inner = data.get("data") or {}
assert inner.get("orchestratorProvider") == "langgraph", inner
assert inner.get("orchestratorFallback") is False, inner
assert data.get("toolResults"), data
assert data.get("trace", {}).get("steps"), data.get("trace")
assert "当前对象专项处置建议" in (data.get("answer") or ""), data.get("answer")
print("[PASS] Phase50.15 Vite proxy LangGraph parity smoke passed")
PY
```

Expected result:

```text
[PASS] Phase50.15 Vite proxy LangGraph parity smoke passed
```

- [ ] **Step 5: Commit live-smoke fixes if any were needed**

If Step 3 or Step 4 required code changes, run:

```bash
git add srmp-ai-orchestrator/app srmp-agent/src/main/java srmp-web-ui/src/views/gis/components/AgentChatFloat.vue scripts/check-phase50-15-langgraph-native-parity.sh
git commit -m "fix: stabilize LangGraph live parity"
```

If no code changes were needed, do not create an empty commit.

## Task 7: Final Verification And Handoff

**Files:**
- No required code changes.

- [ ] **Step 1: Run all deterministic verification commands**

Run:

```bash
bash scripts/check-phase50-14-langgraph-map-agent-parity.sh
bash scripts/check-phase50-15-langgraph-native-parity.sh
bash scripts/check-phase50-12-langgraph-closure.sh
srmp-ai-orchestrator/.venv/bin/python -m py_compile srmp-ai-orchestrator/app/*.py
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -pl srmp-agent test
git diff --check
```

Expected result:

```text
[PASS] LangGraph map-agent executes planned tools
[PASS] Phase50.15 local LangGraph native parity checks passed
[PASS] Phase50.12 LangGraph closure static checks passed
BUILD SUCCESS
```

- [ ] **Step 2: Remove Python cache directories**

Run:

```bash
find srmp-ai-orchestrator/app -type d -name '__pycache__' -prune -exec rm -rf {} +
git status --short
```

Expected result: no tracked cache files appear. Existing untracked `deploy/outline/*` files may remain and must not be staged.

- [ ] **Step 3: Summarize the native removal gate**

Add this note to the final response after implementation:

```text
Phase50.15 reaches LangGraph parity but intentionally keeps NativeMapAgentOrchestrator in place. Native removal is the next phase after live parity is accepted and fallback metrics stay clean.
```

- [ ] **Step 4: Final commit if verification changed scripts or docs**

If verification caused planned script or doc edits, run:

```bash
git add scripts docs srmp-ai-orchestrator/app srmp-agent/src
git commit -m "chore: verify LangGraph native parity"
```

If no files changed, do not create an empty commit.

## Self-Review

- Spec coverage: the plan covers intent parity, tool planning parity, answer parity, trace metadata, read-only write-like behavior, Java contract safety, deterministic local checks, and live Java/Vite smoke.
- Placeholder scan: the plan contains no unresolved placeholder markers and no open-ended implementation steps.
- Type consistency: planned modules use existing `MapAiAgentRequest`, `MapAiContext`, `ToolCall`, and `ToolResult` Pydantic models; workflow state keys are `intent`, `intent_detail`, `tool_plan`, `tool_results`, and `quality`.
