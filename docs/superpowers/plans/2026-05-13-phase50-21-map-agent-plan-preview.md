# Phase50.21 One-Map AI Plan Preview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a business-facing LangGraph execution plan preview to OneMap AI so users can inspect intent, map context, planned tools, source hints, and risks before running the request.

**Architecture:** Keep `/api/agent/map-agent/run` as the only execution path and reuse `/api/agent/orchestrator/ops/plan` for read-only planning. Stabilize the Runtime plan response with action, enriched tools, source hints, and warnings; normalize that response in a small frontend utility; render it in a dedicated drawer; then wire the drawer into `AgentChatFloat.vue` without moving OneMap execution ownership yet.

**Tech Stack:** Python 3.11 + FastAPI + Pydantic, Vue 3 + Element Plus + TypeScript, Node built-in test runner, existing Java Spring proxy.

---

## File Structure

- Create `srmp-ai-orchestrator/app/plan_preview.py`: pure helper functions for enriching plan tools, deriving source hints, and building warnings. No FastAPI or gateway dependency.
- Modify `srmp-ai-orchestrator/app/schemas.py`: add typed `action` and `actionInput` to `MapAiAgentRequest` while keeping extra fields allowed.
- Modify `srmp-ai-orchestrator/app/workflow.py`: call `plan_preview.py` from `LangGraphWorkflow.plan()` and return stable fields.
- Create `srmp-ai-orchestrator/tests/test_debug_plan_preview.py`: endpoint tests for plan response shape and warnings.
- Create `srmp-web-ui/src/utils/mapAiPlanPreview.ts`: frontend normalization, source-hint derivation, context summaries, and risk warnings.
- Create `srmp-web-ui/tests/mapAiPlanPreview.test.mjs`: unit tests for the frontend normalizer.
- Create `srmp-web-ui/src/views/gis/components/MapAiPlanPreviewDrawer.vue`: reusable plan preview drawer.
- Modify `srmp-web-ui/src/views/gis/components/map-ai/MapAiSuggestedActions.vue`: expose a `preview-plan` event for heavy suggested actions.
- Modify `srmp-web-ui/src/views/gis/components/AgentChatFloat.vue`: add plan state, buttons, request snapshots, drawer, and execute-from-plan behavior.

---

## Task 1: Backend Plan Preview Contract Tests

**Files:**
- Create: `srmp-ai-orchestrator/tests/test_debug_plan_preview.py`

- [ ] **Step 1: Write the failing endpoint tests**

Create `srmp-ai-orchestrator/tests/test_debug_plan_preview.py`:

```python
import unittest

from fastapi.testclient import TestClient

from app.main import app


class DebugPlanPreviewApiTest(unittest.TestCase):
    def test_region_solution_plan_returns_action_sources_and_enriched_tools(self):
        client = TestClient(app)

        response = client.post(
            "/api/srmp/langgraph/debug/plan",
            json={
                "action": "GENERATE_REGION_SOLUTION",
                "message": "生成框选区域养护建议",
                "mapContext": {
                    "tenantId": "default",
                    "mode": "REGION",
                    "routeCode": "G210",
                    "year": 2026,
                    "geometry": {
                        "type": "Polygon",
                        "coordinates": [[[106.0, 25.8], [106.2, 25.8], [106.2, 26.0], [106.0, 25.8]]],
                    },
                    "regionSummary": {
                        "routeCount": 1,
                        "sectionCount": 4,
                        "diseaseSummary": {"disease_count": 59},
                    },
                    "selectedLayers": ["ROAD_SECTION", "DISEASE", "ASSESSMENT_RESULT"],
                },
                "actionInput": {"solutionType": "REGION_MAINTENANCE_SUGGESTION"},
                "options": {"useKnowledge": True, "topK": 5, "traceId": "plan-region-1"},
            },
        )

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertEqual("plan-region-1", body["traceId"])
        self.assertEqual("GENERATE_REGION_SOLUTION", body["action"])
        self.assertIn(body["intent"], {"REGION_ANALYSIS", "SOLUTION_GENERATE"})
        self.assertIsInstance(body["contextSummary"], dict)
        self.assertIsInstance(body["toolPlan"], list)
        self.assertTrue(body["toolPlan"])
        self.assertTrue(all("label" in item for item in body["toolPlan"]))
        self.assertTrue(all("readOnly" in item for item in body["toolPlan"]))
        self.assertTrue(all("writeRisk" in item for item in body["toolPlan"]))
        self.assertFalse(any(item["writeRisk"] for item in body["toolPlan"]))
        self.assertTrue(any(item["sourceType"] == "BUSINESS_DATA" for item in body["sourceHints"]))
        self.assertTrue(any(item["sourceType"] == "KNOWLEDGE" for item in body["sourceHints"]))
        self.assertTrue(any(item["sourceType"] == "MAP_REGION" for item in body["sourceHints"]))
        self.assertEqual([], [item for item in body["warnings"] if item["code"] == "REGION_GEOMETRY_MISSING"])

    def test_region_plan_warns_when_geometry_is_missing(self):
        client = TestClient(app)

        response = client.post(
            "/api/srmp/langgraph/debug/plan",
            json={
                "action": "ANALYZE_REGION",
                "message": "分析当前区域",
                "mapContext": {
                    "tenantId": "default",
                    "mode": "REGION",
                    "routeCode": "G210",
                    "year": 2026,
                    "regionSummary": {"routeCount": 1},
                },
                "options": {"useKnowledge": False, "traceId": "plan-region-missing-geometry"},
            },
        )

        self.assertEqual(200, response.status_code)
        body = response.json()
        warning_codes = [item["code"] for item in body["warnings"]]
        self.assertIn("REGION_GEOMETRY_MISSING", warning_codes)
        self.assertTrue(any(item["sourceType"] == "MAP_REGION" for item in body["sourceHints"]))
```

- [ ] **Step 2: Run the backend test to verify it fails**

Run:

```bash
cd srmp-ai-orchestrator
python3 -m unittest tests.test_debug_plan_preview
```

Expected: FAIL because `action`, `sourceHints`, `warnings`, `label`, `readOnly`, and `writeRisk` are not yet returned by the plan endpoint.

- [ ] **Step 3: Commit the failing tests**

```bash
git add srmp-ai-orchestrator/tests/test_debug_plan_preview.py
git commit -m "test: define LangGraph plan preview contract"
```

---

## Task 2: Backend Plan Preview Response Fields

**Files:**
- Create: `srmp-ai-orchestrator/app/plan_preview.py`
- Modify: `srmp-ai-orchestrator/app/schemas.py`
- Modify: `srmp-ai-orchestrator/app/workflow.py`
- Test: `srmp-ai-orchestrator/tests/test_debug_plan_preview.py`

- [ ] **Step 1: Add the pure plan preview helper**

Create `srmp-ai-orchestrator/app/plan_preview.py`:

```python
from typing import Any, Dict, Iterable, List, Optional

from .schemas import MapAiAgentRequest, ToolCall


TOOL_LABELS = {
    "gis.queryNearbyObjects": "查询周边对象",
    "gis.queryRegionSummary": "查询区域统计",
    "gis.queryAssessmentResults": "查询评定结果",
    "gis.queryDiseases": "查询病害",
    "gis.queryDiseasesByStakeRange": "查询桩号范围病害",
    "knowledge.retrieve": "检索知识库",
    "template.match": "匹配方案模板",
    "solution.generateDraft": "生成方案草稿",
}

WRITE_TOOL_NAMES = {
    "solution.saveDraft",
    "solution.updateDraft",
    "solution.confirmDraft",
    "solution.archiveDraft",
}


def enrich_tool_plan(tool_plan: Iterable[ToolCall]) -> List[Dict[str, Any]]:
    result: List[Dict[str, Any]] = []
    for item in tool_plan or []:
        raw = item.model_dump(exclude_none=True) if hasattr(item, "model_dump") else dict(item)
        name = str(raw.get("toolName") or raw.get("name") or "")
        write_risk = is_write_tool(name)
        raw["label"] = tool_label(name)
        raw["readOnly"] = not write_risk
        raw["writeRisk"] = write_risk
        raw["argsSummary"] = summarize_args(raw.get("args") or {})
        result.append(raw)
    return result


def build_source_hints(request: MapAiAgentRequest, tool_plan: Iterable[Dict[str, Any]]) -> List[Dict[str, str]]:
    hints: List[Dict[str, str]] = []
    ctx = request.mapContext
    if ctx and ctx.mapObject:
        add_hint(hints, "MAP_OBJECT", "地图对象", "当前请求包含选中地图对象。")
    if ctx and (ctx.geometry or getattr(ctx, "regionGeometry", None) or ctx.regionSummary or str(ctx.mode or "").upper() == "REGION"):
        add_hint(hints, "MAP_REGION", "框选区域", "当前请求包含区域范围或区域统计摘要。")
    for item in tool_plan or []:
        name = str(item.get("toolName") or item.get("name") or "")
        if name.startswith("gis."):
            add_hint(hints, "BUSINESS_DATA", "业务数据", "计划会查询 GIS 业务图层、病害或评定结果。")
        if name == "knowledge.retrieve":
            add_hint(hints, "KNOWLEDGE", "知识库", "计划会检索养护规则、处置建议或评定说明。")
        if name in {"template.match", "solution.generateDraft"}:
            add_hint(hints, "TEMPLATE", "方案模板", "计划会匹配或使用方案模板生成结构化内容。")
    return hints


def build_plan_warnings(request: MapAiAgentRequest, tool_plan: Iterable[Dict[str, Any]]) -> List[Dict[str, str]]:
    warnings: List[Dict[str, str]] = []
    ctx = request.mapContext
    action = str(request.action or "").upper()
    mode = str(ctx.mode if ctx else "").upper()
    has_region_geometry = bool(ctx and (ctx.geometry or getattr(ctx, "regionGeometry", None)))
    has_region_summary = bool(ctx and ctx.regionSummary)
    has_map_object = bool(ctx and ctx.mapObject)

    if action in {"ANALYZE_REGION", "GENERATE_REGION_SOLUTION"} or mode == "REGION":
        if not has_region_geometry:
            warnings.append(
                {
                    "level": "WARN",
                    "code": "REGION_GEOMETRY_MISSING",
                    "message": "当前为区域分析，但计划请求未携带 geometry；实际执行可能无法精确查询空间范围。",
                }
            )
        if not has_region_summary:
            warnings.append(
                {
                    "level": "INFO",
                    "code": "REGION_SUMMARY_MISSING",
                    "message": "当前计划未携带区域统计摘要；执行时会依赖工具重新查询统计。",
                }
            )

    if action in {"ANALYZE_OBJECT", "GENERATE_OBJECT_SOLUTION"} and not has_map_object:
        warnings.append(
            {
                "level": "WARN",
                "code": "MAP_OBJECT_MISSING",
                "message": "当前为对象分析，但计划请求未携带 mapObject；请先在地图上选择对象。",
            }
        )

    if any(bool(item.get("writeRisk")) for item in tool_plan or []):
        warnings.append(
            {
                "level": "WARN",
                "code": "WRITE_TOOL_PLANNED",
                "message": "计划中包含写入型工具，执行前需要用户确认。",
            }
        )
    return warnings


def tool_label(name: str) -> str:
    return TOOL_LABELS.get(name, name or "未知工具")


def is_write_tool(name: str) -> bool:
    if name in WRITE_TOOL_NAMES:
        return True
    lowered = name.lower()
    return any(token in lowered for token in [".save", ".update", ".delete", ".confirm", ".archive", ".write"])


def summarize_args(args: Dict[str, Any]) -> Dict[str, Any]:
    keys = [
        "tenantId",
        "routeCode",
        "year",
        "mode",
        "limit",
        "topK",
        "intent",
        "solutionType",
        "stakeStart",
        "stakeEnd",
    ]
    summary = {key: args.get(key) for key in keys if args.get(key) not in (None, "")}
    geometry = args.get("geometry")
    if isinstance(geometry, dict):
        summary["geometryType"] = geometry.get("type") or "Geometry"
    region_summary = args.get("regionSummary")
    if isinstance(region_summary, dict):
        summary["regionSummary"] = summarize_region(region_summary)
    return summary


def summarize_region(region: Dict[str, Any]) -> Dict[str, Any]:
    disease_summary = region.get("diseaseSummary") or {}
    return {
        key: value
        for key, value in {
            "routeCount": first(region, "routeCount", "route_count"),
            "sectionCount": first(region, "sectionCount", "section_count"),
            "diseaseCount": first(region, "diseaseCount", "disease_count") or first(disease_summary, "disease_count", "diseaseCount"),
            "assessmentCount": first(region, "assessmentCount", "assessment_count"),
        }.items()
        if value not in (None, "")
    }


def first(data: Optional[Dict[str, Any]], *keys: str) -> Any:
    if not isinstance(data, dict):
        return None
    for key in keys:
        value = data.get(key)
        if value not in (None, ""):
            return value
    return None


def add_hint(hints: List[Dict[str, str]], source_type: str, label: str, reason: str) -> None:
    if any(item.get("sourceType") == source_type for item in hints):
        return
    hints.append({"sourceType": source_type, "label": label, "reason": reason})
```

- [ ] **Step 2: Add typed action fields to the request schema**

In `srmp-ai-orchestrator/app/schemas.py`, update `MapAiAgentRequest`:

```python
class MapAiAgentRequest(BaseModel):
    # 兼容 Java MapAiAgentRequest、前端旧版 context/mapObject 直传，以及后续调试字段。
    model_config = ConfigDict(extra="allow")

    message: Optional[str] = None
    action: Optional[str] = None
    mapContext: Optional[MapAiContext] = None
    context: Optional[Dict[str, Any]] = None
    mapObject: Optional[Dict[str, Any]] = None
    actionInput: Dict[str, Any] = Field(default_factory=dict)
    options: Dict[str, Any] = Field(default_factory=dict)
```

- [ ] **Step 3: Wire helpers into `LangGraphWorkflow.plan()`**

In `srmp-ai-orchestrator/app/workflow.py`, add this import near the other local imports:

```python
from .plan_preview import build_plan_warnings, build_source_hints, enrich_tool_plan
```

Replace the return block in `LangGraphWorkflow.plan()` with:

```python
        raw_tool_plan = final_state.get("tool_plan", [])
        enriched_tool_plan = enrich_tool_plan(raw_tool_plan)
        return {
            "traceId": final_state.get("trace_id"),
            "strategy": strategy_metadata(),
            "action": final_state["request"].action,
            "intent": final_state.get("intent"),
            "contextSummary": final_state.get("context_summary", {}),
            "normalizedRequest": final_state["request"].model_dump(exclude_none=True),
            "toolPlan": enriched_tool_plan,
            "sourceHints": build_source_hints(final_state["request"], enriched_tool_plan),
            "warnings": build_plan_warnings(final_state["request"], enriched_tool_plan),
            "steps": final_state.get("steps", []),
        }
```

- [ ] **Step 4: Run the backend tests**

Run:

```bash
cd srmp-ai-orchestrator
python3 -m unittest tests.test_debug_plan_preview
```

Expected: PASS.

- [ ] **Step 5: Run the existing live trace tests**

Run:

```bash
cd srmp-ai-orchestrator
python3 -m unittest discover -s tests -p 'test_live_trace*.py'
```

Expected: PASS.

- [ ] **Step 6: Commit backend contract implementation**

```bash
git add srmp-ai-orchestrator/app/plan_preview.py srmp-ai-orchestrator/app/schemas.py srmp-ai-orchestrator/app/workflow.py
git commit -m "feat: enrich LangGraph plan preview contract"
```

---

## Task 3: Frontend Plan Preview Normalizer

**Files:**
- Create: `srmp-web-ui/tests/mapAiPlanPreview.test.mjs`
- Create: `srmp-web-ui/src/utils/mapAiPlanPreview.ts`

- [ ] **Step 1: Write frontend utility tests**

Create `srmp-web-ui/tests/mapAiPlanPreview.test.mjs`:

```js
import test from 'node:test'
import assert from 'node:assert/strict'
import {
  buildPlanWarnings,
  deriveSourceHints,
  normalizeMapAiPlanResponse,
  summarizePlanContext
} from '../src/utils/mapAiPlanPreview.ts'

test('normalizes Java wrapped region plan preview', () => {
  const plan = normalizeMapAiPlanResponse({
    code: 0,
    data: {
      action: 'GENERATE_REGION_SOLUTION',
      intent: 'REGION_ANALYSIS',
      contextSummary: { mode: 'REGION', routeCode: 'G210', year: 2026 },
      toolPlan: [
        {
          toolName: 'gis.queryRegionSummary',
          label: '查询区域统计',
          reason: '查询框选区域或路线统计摘要',
          readOnly: true,
          writeRisk: false,
          args: { routeCode: 'G210', year: 2026, geometry: { type: 'Polygon' } }
        },
        {
          toolName: 'knowledge.retrieve',
          reason: '知识库检索处置规则',
          args: { query: '区域养护', topK: 5 }
        }
      ],
      sourceHints: [{ sourceType: 'MAP_REGION', label: '框选区域', reason: '当前请求包含区域范围。' }],
      warnings: []
    }
  })

  assert.equal(plan.status, 'SUCCESS')
  assert.equal(plan.action, 'GENERATE_REGION_SOLUTION')
  assert.equal(plan.intent, 'REGION_ANALYSIS')
  assert.equal(plan.toolPlan.length, 2)
  assert.equal(plan.toolPlan[0].label, '查询区域统计')
  assert.equal(plan.toolPlan[0].argsSummary.geometryType, 'Polygon')
  assert.equal(plan.toolPlan[1].label, '知识库检索')
  assert.equal(plan.sourceHints.some((item) => item.sourceType === 'BUSINESS_DATA'), true)
  assert.equal(plan.sourceHints.some((item) => item.sourceType === 'KNOWLEDGE'), true)
  assert.equal(plan.sourceHints.some((item) => item.sourceType === 'MAP_REGION'), true)
})

test('derives write warning from planned write tool', () => {
  const warnings = buildPlanWarnings(
    {
      action: 'SAVE_SOLUTION_DRAFT',
      mapContext: { mode: 'OBJECT', mapObject: { objectType: 'DISEASE' } }
    },
    [{ name: 'solution.saveDraft', writeRisk: true }]
  )

  assert.equal(warnings.some((item) => item.code === 'WRITE_TOOL_PLANNED'), true)
})

test('warns when region geometry is missing', () => {
  const plan = normalizeMapAiPlanResponse({
    action: 'ANALYZE_REGION',
    intent: 'REGION_ANALYSIS',
    normalizedRequest: {
      action: 'ANALYZE_REGION',
      mapContext: { mode: 'REGION', routeCode: 'G210', year: 2026, regionSummary: { routeCount: 1 } }
    },
    toolPlan: [{ toolName: 'gis.queryRegionSummary', reason: '查询区域统计', args: { routeCode: 'G210' } }]
  })

  assert.equal(plan.warnings.some((item) => item.code === 'REGION_GEOMETRY_MISSING'), true)
})

test('summarizes object and region context', () => {
  assert.deepEqual(
    summarizePlanContext({
      contextSummary: { routeCode: 'G210', year: 2026 },
      normalizedRequest: { mapContext: { mode: 'OBJECT', mapObject: { objectType: 'DISEASE' } } }
    }).slice(0, 3),
    ['对象模式', '路线 G210', '年度 2026']
  )

  assert.equal(
    deriveSourceHints({
      normalizedRequest: { mapContext: { mode: 'REGION', geometry: { type: 'Polygon' } } },
      toolPlan: [{ name: 'template.match' }]
    }).some((item) => item.sourceType === 'TEMPLATE'),
    true
  )
})
```

- [ ] **Step 2: Run the frontend utility test to verify it fails**

Run:

```bash
cd srmp-web-ui
node --no-warnings --test tests/mapAiPlanPreview.test.mjs
```

Expected: FAIL because `src/utils/mapAiPlanPreview.ts` does not exist.

- [ ] **Step 3: Implement the normalizer utility**

Create `srmp-web-ui/src/utils/mapAiPlanPreview.ts`:

```ts
export interface MapAiSourceHint {
  sourceType: string
  label: string
  reason?: string
}

export interface MapAiPlanWarning {
  level: 'INFO' | 'WARN' | 'ERROR' | string
  code: string
  message: string
}

export interface MapAiPlannedTool {
  name: string
  label: string
  reason?: string
  readOnly: boolean
  writeRisk: boolean
  argsSummary: Record<string, any>
  raw: Record<string, any>
}

export interface MapAiPlanStep {
  name: string
  label: string
  status?: string
  elapsedMs?: number
  raw: Record<string, any>
}

export interface MapAiPlanPreview {
  status: 'SUCCESS' | 'FAILED'
  action?: string
  intent?: string
  traceId?: string
  contextSummary: Record<string, any>
  contextChips: string[]
  toolPlan: MapAiPlannedTool[]
  steps: MapAiPlanStep[]
  warnings: MapAiPlanWarning[]
  sourceHints: MapAiSourceHint[]
  normalizedRequest: Record<string, any>
  raw: Record<string, any>
  error?: string
}

const TOOL_LABELS: Record<string, string> = {
  'gis.queryNearbyObjects': '查询周边对象',
  'gis.queryRegionSummary': '查询区域统计',
  'gis.queryAssessmentResults': '查询评定结果',
  'gis.queryDiseases': '查询病害',
  'gis.queryDiseasesByStakeRange': '查询桩号范围病害',
  'knowledge.retrieve': '知识库检索',
  'template.match': '匹配方案模板',
  'solution.generateDraft': '生成方案草稿',
  'solution.saveDraft': '保存方案草稿'
}

export function normalizeMapAiPlanResponse(input: any): MapAiPlanPreview {
  const raw = unwrapPlanPayload(input)
  const normalizedRequest = objectValue(raw.normalizedRequest || raw.request || input?.request)
  const toolPlan = normalizeToolPlan(raw.toolPlan || raw.tools || [])
  const basePlan = {
    action: stringValue(raw.action || normalizedRequest.action),
    intent: stringValue(raw.intent),
    contextSummary: objectValue(raw.contextSummary),
    normalizedRequest,
    toolPlan
  }
  const warnings = mergeWarnings(normalizeWarnings(raw.warnings), buildPlanWarnings(basePlan.normalizedRequest, toolPlan))
  const sourceHints = mergeSourceHints(normalizeSourceHints(raw.sourceHints), deriveSourceHints(basePlan))
  return {
    status: raw.error || raw.status === 'FAILED' ? 'FAILED' : 'SUCCESS',
    action: basePlan.action,
    intent: basePlan.intent,
    traceId: stringValue(raw.traceId || raw.trace_id),
    contextSummary: basePlan.contextSummary,
    contextChips: summarizePlanContext(basePlan),
    toolPlan,
    steps: normalizeSteps(raw.steps || []),
    warnings,
    sourceHints,
    normalizedRequest,
    raw,
    error: stringValue(raw.error || raw.errorMessage || raw.message)
  }
}

export function summarizePlanContext(plan: Partial<MapAiPlanPreview> | Record<string, any>): string[] {
  const request = objectValue((plan as any).normalizedRequest || (plan as any).request)
  const ctx = objectValue(request.mapContext || (plan as any).mapContext)
  const summary = objectValue((plan as any).contextSummary)
  const mode = stringValue(ctx.mode || summary.mode || request.mode)
  const route = stringValue(ctx.routeCode || summary.routeCode || request.routeCode)
  const year = stringValue(ctx.year || summary.year || request.year)
  const chips: string[] = []
  if (mode === 'OBJECT') chips.push('对象模式')
  else if (mode === 'REGION') chips.push('区域模式')
  else if (mode === 'ROUTE') chips.push('路线模式')
  else if (mode) chips.push(`${mode} 模式`)
  if (route) chips.push(`路线 ${route}`)
  if (year) chips.push(`年度 ${year}`)
  if (ctx.mapObject) chips.push('已含地图对象')
  if (ctx.geometry || ctx.regionGeometry) chips.push(`几何 ${ctx.geometry?.type || ctx.regionGeometry?.type || 'Geometry'}`)
  if (ctx.regionSummary) chips.push('已含区域摘要')
  return chips
}

export function deriveSourceHints(plan: Partial<MapAiPlanPreview> | Record<string, any>): MapAiSourceHint[] {
  const request = objectValue((plan as any).normalizedRequest || (plan as any).request)
  const ctx = objectValue(request.mapContext || (plan as any).mapContext)
  const tools = ((plan as any).toolPlan || []) as Array<Partial<MapAiPlannedTool> | Record<string, any>>
  const hints: MapAiSourceHint[] = []
  if (ctx.mapObject) addSourceHint(hints, 'MAP_OBJECT', '地图对象', '当前请求包含选中地图对象。')
  if (ctx.geometry || ctx.regionGeometry || ctx.regionSummary || String(ctx.mode || '').toUpperCase() === 'REGION') {
    addSourceHint(hints, 'MAP_REGION', '框选区域', '当前请求包含区域范围或区域统计。')
  }
  tools.forEach((tool) => {
    const name = stringValue((tool as any).name || (tool as any).toolName) || ''
    if (name.startsWith('gis.')) addSourceHint(hints, 'BUSINESS_DATA', '业务数据', '计划会查询 GIS 业务图层、病害或评定结果。')
    if (name === 'knowledge.retrieve') addSourceHint(hints, 'KNOWLEDGE', '知识库', '计划会检索养护知识、规则或处置建议。')
    if (name === 'template.match' || name === 'solution.generateDraft') addSourceHint(hints, 'TEMPLATE', '方案模板', '计划会匹配或使用方案模板。')
  })
  return hints
}

export function buildPlanWarnings(requestLike: Record<string, any>, tools: Array<Partial<MapAiPlannedTool> | Record<string, any>>): MapAiPlanWarning[] {
  const request = objectValue(requestLike)
  const ctx = objectValue(request.mapContext)
  const action = String(request.action || '').toUpperCase()
  const mode = String(ctx.mode || '').toUpperCase()
  const warnings: MapAiPlanWarning[] = []
  if (action === 'ANALYZE_REGION' || action === 'GENERATE_REGION_SOLUTION' || mode === 'REGION') {
    if (!ctx.geometry && !ctx.regionGeometry) {
      warnings.push({ level: 'WARN', code: 'REGION_GEOMETRY_MISSING', message: '当前为区域分析，但未携带区域几何。' })
    }
  }
  if ((action === 'ANALYZE_OBJECT' || action === 'GENERATE_OBJECT_SOLUTION') && !ctx.mapObject) {
    warnings.push({ level: 'WARN', code: 'MAP_OBJECT_MISSING', message: '当前为对象分析，但未携带地图对象。' })
  }
  if (tools.some((tool) => Boolean((tool as any).writeRisk))) {
    warnings.push({ level: 'WARN', code: 'WRITE_TOOL_PLANNED', message: '计划中包含写入型工具，执行前需要确认。' })
  }
  return warnings
}

function normalizeToolPlan(items: any[]): MapAiPlannedTool[] {
  return Array.isArray(items)
    ? items.map((item) => {
        const raw = objectValue(item)
        const name = stringValue(raw.toolName || raw.name) || ''
        const writeRisk = raw.writeRisk === true || isWriteTool(name)
        return {
          name,
          label: stringValue(raw.label) || TOOL_LABELS[name] || name || '未知工具',
          reason: stringValue(raw.reason),
          readOnly: raw.readOnly === false ? false : !writeRisk,
          writeRisk,
          argsSummary: objectValue(raw.argsSummary || summarizeArgs(raw.args || {})),
          raw
        }
      })
    : []
}

function normalizeSteps(items: any[]): MapAiPlanStep[] {
  return Array.isArray(items)
    ? items.map((item) => {
        const raw = objectValue(item)
        const name = stringValue(raw.name || raw.node || raw.step_name) || ''
        return {
          name,
          label: stringValue(raw.label || raw.message || raw.step_label || name) || '计划步骤',
          status: stringValue(raw.status),
          elapsedMs: numberValue(raw.elapsedMs ?? raw.costMs ?? raw.cost_ms),
          raw
        }
      })
    : []
}

function normalizeWarnings(items: any[]): MapAiPlanWarning[] {
  return Array.isArray(items)
    ? items.map((item) => {
        if (typeof item === 'string') return { level: 'WARN', code: item, message: item }
        const raw = objectValue(item)
        return {
          level: stringValue(raw.level) || 'WARN',
          code: stringValue(raw.code) || stringValue(raw.message) || 'PLAN_WARNING',
          message: stringValue(raw.message) || stringValue(raw.code) || '计划存在风险提示。'
        }
      })
    : []
}

function normalizeSourceHints(items: any[]): MapAiSourceHint[] {
  return Array.isArray(items)
    ? items.map((item) => {
        const raw = objectValue(item)
        return {
          sourceType: stringValue(raw.sourceType || raw.type) || 'UNKNOWN',
          label: stringValue(raw.label || raw.sourceTitle || raw.sourceType || raw.type) || '来源',
          reason: stringValue(raw.reason || raw.contentExcerpt)
        }
      })
    : []
}

function mergeWarnings(primary: MapAiPlanWarning[], derived: MapAiPlanWarning[]): MapAiPlanWarning[] {
  const result = [...primary]
  derived.forEach((item) => {
    if (!result.some((existing) => existing.code === item.code)) result.push(item)
  })
  return result
}

function mergeSourceHints(primary: MapAiSourceHint[], derived: MapAiSourceHint[]): MapAiSourceHint[] {
  const result = [...primary]
  derived.forEach((item) => addSourceHint(result, item.sourceType, item.label, item.reason || ''))
  return result
}

function summarizeArgs(args: Record<string, any>): Record<string, any> {
  const raw = objectValue(args)
  const summary: Record<string, any> = {}
  ;['tenantId', 'routeCode', 'year', 'mode', 'limit', 'topK', 'intent', 'solutionType', 'stakeStart', 'stakeEnd'].forEach((key) => {
    if (raw[key] !== undefined && raw[key] !== null && raw[key] !== '') summary[key] = raw[key]
  })
  if (raw.geometry && typeof raw.geometry === 'object') summary.geometryType = raw.geometry.type || 'Geometry'
  if (raw.regionSummary && typeof raw.regionSummary === 'object') summary.regionSummary = true
  return summary
}

function unwrapPlanPayload(input: any): Record<string, any> {
  if (input?.code === 0 && input.data && typeof input.data === 'object') return input.data
  if (input?.data?.body && typeof input.data.body === 'object') return input.data.body
  if (input?.body && typeof input.body === 'object') return input.body
  if (input?.data && typeof input.data === 'object' && (input.data.toolPlan || input.data.intent)) return input.data
  return objectValue(input)
}

function addSourceHint(hints: MapAiSourceHint[], sourceType: string, label: string, reason: string): void {
  if (hints.some((item) => item.sourceType === sourceType)) return
  hints.push({ sourceType, label, reason })
}

function isWriteTool(name: string): boolean {
  const lowered = String(name || '').toLowerCase()
  return ['.save', '.update', '.delete', '.confirm', '.archive', '.write'].some((token) => lowered.includes(token))
}

function objectValue(value: any): Record<string, any> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value : {}
}

function stringValue(value: any): string | undefined {
  if (value === undefined || value === null || value === '') return undefined
  return String(value)
}

function numberValue(value: any): number | undefined {
  const num = Number(value)
  return Number.isFinite(num) ? num : undefined
}
```

- [ ] **Step 4: Run the frontend utility test**

Run:

```bash
cd srmp-web-ui
node --no-warnings --test tests/mapAiPlanPreview.test.mjs
```

Expected: PASS.

- [ ] **Step 5: Commit frontend utility**

```bash
git add srmp-web-ui/tests/mapAiPlanPreview.test.mjs srmp-web-ui/src/utils/mapAiPlanPreview.ts
git commit -m "feat: add one-map AI plan preview normalizer"
```

---

## Task 4: Plan Preview Drawer Component

**Files:**
- Create: `srmp-web-ui/src/views/gis/components/MapAiPlanPreviewDrawer.vue`
- Modify: `srmp-web-ui/tests/mapAiPlanPreview.test.mjs`

- [ ] **Step 1: Extend the utility test with drawer-facing labels**

Append this test to `srmp-web-ui/tests/mapAiPlanPreview.test.mjs`:

```js
test('keeps plan data display-safe when fields are missing', () => {
  const plan = normalizeMapAiPlanResponse({
    intent: 'GENERAL_CHAT',
    toolPlan: [{ toolName: 'unknown.tool', args: { routeCode: 'G210' } }],
    steps: [{ name: 'intent_recognize', status: 'SUCCESS' }]
  })

  assert.equal(plan.status, 'SUCCESS')
  assert.equal(plan.toolPlan[0].label, 'unknown.tool')
  assert.equal(plan.toolPlan[0].readOnly, true)
  assert.equal(plan.steps[0].label, 'intent_recognize')
  assert.deepEqual(plan.sourceHints, [])
})
```

- [ ] **Step 2: Run the utility test**

Run:

```bash
cd srmp-web-ui
node --no-warnings --test tests/mapAiPlanPreview.test.mjs
```

Expected: PASS. This locks the data shape the drawer consumes.

- [ ] **Step 3: Create the drawer component**

Create `srmp-web-ui/src/views/gis/components/MapAiPlanPreviewDrawer.vue`:

```vue
<template>
  <el-drawer
    :model-value="visible"
    title="AI 执行计划"
    size="420px"
    append-to-body
    class="map-ai-plan-drawer"
    @update:model-value="$emit('update:visible', $event)"
    @close="$emit('close')"
  >
    <div class="plan-drawer-body">
      <el-alert
        v-if="error"
        type="error"
        show-icon
        :closable="false"
        :title="error"
      />

      <section v-if="loading" class="plan-loading">
        <el-skeleton animated :rows="5" />
      </section>

      <template v-else-if="plan">
        <section class="plan-section">
          <div class="plan-section-head">
            <strong>识别结果</strong>
            <el-tag size="small" :type="plan.status === 'SUCCESS' ? 'success' : 'danger'">{{ plan.status }}</el-tag>
          </div>
          <div class="plan-tags">
            <el-tag v-if="plan.action" size="small" effect="plain">{{ plan.action }}</el-tag>
            <el-tag v-if="plan.intent" size="small" type="info" effect="plain">{{ plan.intent }}</el-tag>
            <el-tag v-if="plan.traceId" size="small" type="info" effect="plain">{{ plan.traceId }}</el-tag>
          </div>
          <div v-if="plan.contextChips.length" class="plan-context">
            <span v-for="item in plan.contextChips" :key="item">{{ item }}</span>
          </div>
        </section>

        <section v-if="plan.warnings.length" class="plan-section">
          <div class="plan-section-head">
            <strong>风险提示</strong>
            <span>{{ plan.warnings.length }}</span>
          </div>
          <div class="warning-list">
            <div v-for="item in plan.warnings" :key="item.code" class="warning-item">
              <el-tag size="small" :type="item.level === 'ERROR' ? 'danger' : item.level === 'INFO' ? 'info' : 'warning'">
                {{ item.level }}
              </el-tag>
              <span>{{ item.message }}</span>
            </div>
          </div>
        </section>

        <section class="plan-section">
          <div class="plan-section-head">
            <strong>计划工具</strong>
            <span>{{ plan.toolPlan.length }}</span>
          </div>
          <div v-if="plan.toolPlan.length" class="tool-list">
            <div v-for="tool in plan.toolPlan" :key="tool.name + tool.reason" class="tool-item">
              <div class="tool-title">
                <strong>{{ tool.label }}</strong>
                <el-tag size="small" :type="tool.writeRisk ? 'warning' : 'success'" effect="plain">
                  {{ tool.writeRisk ? '写入' : '只读' }}
                </el-tag>
              </div>
              <p v-if="tool.reason">{{ tool.reason }}</p>
              <div v-if="Object.keys(tool.argsSummary).length" class="arg-grid">
                <span v-for="(value, key) in tool.argsSummary" :key="key">
                  <em>{{ key }}</em>
                  <strong>{{ displayValue(value) }}</strong>
                </span>
              </div>
            </div>
          </div>
          <el-empty v-else description="未规划工具" :image-size="72" />
        </section>

        <section v-if="plan.sourceHints.length" class="plan-section">
          <div class="plan-section-head">
            <strong>预计来源</strong>
            <span>{{ plan.sourceHints.length }}</span>
          </div>
          <div class="source-list">
            <div v-for="item in plan.sourceHints" :key="item.sourceType" class="source-item">
              <el-tag size="small" effect="plain">{{ item.label }}</el-tag>
              <span>{{ item.reason }}</span>
            </div>
          </div>
        </section>

        <section v-if="plan.steps.length" class="plan-section">
          <div class="plan-section-head">
            <strong>规划步骤</strong>
            <span>{{ plan.steps.length }}</span>
          </div>
          <div class="step-list">
            <div v-for="step in plan.steps" :key="step.name + step.label" class="step-item">
              <span>{{ step.label }}</span>
              <el-tag v-if="step.status" size="small" effect="plain">{{ step.status }}</el-tag>
            </div>
          </div>
        </section>
      </template>

      <el-empty v-else description="暂无执行计划" :image-size="96" />
    </div>

    <template #footer>
      <div class="plan-footer">
        <el-button :disabled="loading" @click="$emit('refresh')">刷新计划</el-button>
        <el-button type="primary" :disabled="loading || !plan" @click="$emit('execute')">按计划执行</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import type { MapAiPlanPreview } from '../../../utils/mapAiPlanPreview'

defineProps<{
  visible: boolean
  loading?: boolean
  plan?: MapAiPlanPreview | null
  error?: string
}>()

defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'close'): void
  (e: 'refresh'): void
  (e: 'execute'): void
}>()

function displayValue(value: any) {
  if (value === true) return '是'
  if (value === false) return '否'
  if (value === undefined || value === null || value === '') return '-'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}
</script>

<style scoped>
.plan-drawer-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.plan-section {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px;
  background: #fff;
}

.plan-section-head,
.tool-title,
.step-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.plan-tags,
.plan-context,
.source-list,
.warning-list,
.step-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.plan-context span {
  padding: 3px 8px;
  border-radius: 6px;
  background: #f3f4f6;
  color: #374151;
  font-size: 12px;
}

.tool-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 10px;
}

.tool-item {
  border: 1px solid #eef2f7;
  border-radius: 6px;
  padding: 10px;
  background: #fafafa;
}

.tool-item p {
  margin: 6px 0 0;
  color: #6b7280;
  font-size: 12px;
}

.arg-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
  margin-top: 8px;
}

.arg-grid span {
  min-width: 0;
  border-radius: 6px;
  background: #fff;
  padding: 6px;
}

.arg-grid em {
  display: block;
  color: #6b7280;
  font-size: 11px;
  font-style: normal;
}

.arg-grid strong {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #111827;
  font-size: 12px;
}

.warning-item,
.source-item {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  width: 100%;
  color: #4b5563;
  font-size: 12px;
}

.plan-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
```

- [ ] **Step 4: Run the frontend utility test again**

Run:

```bash
cd srmp-web-ui
node --no-warnings --test tests/mapAiPlanPreview.test.mjs
```

Expected: PASS.

- [ ] **Step 5: Commit the drawer component**

```bash
git add srmp-web-ui/src/views/gis/components/MapAiPlanPreviewDrawer.vue srmp-web-ui/tests/mapAiPlanPreview.test.mjs
git commit -m "feat: add one-map AI plan preview drawer"
```

---

## Task 5: Wire Plan Preview Into OneMap AI Assistant

**Files:**
- Modify: `srmp-web-ui/src/views/gis/components/map-ai/MapAiSuggestedActions.vue`
- Modify: `srmp-web-ui/src/views/gis/components/AgentChatFloat.vue`
- Test: `srmp-web-ui/tests/mapAiPlanPreview.test.mjs`

- [ ] **Step 1: Add suggested-action plan events**

Replace `srmp-web-ui/src/views/gis/components/map-ai/MapAiSuggestedActions.vue` with:

```vue
<template>
  <div v-if="normalizedActions.length" class="map-ai-suggested-actions">
    <span v-for="item in normalizedActions" :key="item.action + item.label" class="suggested-action-item">
      <el-button
        size="small"
        :type="item.requiresConfirmation ? 'warning' : 'primary'"
        plain
        :disabled="item.disabled"
        @click="$emit('run-action', item)"
      >
        {{ item.label }}
      </el-button>
      <el-button
        v-if="isHeavyAction(item.action)"
        size="small"
        text
        :disabled="item.disabled"
        @click="$emit('preview-plan', item)"
      >
        计划
      </el-button>
    </span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { MapAgentAction, MapAgentSuggestedAction } from '../../../../api/agent'

const props = defineProps<{ actions?: MapAgentSuggestedAction[] }>()
defineEmits<{
  (e: 'run-action', action: MapAgentSuggestedAction): void
  (e: 'preview-plan', action: MapAgentSuggestedAction): void
}>()

const normalizedActions = computed(() => props.actions || [])

function isHeavyAction(action: MapAgentAction | string) {
  return ['GENERATE_OBJECT_SOLUTION', 'GENERATE_REGION_SOLUTION', 'GENERATE_ROUTE_REPORT', 'SAVE_SOLUTION_DRAFT'].includes(String(action))
}
</script>

<style scoped>
.map-ai-suggested-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 0;
}

.suggested-action-item {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}
</style>
```

- [ ] **Step 2: Add imports to `AgentChatFloat.vue`**

In `srmp-web-ui/src/views/gis/components/AgentChatFloat.vue`, replace:

```ts
import { mapAgentRun, type MapAgentActionResult, type MapAgentRunResponse, type MapAgentSuggestedAction } from '../../../api/agent'
```

with:

```ts
import { mapAgentRun, type MapAgentAction, type MapAgentActionResult, type MapAgentRunRequest, type MapAgentRunResponse, type MapAgentSuggestedAction } from '../../../api/agent'
```

Replace:

```ts
import { getOrchestratorLiveTrace, getOrchestratorQuickDiagnostics } from '../../../api/orchestrator'
```

with:

```ts
import { getOrchestratorLiveTrace, getOrchestratorQuickDiagnostics, runOrchestratorPlan } from '../../../api/orchestrator'
```

Add component and utility imports near the existing component imports:

```ts
import MapAiPlanPreviewDrawer from './MapAiPlanPreviewDrawer.vue'
import { normalizeMapAiPlanResponse, type MapAiPlanPreview } from '../../../utils/mapAiPlanPreview'
```

- [ ] **Step 3: Add plan drawer to the template**

In `AgentChatFloat.vue`, inside `.analysis-title-actions`, after the `状态诊断` button, add:

```vue
            <el-button size="small" text :loading="planLoading" @click="previewCurrentPlan()">执行计划</el-button>
```

Inside the object action template, after the primary solution button, add:

```vue
            <el-button
              v-if="primarySolutionAction"
              size="small"
              plain
              :disabled="!activeMapObject || solutionLoading || loading"
              :loading="planLoading && planExecutionSnapshot?.action === 'GENERATE_OBJECT_SOLUTION'"
              @click="previewObjectSolutionPlan(primarySolutionAction.type)"
            >方案计划</el-button>
```

Inside the region action template, after the `生成区域建议` button, add:

```vue
            <el-button
              size="small"
              plain
              :disabled="!activeRegionContext || regionBusy"
              :loading="planLoading && planExecutionSnapshot?.action === 'GENERATE_REGION_SOLUTION'"
              @click="previewRegionSolutionPlan"
            >方案计划</el-button>
```

Replace the existing `MapAiSuggestedActions` line:

```vue
      <MapAiSuggestedActions :actions="latestSuggestedActions" @run-action="runSuggestedAction" />
```

with:

```vue
      <MapAiSuggestedActions
        :actions="latestSuggestedActions"
        @run-action="runSuggestedAction"
        @preview-plan="previewSuggestedActionPlan"
      />
```

After `AiTraceDrawer`, add:

```vue
  <MapAiPlanPreviewDrawer
    v-model:visible="planDrawerVisible"
    :loading="planLoading"
    :plan="planPreview"
    :error="planError"
    @refresh="refreshPlanPreview"
    @execute="executePlanPreview"
  />
```

- [ ] **Step 4: Add plan state and snapshot types**

In the script section after `let liveTraceTimer`, add:

```ts
type PlanExecutionKind = 'SEND' | 'OBJECT_SOLUTION' | 'REGION_SOLUTION' | 'ROUTE_REPORT' | 'SUGGESTED_ACTION'

interface PlanExecutionSnapshot {
  kind: PlanExecutionKind
  action: MapAgentAction
  message: string
  request: MapAgentRunRequest
  solutionType?: MapObjectSolutionType
  suggestedAction?: MapAgentSuggestedAction
}

const planDrawerVisible = ref(false)
const planLoading = ref(false)
const planError = ref('')
const planPreview = ref<MapAiPlanPreview | null>(null)
const planExecutionSnapshot = ref<PlanExecutionSnapshot | null>(null)
```

- [ ] **Step 5: Add plan request builders**

In `AgentChatFloat.vue`, after `findWeakSections()`, add:

```ts
function defaultPlanMessage() {
  const text = input.value.trim()
  if (text) return text
  if (contextMode.value === 'REGION') return '综合分析当前区域内线路、路段、评定单元、病害和评定结果'
  if (contextMode.value === 'OBJECT') return '分析当前地图选中对象，说明主要问题、成因判断和养护建议'
  if (contextMode.value === 'ROUTE') return '分析当前路线整体路况和养护重点'
  return '基于当前地图上下文回答问题'
}

function buildPlanRequest(action: MapAgentAction, message: string, actionInput: Record<string, any> = {}): MapAgentRunRequest {
  const mapContext = buildMapAiContext(message)
  if (mapContext.mode === 'REGION') {
    mapContext.geometry = mapContext.geometry || mapContext.regionGeometry || mapContext.region?.geometry || props.context?.regionGeometry || null
  }
  return {
    action,
    message,
    mapContext,
    actionInput,
    options: { ...options, useTools: useAgentTools.value, traceId: createWebTraceId() }
  }
}

function buildCurrentPlanSnapshot(): PlanExecutionSnapshot {
  const action = contextMode.value === 'REGION' ? 'ANALYZE_REGION' : contextMode.value === 'OBJECT' ? 'ANALYZE_OBJECT' : 'CHAT'
  const message = defaultPlanMessage()
  return {
    kind: 'SEND',
    action,
    message,
    request: buildPlanRequest(action, message, { mapObject: activeMapObject.value })
  }
}

function buildObjectSolutionPlanSnapshot(solutionType: MapObjectSolutionType): PlanExecutionSnapshot {
  const obj: any = activeMapObject.value
  const query = props.context?.query || {}
  const message = '生成当前对象的结构化养护建议'
  return {
    kind: solutionType === 'ROUTE_REPORT' ? 'ROUTE_REPORT' : 'OBJECT_SOLUTION',
    action: solutionType === 'ROUTE_REPORT' ? 'GENERATE_ROUTE_REPORT' : 'GENERATE_OBJECT_SOLUTION',
    message,
    solutionType,
    request: buildPlanRequest(solutionType === 'ROUTE_REPORT' ? 'GENERATE_ROUTE_REPORT' : 'GENERATE_OBJECT_SOLUTION', message, {
      objectType: normalizeObjectType(obj),
      objectId: String(obj?.objectId || obj?.object_id || obj?.id || obj?.featureId || ''),
      routeCode: String(obj?.routeCode || obj?.route_code || query.routeCode || ''),
      year: normalizeYear(obj?.year || query.year),
      solutionType,
      mapObject: obj
    })
  }
}

function buildRegionSolutionPlanSnapshot(): PlanExecutionSnapshot {
  const message = '生成框选区域养护建议'
  return {
    kind: 'REGION_SOLUTION',
    action: 'GENERATE_REGION_SOLUTION',
    message,
    request: buildPlanRequest('GENERATE_REGION_SOLUTION', message, {
      solutionType: 'REGION_MAINTENANCE_SUGGESTION'
    })
  }
}
```

- [ ] **Step 6: Add plan API actions**

After the functions from the previous step, add:

```ts
async function openPlanPreview(snapshot: PlanExecutionSnapshot) {
  planExecutionSnapshot.value = snapshot
  planDrawerVisible.value = true
  planLoading.value = true
  planError.value = ''
  try {
    const result = await runOrchestratorPlan(snapshot.request as any)
    planPreview.value = normalizeMapAiPlanResponse(result)
  } catch (error: any) {
    planPreview.value = null
    planError.value = error?.message || '执行计划生成失败'
  } finally {
    planLoading.value = false
  }
}

function previewCurrentPlan() {
  openPlanPreview(buildCurrentPlanSnapshot())
}

function previewObjectSolutionPlan(solutionType: MapObjectSolutionType) {
  if (!activeMapObject.value) {
    ElMessage.warning('请先在地图上选择一个对象')
    return
  }
  openPlanPreview(buildObjectSolutionPlanSnapshot(solutionType))
}

function previewRegionSolutionPlan() {
  if (!activeRegionContext.value) {
    ElMessage.warning('请先框选一个区域')
    return
  }
  openPlanPreview(buildRegionSolutionPlanSnapshot())
}

function previewSuggestedActionPlan(action: MapAgentSuggestedAction) {
  if (action.action === 'GENERATE_REGION_SOLUTION') {
    previewRegionSolutionPlan()
    return
  }
  if (action.action === 'GENERATE_ROUTE_REPORT') {
    openPlanPreview(buildObjectSolutionPlanSnapshot('ROUTE_REPORT'))
    return
  }
  if (action.action === 'GENERATE_OBJECT_SOLUTION') {
    const primary = solutionActions.value.find((it: any) => it.primary) || solutionActions.value[0]
    if (primary) previewObjectSolutionPlan(primary.type)
    return
  }
  const message = action.label || action.action
  openPlanPreview({
    kind: 'SUGGESTED_ACTION',
    action: action.action,
    message,
    suggestedAction: action,
    request: buildPlanRequest(action.action, message, action.payload || {})
  })
}

function refreshPlanPreview() {
  if (planExecutionSnapshot.value) openPlanPreview(planExecutionSnapshot.value)
}

async function executePlanPreview() {
  const snapshot = planExecutionSnapshot.value
  if (!snapshot) return
  planDrawerVisible.value = false
  if (snapshot.kind === 'REGION_SOLUTION') {
    emit('generate-region')
    return
  }
  if (snapshot.kind === 'OBJECT_SOLUTION' || snapshot.kind === 'ROUTE_REPORT') {
    if (snapshot.solutionType) generateSolutionDraft(snapshot.solutionType)
    return
  }
  input.value = snapshot.message
  await nextTick()
  await send()
}
```

- [ ] **Step 7: Run frontend utility tests**

Run:

```bash
cd srmp-web-ui
node --no-warnings --test tests/mapAiPlanPreview.test.mjs tests/liveTrace.test.mjs tests/aiRunFeedback.test.mjs
```

Expected: PASS.

- [ ] **Step 8: Commit the UI wiring**

```bash
git add srmp-web-ui/src/views/gis/components/AgentChatFloat.vue srmp-web-ui/src/views/gis/components/map-ai/MapAiSuggestedActions.vue
git commit -m "feat: preview LangGraph plans in one-map AI"
```

---

## Task 6: Verification And Manual Acceptance

**Files:**
- Verify: `srmp-ai-orchestrator/app`
- Verify: `srmp-web-ui/src`
- Verify: `srmp-web-ui/tests`

- [ ] **Step 1: Run backend plan and live trace tests**

Run:

```bash
cd srmp-ai-orchestrator
python3 -m unittest tests.test_debug_plan_preview
python3 -m unittest discover -s tests -p 'test_live_trace*.py'
```

Expected: both commands PASS.

- [ ] **Step 2: Run frontend focused tests**

Run:

```bash
cd srmp-web-ui
node --no-warnings --test tests/mapAiPlanPreview.test.mjs tests/liveTrace.test.mjs tests/aiRunFeedback.test.mjs tests/latestRequestGuard.test.mjs
```

Expected: PASS.

- [ ] **Step 3: Run frontend production build**

Run:

```bash
cd srmp-web-ui
npm run build
```

Expected: PASS. Existing Vite chunk-size warnings are acceptable if there are no TypeScript or build errors.

- [ ] **Step 4: Run a live plan smoke through Java proxy**

With Java backend and `srmp-ai-orchestrator` running, run:

```bash
curl -fsS -X POST 'http://localhost:8080/api/agent/orchestrator/ops/plan' \
  -H 'Content-Type: application/json' \
  -d '{
    "action": "GENERATE_REGION_SOLUTION",
    "message": "生成框选区域养护建议",
    "mapContext": {
      "tenantId": "default",
      "mode": "REGION",
      "routeCode": "G210",
      "year": 2026,
      "geometry": {"type": "Polygon", "coordinates": [[[106.0,25.8],[106.2,25.8],[106.2,26.0],[106.0,25.8]]]},
      "regionSummary": {"routeCount": 1, "sectionCount": 4}
    },
    "options": {"useKnowledge": true, "topK": 5, "traceId": "manual-plan-smoke"}
  }' | python3 -m json.tool
```

Expected:

- response code is success;
- payload contains `action`;
- payload contains `toolPlan`;
- payload contains `sourceHints`;
- payload contains `warnings`.

- [ ] **Step 5: Manual browser acceptance**

Open `http://localhost:5173/gis/one-map` and verify:

1. With no selected object, click `执行计划`; the plan drawer opens and either shows route/free-mode plan or a context warning.
2. Select a map object and click `执行计划`; the drawer shows `ANALYZE_OBJECT`, object context chips, and GIS/knowledge tools.
3. Select a map object and click `方案计划`; the drawer shows `GENERATE_OBJECT_SOLUTION` or `GENERATE_ROUTE_REPORT`.
4. Draw a polygon or rectangle region and click `方案计划`; the drawer shows `GENERATE_REGION_SOLUTION`, region context chips, source hints, and planned tools.
5. Click `按计划执行`; the drawer closes and the existing execution path runs.
6. During execution, the live trace wait panel still updates.
7. After execution, `AI Trace` still opens with the final trace.

- [ ] **Step 6: Final status check**

Run:

```bash
git status --short --branch
git log --oneline --max-count=8
```

Expected:

- only unrelated pre-existing untracked local files may remain;
- new Phase50.21 implementation commits are visible on top of `main`.

---

## Self-Review Checklist

- Spec coverage: backend plan stability, frontend normalization, drawer display, one-map AI entry points, errors, tests, and manual acceptance are covered.
- Scope: one-click startup and Docker initialization are excluded.
- Contract consistency: frontend `MapAiPlanPreview` fields match backend `action`, `intent`, `contextSummary`, `toolPlan`, `sourceHints`, `warnings`, `steps`, and `normalizedRequest`.
- Safety: plan preview does not execute tools, call LLM, or save drafts; execution still goes through `/api/agent/map-agent/run` or the existing region emit path.
