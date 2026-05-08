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

from app.java_tools import extract_knowledge_sources
from app.schemas import MapAiAgentRequest, MapAiContext, ToolResult
from app.workflow import LANGGRAPH_AVAILABLE, LangGraphWorkflow


class FakeGateway:
    async def execute_tool(self, call, request, tenant_id, trace_id):
        return ToolResult(
            toolName=call.toolName,
            success=True,
            summary="fake hit",
            data={"items": [{"routeCode": "G210", "name": call.toolName}]},
            count=1,
            costMs=1,
        )


class FakeLlmClient:
    async def chat(self, prompt):
        return ""


async def main():
    sources = extract_knowledge_sources([
        ToolResult(
            toolName="knowledge.retrieve",
            success=True,
            data={"hits": [{"id": "kg-001", "chunkId": "chunk-001", "documentId": "doc-001", "title": "规范", "score": 0.8}]},
        )
    ])
    if not sources or sources[0].get("sourceId") != "kg-001":
        raise SystemExit("[FAIL] LangGraph knowledge source normalization lost sourceId")
    if any(key in sources[0] for key in ("id", "raw")):
        raise SystemExit("[FAIL] LangGraph knowledge source normalization leaks non-Java DTO fields")

    workflow = LangGraphWorkflow(gateway=FakeGateway(), llm_client=FakeLlmClient())
    request = MapAiAgentRequest(
        message="分析当前路线整体路况，指出主要风险和养护重点",
        mapContext=MapAiContext(
            mode="ROUTE",
            routeCode="G210",
            year=2026,
            selectedLayers=["ROAD_SECTION", "DISEASE", "ASSESSMENT_RESULT"],
            userQuestion="分析当前路线整体路况，指出主要风险和养护重点",
        ),
        options={"topK": 2},
    )
    response = await workflow.run(request=request, tenant_id="default", trace_id="phase50-14-tool-state")
    tool_plan = response.data.get("toolPlan") or []
    tool_results = response.toolResults or []
    if not LANGGRAPH_AVAILABLE:
        print("[SKIP] langgraph package unavailable; sequential fallback smoke passed")
        return
    if not tool_plan:
        raise SystemExit("[FAIL] LangGraph response lost planned tools")
    if not tool_results:
        raise SystemExit("[FAIL] LangGraph tool_execute did not receive tool_plan")
    if response.data.get("toolSuccessCount", 0) <= 0:
        raise SystemExit("[FAIL] LangGraph evidence did not include successful tools")
    print("[PASS] LangGraph map-agent executes planned tools")


asyncio.run(main())
PY
