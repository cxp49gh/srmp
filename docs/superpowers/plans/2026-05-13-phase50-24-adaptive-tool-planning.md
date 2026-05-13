# Phase50.24 Adaptive Tool Planning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a bounded, read-only second-pass tool planning branch to the LangGraph Runtime when first-pass evidence is insufficient.

**Architecture:** Add a pure Python `adaptive_planner.py` decision module, wire three no-op-friendly workflow nodes before answer generation, and expose adaptive planning status through response `answerMeta`, `data`, `trace`, and plan execution comparison. The frontend only normalizes and displays the new comparison fields.

**Tech Stack:** Python 3.11, FastAPI/Pydantic, LangGraph StateGraph with sequential fallback, unittest/pytest, Vue 3 TypeScript utilities, Node test runner.

---

## File Structure

- Create `srmp-ai-orchestrator/app/adaptive_planner.py`: pure decision logic for second-pass read-only tool planning.
- Create `srmp-ai-orchestrator/tests/test_adaptive_planner.py`: unit tests for planner decisions and limits.
- Create `srmp-ai-orchestrator/tests/test_adaptive_workflow.py`: integration-style workflow tests with fake gateway results.
- Modify `srmp-ai-orchestrator/app/config.py`: add adaptive planning settings.
- Modify `srmp-ai-orchestrator/app/workflow.py`: add adaptive nodes, evidence helper, response metadata, and strategy metadata.
- Modify `srmp-ai-orchestrator/app/plan_execution.py`: classify extra adaptive tools as explained additions.
- Modify `srmp-ai-orchestrator/app/map_agent_run.py`: pass adaptive planning metadata into plan execution for map-agent responses.
- Modify `srmp-ai-orchestrator/tests/test_plan_execution.py`: cover adaptive extra tool comparison.
- Modify `srmp-web-ui/src/utils/mapAiPlanPreview.ts`: normalize adaptive extra tool fields.
- Modify `srmp-web-ui/src/views/gis/components/MapAiPlanPreviewDrawer.vue`: display adaptive additions separately from unexplained extra tools.
- Modify `srmp-web-ui/tests/mapAiPlanPreview.test.mjs`: cover adaptive comparison normalization.

## Task 1: Add Adaptive Planner Helper

**Files:**
- Create: `srmp-ai-orchestrator/app/adaptive_planner.py`
- Create: `srmp-ai-orchestrator/tests/test_adaptive_planner.py`

- [ ] **Step 1: Write failing planner tests**

Create `srmp-ai-orchestrator/tests/test_adaptive_planner.py`:

```python
import unittest

from app.adaptive_planner import build_adaptive_tool_calls, plan_adaptive_tools
from app.schemas import MapAiAgentRequest, MapAiContext, ToolCall, ToolResult


class AdaptivePlannerTest(unittest.TestCase):
    def test_skips_when_evidence_is_sufficient(self):
        request = MapAiAgentRequest(message="分析区域", mapContext=MapAiContext(mode="REGION"))

        decision = plan_adaptive_tools(
            request=request,
            intent="REGION_ANALYSIS",
            intent_detail={},
            evidence={"sufficient": True, "businessHitCount": 2, "knowledgeHitCount": 0},
            tool_results=[ToolResult(toolName="gis.queryRegionSummary", success=True, count=2)],
            existing_plan=[ToolCall(toolName="gis.queryRegionSummary", args={}, reason="first pass")],
        )

        self.assertFalse(decision.should_replan)
        self.assertEqual("SKIPPED_SUFFICIENT", decision.status)
        self.assertEqual([], decision.added_calls)

    def test_adds_knowledge_when_business_has_no_hits_and_knowledge_not_run(self):
        request = MapAiAgentRequest(
            message="这个区域怎么养护",
            mapContext=MapAiContext(mode="REGION", routeCode="G210", year=2026),
            options={"useKnowledge": False},
        )

        decision = plan_adaptive_tools(
            request=request,
            intent="REGION_ANALYSIS",
            intent_detail={},
            evidence={"sufficient": False, "businessHitCount": 0, "knowledgeHitCount": 0},
            tool_results=[ToolResult(toolName="gis.queryRegionSummary", success=True, count=0)],
            existing_plan=[ToolCall(toolName="gis.queryRegionSummary", args={"routeCode": "G210"}, reason="first pass")],
        )

        self.assertTrue(decision.should_replan)
        self.assertEqual("PLANNED", decision.status)
        self.assertEqual(["knowledge.retrieve"], [item.toolName for item in decision.added_calls])
        self.assertIn("G210", decision.added_calls[0].args["query"])

    def test_does_not_repeat_executed_tools(self):
        request = MapAiAgentRequest(message="补充解释", mapContext=MapAiContext(mode="REGION"))

        decision = plan_adaptive_tools(
            request=request,
            intent="REGION_ANALYSIS",
            intent_detail={},
            evidence={"sufficient": False, "businessHitCount": 0, "knowledgeHitCount": 0},
            tool_results=[
                ToolResult(toolName="gis.queryRegionSummary", success=True, count=0),
                ToolResult(toolName="knowledge.retrieve", success=True, count=0),
            ],
            existing_plan=[
                ToolCall(toolName="gis.queryRegionSummary", args={}, reason="first pass"),
                ToolCall(toolName="knowledge.retrieve", args={"query": "区域"}, reason="first pass"),
            ],
        )

        self.assertFalse(decision.should_replan)
        self.assertEqual("SKIPPED_NO_CANDIDATE", decision.status)
        self.assertIn("knowledge.retrieve", decision.skipped_tool_names)

    def test_skips_when_tool_limit_reached(self):
        request = MapAiAgentRequest(message="分析区域", mapContext=MapAiContext(mode="REGION"))
        existing_results = [
            ToolResult(toolName="gis.queryRegionSummary", success=True, count=0),
            ToolResult(toolName="gis.queryDiseases", success=True, count=0),
            ToolResult(toolName="gis.queryAssessmentResults", success=True, count=0),
            ToolResult(toolName="gis.queryDiseasesByStakeRange", success=True, count=0),
            ToolResult(toolName="gis.queryNearbyObjects", success=True, count=0),
            ToolResult(toolName="template.match", success=True, count=0),
        ]

        decision = plan_adaptive_tools(
            request=request,
            intent="REGION_ANALYSIS",
            intent_detail={},
            evidence={"sufficient": False, "businessHitCount": 0, "knowledgeHitCount": 0},
            tool_results=existing_results,
            existing_plan=[ToolCall(toolName=item.toolName, args={}, reason="first pass") for item in existing_results],
        )

        self.assertFalse(decision.should_replan)
        self.assertEqual("SKIPPED_LIMIT", decision.status)

    def test_request_can_disable_adaptive_planning(self):
        request = MapAiAgentRequest(
            message="分析区域",
            mapContext=MapAiContext(mode="REGION"),
            options={"disableAdaptivePlanning": True},
        )

        decision = plan_adaptive_tools(
            request=request,
            intent="REGION_ANALYSIS",
            intent_detail={},
            evidence={"sufficient": False, "businessHitCount": 0, "knowledgeHitCount": 0},
            tool_results=[],
            existing_plan=[],
        )

        self.assertFalse(decision.should_replan)
        self.assertEqual("DISABLED", decision.status)

    def test_region_candidate_uses_region_summary_when_it_was_not_executed(self):
        request = MapAiAgentRequest(
            message="分析框选区域",
            mapContext=MapAiContext(mode="REGION", routeCode="G210", year=2026, geometry={"type": "Polygon"}),
        )

        calls = build_adaptive_tool_calls(
            request=request,
            intent="REGION_ANALYSIS",
            intent_detail={},
            evidence={"sufficient": False, "businessHitCount": 0, "knowledgeHitCount": 0},
            executed_tool_names={"knowledge.retrieve"},
        )

        self.assertEqual("gis.queryRegionSummary", calls[0].toolName)
        self.assertEqual("G210", calls[0].args["routeCode"])


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run planner tests and confirm they fail**

Run:

```bash
cd srmp-ai-orchestrator
python -m pytest tests/test_adaptive_planner.py -q
```

Expected: fail with `ModuleNotFoundError: No module named 'app.adaptive_planner'`.

- [ ] **Step 3: Add adaptive planner implementation**

Create `srmp-ai-orchestrator/app/adaptive_planner.py`:

```python
from dataclasses import dataclass, field
from typing import Any, Dict, Iterable, List, Optional, Set

from .config import settings
from .planner import build_query, context_args, region_args
from .schemas import MapAiAgentRequest, ToolCall, ToolResult


ADAPTIVE_READONLY_TOOLS = {
    "knowledge.retrieve",
    "gis.queryDiseases",
    "gis.queryAssessmentResults",
    "gis.queryDiseasesByStakeRange",
    "gis.queryRegionSummary",
    "gis.queryNearbyObjects",
    "template.match",
}


@dataclass
class AdaptivePlanningDecision:
    enabled: bool
    should_replan: bool
    status: str
    reason: str
    added_calls: List[ToolCall] = field(default_factory=list)
    skipped_tool_names: List[str] = field(default_factory=list)
    iteration: int = 1
    max_iterations: int = 1
    evidence_sufficient_before: bool = False

    def to_summary(self, executed_results: Optional[Iterable[ToolResult]] = None, evidence_after: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        status = self.status
        evidence_sufficient_after = bool((evidence_after or {}).get("sufficient")) if evidence_after is not None else None
        if self.should_replan and executed_results is not None:
            results = list(executed_results or [])
            status = "EXECUTED" if any(item.success for item in results) else "FAILED"
        return {
            "enabled": self.enabled,
            "status": status,
            "iterations": 1 if self.should_replan else 0,
            "maxIterations": self.max_iterations,
            "reason": self.reason,
            "addedToolNames": [item.toolName for item in self.added_calls],
            "skippedToolNames": self.skipped_tool_names,
            "evidenceSufficientBefore": self.evidence_sufficient_before,
            "evidenceSufficientAfter": evidence_sufficient_after,
        }


def plan_adaptive_tools(
    request: MapAiAgentRequest,
    intent: str,
    intent_detail: Optional[Dict[str, Any]],
    evidence: Dict[str, Any],
    tool_results: List[ToolResult],
    existing_plan: List[ToolCall],
    iteration: int = 1,
) -> AdaptivePlanningDecision:
    options = request.options or {}
    max_iterations = _effective_max_iterations(options)
    evidence_sufficient = bool((evidence or {}).get("sufficient"))
    if not settings.adaptive_planning_enabled or _bool_option(options, "disableAdaptivePlanning", False):
        return _decision(False, False, "DISABLED", "自适应工具规划已关闭。", max_iterations, evidence_sufficient)
    if max_iterations <= 0:
        return _decision(True, False, "DISABLED", "本次请求不允许自适应追加工具。", max_iterations, evidence_sufficient)
    if iteration > max_iterations:
        return _decision(True, False, "SKIPPED_LIMIT", "已达到自适应规划轮次上限。", max_iterations, evidence_sufficient)
    if evidence_sufficient:
        return _decision(True, False, "SKIPPED_SUFFICIENT", "第一轮证据已足够。", max_iterations, evidence_sufficient)
    if len(tool_results or []) >= settings.max_tool_calls:
        return _decision(True, False, "SKIPPED_LIMIT", "已达到工具调用数量上限。", max_iterations, evidence_sufficient)

    executed_tool_names = {item.toolName for item in tool_results or [] if item.toolName}
    candidates = build_adaptive_tool_calls(request, intent, intent_detail or {}, evidence or {}, executed_tool_names)
    added_calls, skipped = _filter_candidates(candidates, executed_tool_names, existing_plan or [], settings.max_tool_calls - len(tool_results or []))
    if not added_calls:
        return AdaptivePlanningDecision(
            enabled=True,
            should_replan=False,
            status="SKIPPED_NO_CANDIDATE",
            reason="证据不足，但没有未执行过的安全只读候选工具。",
            skipped_tool_names=skipped,
            max_iterations=max_iterations,
            evidence_sufficient_before=evidence_sufficient,
        )
    return AdaptivePlanningDecision(
        enabled=True,
        should_replan=True,
        status="PLANNED",
        reason=_reason_for_calls(added_calls, evidence or {}),
        added_calls=added_calls,
        skipped_tool_names=skipped,
        iteration=iteration,
        max_iterations=max_iterations,
        evidence_sufficient_before=evidence_sufficient,
    )


def build_adaptive_tool_calls(
    request: MapAiAgentRequest,
    intent: str,
    intent_detail: Optional[Dict[str, Any]],
    evidence: Dict[str, Any],
    executed_tool_names: Set[str],
) -> List[ToolCall]:
    ctx = request.mapContext
    candidates: List[ToolCall] = []
    business_hits = _number(evidence.get("businessHitCount"))
    knowledge_hits = _number(evidence.get("knowledgeHitCount"))
    mode = str(ctx.mode if ctx else "").upper()

    if _is_region_request(intent, mode) and "gis.queryRegionSummary" not in executed_tool_names:
        candidates.append(ToolCall(toolName="gis.queryRegionSummary", args=region_args(ctx, {"limit": 50}), reason="第一轮证据不足，补充区域统计摘要"))

    if "knowledge.retrieve" not in executed_tool_names and (business_hits <= 0 or knowledge_hits <= 0):
        candidates.append(
            ToolCall(
                toolName="knowledge.retrieve",
                args={"query": build_query(request, intent), "topK": _option_int(request, "topK", 5)},
                reason="第一轮证据不足，补充知识库规则和处置依据",
            )
        )

    if intent in {"OBJECT_ANALYSIS", "SOLUTION_GENERATE"} and "gis.queryAssessmentResults" not in executed_tool_names:
        candidates.append(ToolCall(toolName="gis.queryAssessmentResults", args=context_args(ctx, {"limit": 20}), reason="第一轮证据不足，补充评定结果"))

    return candidates


def _filter_candidates(
    candidates: Iterable[ToolCall],
    executed_tool_names: Set[str],
    existing_plan: List[ToolCall],
    remaining_slots: int,
) -> tuple[List[ToolCall], List[str]]:
    added: List[ToolCall] = []
    skipped: List[str] = []
    existing_keys = {_call_key(item) for item in existing_plan or []}
    for call in candidates:
        if call.toolName in executed_tool_names:
            skipped.append(call.toolName)
            continue
        if call.toolName not in settings.allowed_tools or call.toolName not in ADAPTIVE_READONLY_TOOLS:
            skipped.append(call.toolName)
            continue
        key = _call_key(call)
        if key in existing_keys:
            skipped.append(call.toolName)
            continue
        if remaining_slots <= 0:
            skipped.append(call.toolName)
            continue
        added.append(call)
        existing_keys.add(key)
        remaining_slots -= 1
    return added, _unique(skipped)


def _decision(enabled: bool, should_replan: bool, status: str, reason: str, max_iterations: int, evidence_sufficient: bool) -> AdaptivePlanningDecision:
    return AdaptivePlanningDecision(
        enabled=enabled,
        should_replan=should_replan,
        status=status,
        reason=reason,
        max_iterations=max_iterations,
        evidence_sufficient_before=evidence_sufficient,
    )


def _effective_max_iterations(options: Dict[str, Any]) -> int:
    requested = _option_int_from_dict(options, "maxAdaptiveIterations", settings.max_adaptive_iterations)
    return max(0, min(requested, settings.max_adaptive_iterations, 1))


def _option_int(request: MapAiAgentRequest, key: str, default: int) -> int:
    return _option_int_from_dict(request.options or {}, key, default)


def _option_int_from_dict(options: Dict[str, Any], key: str, default: int) -> int:
    try:
        return int(options.get(key, default))
    except (TypeError, ValueError):
        return default


def _bool_option(options: Dict[str, Any], key: str, default: bool) -> bool:
    value = options.get(key)
    if value is None:
        return default
    if isinstance(value, bool):
        return value
    return str(value).strip().lower() in {"1", "true", "yes", "y", "on"}


def _is_region_request(intent: str, mode: str) -> bool:
    return intent == "REGION_ANALYSIS" or mode in {"REGION", "BOX", "POLYGON", "SELECTION"}


def _reason_for_calls(calls: List[ToolCall], evidence: Dict[str, Any]) -> str:
    names = [item.toolName for item in calls]
    if names == ["knowledge.retrieve"]:
        return "业务工具未命中，追加知识检索补充解释依据。"
    if "gis.queryRegionSummary" in names:
        return "第一轮证据不足，追加区域统计摘要补充业务依据。"
    return "第一轮证据不足，追加安全只读工具补充依据。"


def _call_key(call: ToolCall) -> tuple[str, str]:
    return (call.toolName, str(sorted((call.args or {}).items())))


def _number(value: Any) -> float:
    try:
        return float(value or 0)
    except (TypeError, ValueError):
        return 0


def _unique(values: Iterable[str]) -> List[str]:
    result: List[str] = []
    for value in values:
        if value and value not in result:
            result.append(value)
    return result
```

- [ ] **Step 4: Run planner tests and confirm they pass**

Run:

```bash
cd srmp-ai-orchestrator
python -m pytest tests/test_adaptive_planner.py -q
```

Expected: all tests pass.

- [ ] **Step 5: Commit planner helper**

```bash
git add srmp-ai-orchestrator/app/adaptive_planner.py srmp-ai-orchestrator/tests/test_adaptive_planner.py
git commit -m "feat: add adaptive tool planner"
```

## Task 2: Wire Adaptive Nodes Into Runtime Workflow

**Files:**
- Modify: `srmp-ai-orchestrator/app/config.py`
- Modify: `srmp-ai-orchestrator/app/workflow.py`
- Create: `srmp-ai-orchestrator/tests/test_adaptive_workflow.py`

- [ ] **Step 1: Write failing workflow tests**

Create `srmp-ai-orchestrator/tests/test_adaptive_workflow.py`:

```python
import unittest

from app.schemas import MapAiAgentRequest, MapAiContext, ToolCall, ToolResult
from app.workflow import LangGraphWorkflow, strategy_metadata


class AdaptiveWorkflowTest(unittest.IsolatedAsyncioTestCase):
    async def test_workflow_runs_adaptive_second_pass_before_answer(self):
        gateway = AdaptiveFakeGateway()
        workflow = LangGraphWorkflow(gateway=gateway, llm_client=FakeLlmClient())
        request = MapAiAgentRequest(
            action="ANALYZE_REGION",
            message="分析这个区域怎么养护",
            mapContext=MapAiContext(mode="REGION", routeCode="G210", year=2026),
            options={"traceId": "adaptive-run-1", "useKnowledge": False},
        )

        response = await workflow.run(request, tenant_id="default", trace_id=None)

        self.assertEqual(["gis.queryRegionSummary", "knowledge.retrieve"], gateway.tool_names)
        self.assertEqual("EXECUTED", response.data["adaptivePlanning"]["status"])
        self.assertEqual(["knowledge.retrieve"], response.data["adaptivePlanning"]["addedToolNames"])
        self.assertEqual("EXECUTED", response.answerMeta["adaptivePlanningStatus"])
        self.assertEqual(response.data["adaptivePlanning"], response.trace["adaptivePlanning"])
        self.assertTrue(response.data["evidence"]["sufficient"])
        self.assertIn("adaptive_tool_planning", response.data["nodeFlow"])

    async def test_workflow_skips_adaptive_when_first_pass_evidence_is_sufficient(self):
        gateway = SufficientFakeGateway()
        workflow = LangGraphWorkflow(gateway=gateway, llm_client=FakeLlmClient())
        request = MapAiAgentRequest(
            action="ANALYZE_REGION",
            message="分析区域",
            mapContext=MapAiContext(mode="REGION", routeCode="G210", year=2026),
            options={"traceId": "adaptive-run-2", "useKnowledge": False},
        )

        response = await workflow.run(request, tenant_id="default", trace_id=None)

        self.assertEqual(["gis.queryRegionSummary"], gateway.tool_names)
        self.assertEqual("SKIPPED_SUFFICIENT", response.data["adaptivePlanning"]["status"])
        self.assertEqual([], response.data["adaptivePlanning"]["addedToolNames"])

    async def test_strategy_metadata_exposes_adaptive_planning(self):
        metadata = strategy_metadata()

        self.assertTrue(metadata["adaptivePlanning"])
        self.assertEqual(1, metadata["maxAdaptiveIterations"])


class AdaptiveFakeGateway:
    def __init__(self):
        self.tool_names = []

    async def execute_tool(self, call: ToolCall, **kwargs):
        self.tool_names.append(call.toolName)
        if call.toolName == "gis.queryRegionSummary":
            return ToolResult(toolName=call.toolName, success=True, count=0, data={"items": []})
        if call.toolName == "knowledge.retrieve":
            return ToolResult(toolName=call.toolName, success=True, count=2, data={"sources": [{"title": "规则"}]})
        return ToolResult(toolName=call.toolName, success=False, errorMessage="unexpected tool")


class SufficientFakeGateway:
    def __init__(self):
        self.tool_names = []

    async def execute_tool(self, call: ToolCall, **kwargs):
        self.tool_names.append(call.toolName)
        return ToolResult(toolName=call.toolName, success=True, count=3, data={"items": [{"id": 1}, {"id": 2}, {"id": 3}]})


class FakeLlmClient:
    pass


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run workflow tests and confirm they fail**

Run:

```bash
cd srmp-ai-orchestrator
python -m pytest tests/test_adaptive_workflow.py -q
```

Expected: fail because `adaptivePlanning` and adaptive workflow nodes do not exist yet.

- [ ] **Step 3: Add adaptive settings**

In `srmp-ai-orchestrator/app/config.py`, add these fields after `require_evidence_prefix`:

```python
    adaptive_planning_enabled: bool = _bool_env("SRMP_ADAPTIVE_PLANNING_ENABLED", True)
    max_adaptive_iterations: int = max(0, min(_int_env("SRMP_MAX_ADAPTIVE_ITERATIONS", 1), 1))
```

- [ ] **Step 4: Wire workflow imports, flow, labels, and state**

In `srmp-ai-orchestrator/app/workflow.py`, add this import:

```python
from .adaptive_planner import plan_adaptive_tools
```

Update `NODE_FLOW`:

```python
NODE_FLOW = [
    "request_normalize",
    "context_build",
    "intent_recognize",
    "context_enrich",
    "tool_planning",
    "tool_execute",
    "evidence_fuse",
    "adaptive_tool_planning",
    "adaptive_tool_execute",
    "adaptive_evidence_fuse",
    "answer_generate",
    "quality_guard",
]
```

Add labels to `NODE_LABELS`:

```python
    "adaptive_tool_planning": "自适应补充规划",
    "adaptive_tool_execute": "执行补充工具",
    "adaptive_evidence_fuse": "融合补充证据",
```

Add fields to `AgentState`:

```python
    adaptive_tool_plan: List[ToolCall]
    adaptive_tool_results: List[ToolResult]
    adaptive_planning: Dict[str, Any]
```

Update `strategy_metadata()`:

```python
        "adaptivePlanning": settings.adaptive_planning_enabled,
        "maxAdaptiveIterations": settings.max_adaptive_iterations,
```

Update `_build_graph()` by adding nodes before `answer_generate`:

```python
        builder.add_node("adaptive_tool_planning", self._wrap_node("adaptive_tool_planning", self._adaptive_tool_planning))
        builder.add_node("adaptive_tool_execute", self._wrap_node("adaptive_tool_execute", self._adaptive_tool_execute))
        builder.add_node("adaptive_evidence_fuse", self._wrap_node("adaptive_evidence_fuse", self._adaptive_evidence_fuse))
```

- [ ] **Step 5: Refactor evidence construction**

In `srmp-ai-orchestrator/app/workflow.py`, replace the body of `_evidence_fuse()` with:

```python
    async def _evidence_fuse(self, state: AgentState) -> Dict[str, Any]:
        evidence = self._build_evidence(state.get("tool_results", []))
        self._step(state, "evidence_fuse", "融合 GIS 与知识库证据", evidence)
        return {"evidence": evidence}
```

Add this helper near `_evidence_fuse()`:

```python
    def _build_evidence(self, results: List[ToolResult]) -> Dict[str, Any]:
        success = [item for item in results if item.success]
        failed = [item for item in results if not item.success]
        business_hit_count = 0
        knowledge_hit_count = 0

        for item in success:
            count = item.count if isinstance(item.count, int) else _estimate_hit_count(item.data)
            if item.toolName == "knowledge.retrieve":
                knowledge_hit_count += count
            else:
                business_hit_count += count

        return {
            "strategyVersion": settings.strategy_version,
            "toolSuccessCount": len(success),
            "toolFailedCount": len(failed),
            "businessHitCount": business_hit_count,
            "knowledgeHitCount": knowledge_hit_count,
            "sufficient": bool(success) and (business_hit_count > 0 or knowledge_hit_count > 0),
            "failedTools": [{"toolName": item.toolName, "error": item.errorMessage or item.error} for item in failed],
            "toolSummary": [
                {
                    "toolName": item.toolName,
                    "success": item.success,
                    "hitCount": item.count if isinstance(item.count, int) else _estimate_hit_count(item.data),
                    "reason": item.summary or item.reason,
                    "costMs": item.costMs,
                }
                for item in results
            ],
        }
```

- [ ] **Step 6: Add adaptive node handlers**

In `srmp-ai-orchestrator/app/workflow.py`, add these methods between `_evidence_fuse()` and `_call_llm()`:

```python
    async def _adaptive_tool_planning(self, state: AgentState) -> Dict[str, Any]:
        decision = plan_adaptive_tools(
            request=state["request"],
            intent=state.get("intent", "KNOWLEDGE_QA"),
            intent_detail=state.get("intent_detail", {}),
            evidence=state.get("evidence", {}),
            tool_results=state.get("tool_results", []),
            existing_plan=state.get("tool_plan", []),
        )
        summary = decision.to_summary()
        step_status = "SUCCESS" if decision.should_replan else "SKIPPED"
        self._step(
            state,
            "adaptive_tool_planning",
            decision.reason,
            summary,
            status=step_status,
            count=len(decision.added_calls),
        )
        return {"adaptive_tool_plan": decision.added_calls, "adaptive_planning": summary}

    async def _adaptive_tool_execute(self, state: AgentState) -> Dict[str, Any]:
        calls = state.get("adaptive_tool_plan", [])
        if not calls:
            self._step(state, "adaptive_tool_execute", "无补充工具需要执行", state.get("adaptive_planning", {}), status="SKIPPED")
            return {"adaptive_tool_results": []}

        results: List[ToolResult] = []
        for call in calls:
            results.append(await self._execute_single_tool(state, call))
        combined_results = list(state.get("tool_results", [])) + results
        planning = dict(state.get("adaptive_planning") or {})
        planning.update({
            "status": "EXECUTED" if any(item.success for item in results) else "FAILED",
            "addedToolNames": [item.toolName for item in calls],
        })
        self._step(
            state,
            "adaptive_tool_execute",
            "执行自适应补充工具",
            {
                "success": sum(1 for item in results if item.success),
                "failed": sum(1 for item in results if not item.success),
                "tools": [item.toolName for item in results],
            },
            status="SUCCESS" if any(item.success for item in results) else "FAILED",
            count=len(results),
        )
        return {"tool_results": combined_results, "adaptive_tool_results": results, "adaptive_planning": planning}

    async def _adaptive_evidence_fuse(self, state: AgentState) -> Dict[str, Any]:
        results = state.get("adaptive_tool_results", [])
        if not results:
            self._step(state, "adaptive_evidence_fuse", "无补充证据需要融合", state.get("adaptive_planning", {}), status="SKIPPED")
            return {}
        evidence = self._build_evidence(state.get("tool_results", []))
        planning = dict(state.get("adaptive_planning") or {})
        planning["evidenceSufficientAfter"] = bool(evidence.get("sufficient"))
        self._step(state, "adaptive_evidence_fuse", "融合自适应补充证据", {"adaptivePlanning": planning, "evidence": evidence})
        return {"evidence": evidence, "adaptive_planning": planning}
```

- [ ] **Step 7: Expose adaptive planning in response**

In `_to_response()`, after `answer_meta = dict(answer_meta)`, add:

```python
        adaptive_planning = state.get("adaptive_planning") or {
            "enabled": settings.adaptive_planning_enabled,
            "status": "SKIPPED_NO_CANDIDATE",
            "iterations": 0,
            "maxIterations": settings.max_adaptive_iterations,
            "reason": "自适应规划未产生补充工具。",
            "addedToolNames": [],
            "skippedToolNames": [],
            "evidenceSufficientBefore": bool((state.get("evidence") or {}).get("sufficient")),
            "evidenceSufficientAfter": bool((state.get("evidence") or {}).get("sufficient")),
        }
        answer_meta["adaptivePlanningStatus"] = adaptive_planning.get("status")
```

Pass adaptive planning into `build_plan_execution()`:

```python
                "adaptivePlanning": adaptive_planning,
```

Add adaptive planning to `trace_payload`:

```python
            "adaptivePlanning": adaptive_planning,
```

Add adaptive planning to response `data`:

```python
                "adaptivePlanning": adaptive_planning,
```

- [ ] **Step 8: Run workflow tests and existing orchestrator tests**

Run:

```bash
cd srmp-ai-orchestrator
python -m pytest tests/test_adaptive_planner.py tests/test_adaptive_workflow.py tests/test_plan_execution.py tests/test_debug_plan_preview.py tests/test_live_trace.py -q
```

Expected: all selected tests pass.

- [ ] **Step 9: Commit workflow wiring**

```bash
git add srmp-ai-orchestrator/app/config.py srmp-ai-orchestrator/app/workflow.py srmp-ai-orchestrator/tests/test_adaptive_workflow.py
git commit -m "feat: run adaptive tool planning in workflow"
```

## Task 3: Explain Adaptive Extras in Plan Execution

**Files:**
- Modify: `srmp-ai-orchestrator/app/plan_execution.py`
- Modify: `srmp-ai-orchestrator/app/map_agent_run.py`
- Modify: `srmp-ai-orchestrator/tests/test_plan_execution.py`

- [ ] **Step 1: Add failing plan execution test**

Append this test to `PlanExecutionHelpersTest` in `srmp-ai-orchestrator/tests/test_plan_execution.py`:

```python
    def test_adaptive_extra_tool_is_explained_as_partial(self):
        plan = normalize_plan_preview(
            {
                "planTraceId": "plan-adaptive-1",
                "action": "ANALYZE_REGION",
                "intent": "REGION_ANALYSIS",
                "toolNames": ["gis.queryRegionSummary"],
                "sourceTypes": ["BUSINESS_DATA"],
            }
        )

        result = build_plan_execution(
            plan,
            {
                "runTraceId": "run-adaptive-1",
                "actualAction": "ANALYZE_REGION",
                "actualIntent": "REGION_ANALYSIS",
                "toolResults": [
                    {"toolName": "gis.queryRegionSummary", "success": True},
                    {"toolName": "knowledge.retrieve", "success": True},
                ],
                "sources": [],
                "evidence": {"businessHitCount": 0, "knowledgeHitCount": 2},
                "adaptivePlanning": {
                    "status": "EXECUTED",
                    "reason": "业务工具未命中，追加知识检索补充解释依据。",
                    "addedToolNames": ["knowledge.retrieve"],
                },
            },
        )

        self.assertEqual("PARTIAL", result["status"])
        self.assertEqual(["knowledge.retrieve"], result["extraToolNames"])
        self.assertEqual(["knowledge.retrieve"], result["adaptiveExtraToolNames"])
        self.assertEqual("业务工具未命中，追加知识检索补充解释依据。", result["adaptiveReason"])
        self.assertTrue(any(item["code"] == "ADAPTIVE_EXTRA_TOOL_EXECUTED" for item in result["warnings"]))
```

- [ ] **Step 2: Run test and confirm it fails**

Run:

```bash
cd srmp-ai-orchestrator
python -m pytest tests/test_plan_execution.py -q
```

Expected: fail because `adaptiveExtraToolNames` is missing.

- [ ] **Step 3: Update plan execution helper**

In `srmp-ai-orchestrator/app/plan_execution.py`, inside `build_plan_execution()`, add adaptive extraction after `extra_tools`:

```python
    adaptive_planning = response_state.get("adaptivePlanning") if isinstance(response_state.get("adaptivePlanning"), dict) else {}
    adaptive_added_tools = _unique_strings(adaptive_planning.get("addedToolNames") or [])
    adaptive_extra_tools = [item for item in extra_tools if item in adaptive_added_tools]
    adaptive_reason = _string_or_none(adaptive_planning.get("reason"))
```

Replace the existing `if extra_tools:` warning block with:

```python
    if extra_tools:
        if adaptive_extra_tools and len(adaptive_extra_tools) == len(extra_tools):
            warnings.append({"level": "INFO", "code": "ADAPTIVE_EXTRA_TOOL_EXECUTED", "message": "实际执行追加了自适应规划工具。"})
        else:
            warnings.append({"level": "INFO", "code": "EXTRA_TOOL_EXECUTED", "message": "实际执行调用了计划外工具。"})
```

Add fields to the returned dict for both `NO_PLAN` and available-plan branches:

```python
            "adaptiveExtraToolNames": [],
            "adaptiveReason": None,
```

and:

```python
        "adaptiveExtraToolNames": adaptive_extra_tools,
        "adaptiveReason": adaptive_reason,
```

- [ ] **Step 4: Pass adaptive planning from map-agent wrapper**

In `srmp-ai-orchestrator/app/map_agent_run.py`, inside `_attach_plan_execution()`, add `adaptivePlanning` to the response state:

```python
                "adaptivePlanning": (response.data or {}).get("adaptivePlanning") or {},
```

- [ ] **Step 5: Run plan execution tests**

Run:

```bash
cd srmp-ai-orchestrator
python -m pytest tests/test_plan_execution.py -q
```

Expected: all tests pass.

- [ ] **Step 6: Commit plan execution explanation**

```bash
git add srmp-ai-orchestrator/app/plan_execution.py srmp-ai-orchestrator/app/map_agent_run.py srmp-ai-orchestrator/tests/test_plan_execution.py
git commit -m "feat: explain adaptive plan execution extras"
```

## Task 4: Normalize and Display Adaptive Comparison Fields

**Files:**
- Modify: `srmp-web-ui/src/utils/mapAiPlanPreview.ts`
- Modify: `srmp-web-ui/src/views/gis/components/MapAiPlanPreviewDrawer.vue`
- Modify: `srmp-web-ui/tests/mapAiPlanPreview.test.mjs`

- [ ] **Step 1: Write failing frontend normalization test**

Append this test to `srmp-web-ui/tests/mapAiPlanPreview.test.mjs`:

```js
test('normalizes adaptive extra tools in plan execution', () => {
  const execution = normalizePlanExecution({
    planExecution: {
      available: true,
      status: 'PARTIAL',
      plannedToolNames: ['gis.queryRegionSummary'],
      actualToolNames: ['gis.queryRegionSummary', 'knowledge.retrieve'],
      missingToolNames: [],
      extraToolNames: ['knowledge.retrieve'],
      adaptiveExtraToolNames: ['knowledge.retrieve'],
      adaptiveReason: '业务工具未命中，追加知识检索补充解释依据。',
      plannedSourceTypes: ['BUSINESS_DATA'],
      actualSourceTypes: ['KNOWLEDGE'],
      missingSourceTypes: ['BUSINESS_DATA']
    }
  })

  assert.deepEqual(execution.adaptiveExtraToolNames, ['knowledge.retrieve'])
  assert.equal(execution.adaptiveReason, '业务工具未命中，追加知识检索补充解释依据。')
})
```

- [ ] **Step 2: Run frontend test and confirm it fails**

Run:

```bash
cd srmp-web-ui
node --no-warnings --test tests/mapAiPlanPreview.test.mjs
```

Expected: fail because `adaptiveExtraToolNames` is not in the normalized object.

- [ ] **Step 3: Extend plan execution type and normalizer**

In `srmp-web-ui/src/utils/mapAiPlanPreview.ts`, add fields to `MapAiPlanExecution`:

```ts
  adaptiveExtraToolNames: string[]
  adaptiveReason?: string
```

In `normalizePlanExecution()`, add:

```ts
    adaptiveExtraToolNames: uniqueStrings(arrayValue(raw.adaptiveExtraToolNames)),
    adaptiveReason: stringValue(raw.adaptiveReason),
```

- [ ] **Step 4: Display adaptive extra tools separately**

In `srmp-web-ui/src/views/gis/components/MapAiPlanPreviewDrawer.vue`, replace the extra tool diff block:

```vue
            <div v-if="planExecution.extraToolNames.length" class="diff-item">
              <el-tag size="small" type="warning" effect="plain">额外工具</el-tag>
              <span>{{ planExecution.extraToolNames.join('、') }}</span>
            </div>
```

with:

```vue
            <div v-if="planExecution.adaptiveExtraToolNames.length" class="diff-item">
              <el-tag size="small" type="success" effect="plain">自适应追加</el-tag>
              <span>{{ planExecution.adaptiveExtraToolNames.join('、') }}</span>
            </div>
            <div v-if="unexplainedExtraTools(planExecution).length" class="diff-item">
              <el-tag size="small" type="warning" effect="plain">额外工具</el-tag>
              <span>{{ unexplainedExtraTools(planExecution).join('、') }}</span>
            </div>
            <div v-if="planExecution.adaptiveReason" class="diff-item">
              <el-tag size="small" type="info" effect="plain">追加原因</el-tag>
              <span>{{ planExecution.adaptiveReason }}</span>
            </div>
```

Add this helper in `<script setup>`:

```ts
function unexplainedExtraTools(planExecution: MapAiPlanExecution) {
  return planExecution.extraToolNames.filter((item) => !planExecution.adaptiveExtraToolNames.includes(item))
}
```

- [ ] **Step 5: Run frontend tests**

Run:

```bash
cd srmp-web-ui
node --no-warnings --test tests/mapAiPlanPreview.test.mjs tests/mapAiWorkbenchSplit.test.mjs
```

Expected: all selected tests pass.

- [ ] **Step 6: Commit frontend adaptive display**

```bash
git add srmp-web-ui/src/utils/mapAiPlanPreview.ts srmp-web-ui/src/views/gis/components/MapAiPlanPreviewDrawer.vue srmp-web-ui/tests/mapAiPlanPreview.test.mjs
git commit -m "feat: show adaptive plan execution extras"
```

## Task 5: Final Verification and Integration

**Files:**
- Verify only; no file edits expected.

- [ ] **Step 1: Run orchestrator regression tests**

Run:

```bash
cd srmp-ai-orchestrator
python -m pytest tests/test_adaptive_planner.py tests/test_adaptive_workflow.py tests/test_plan_execution.py tests/test_debug_plan_preview.py tests/test_live_trace.py tests/test_live_trace_api.py -q
```

Expected: all tests pass.

- [ ] **Step 2: Run frontend focused tests**

Run:

```bash
cd srmp-web-ui
node --no-warnings --test tests/mapAiPlanPreview.test.mjs tests/mapAiWorkbenchSplit.test.mjs tests/liveTrace.test.mjs tests/aiRunFeedback.test.mjs tests/latestRequestGuard.test.mjs
```

Expected: all tests pass.

- [ ] **Step 3: Build frontend**

Run:

```bash
cd srmp-web-ui
npm run build
```

Expected: build succeeds. Existing Vite chunk-size warnings are acceptable if no new error appears.

- [ ] **Step 4: Check git status**

Run:

```bash
git status --short --branch
```

Expected: only known local untracked environment files remain outside this work:

```text
?? .env.dev
?? dd.json
?? deploy/outline/data/
?? deploy/outline/outline.env
?? deploy/outline/outline.env.bak
?? test.png
```

- [ ] **Step 5: Merge and push**

If all verification passes:

```bash
git switch main
git merge --ff-only codex/phase50-24-adaptive-tool-planning-design
git push origin main
```

Expected: `origin/main` advances to the final Phase50.24 commit.

## Self-Review

- Spec coverage: the plan covers the adaptive helper, workflow nodes, response contract, plan execution explanation, frontend normalization/display, and verification.
- Scope check: all implementation is bounded to one extra read-only pass; no multi-round loop, write tool, or UI redesign is included.
- Type consistency: backend response fields use `adaptivePlanning`, `adaptivePlanningStatus`, `adaptiveExtraToolNames`, and `adaptiveReason`; frontend normalizer uses the same names.
