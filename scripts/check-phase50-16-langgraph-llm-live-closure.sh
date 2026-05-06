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

assert_file_contains() {
  local file="$1"
  local pattern="$2"
  local message="$3"
  if ! grep -q "$pattern" "$file"; then
    echo "[FAIL] $message" >&2
    exit 1
  fi
}

assert_file_contains srmp-ai-orchestrator/app/llm_client.py "class LlmResult" "llm_client.py must define LlmResult"
assert_file_contains srmp-ai-orchestrator/app/llm_client.py "async def generate" "LlmClient must expose generate()"
assert_file_contains srmp-ai-orchestrator/app/workflow.py "llm_answer" "workflow must emit llm_answer trace step"
assert_file_contains srmp-ai-orchestrator/app/config.py "llm_read_timeout_seconds" "config must expose LangGraph LLM read timeout"
assert_file_contains srmp-ai-orchestrator/app/main.py "llm-probe" "FastAPI must expose LangGraph LLM probe"
assert_file_contains srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/controller/AgentOrchestratorOpsController.java "/llm-probe" "Java ops proxy must expose LangGraph LLM probe"
assert_file_contains srmp-web-ui/src/views/agent/LangGraphOpsPage.vue "LangGraph LLM" "LangGraph ops page must show LLM diagnostics"

PYTHONPATH="$ROOT/srmp-ai-orchestrator" "$PYTHON_BIN" - <<'PY'
import asyncio
import warnings

warnings.filterwarnings("ignore", message="urllib3 v2 only supports OpenSSL")

from app.config import settings
from app.llm_client import LlmResult
from app.schemas import MapAiAgentRequest, MapAiContext, ToolResult
from app.workflow import LangGraphWorkflow


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


def request():
    return MapAiAgentRequest(
        message="分析当前选中病害并给出处置建议",
        mapContext=MapAiContext(
            tenantId="default",
            mode="OBJECT",
            routeCode="G210",
            year=2026,
            mapObject=DISEASE_OBJ,
            selectedLayers=["DISEASE", "ASSESSMENT_RESULT"],
            userQuestion="分析当前选中病害并给出处置建议",
        ),
        mapObject=DISEASE_OBJ,
        options={"topK": 3, "useKnowledge": True, "useTools": True},
    )


class FakeGateway:
    async def execute_tool(self, call, request, tenant_id, trace_id):
        if call.toolName == "knowledge.retrieve":
            data = {
                "hits": [
                    {
                        "chunkId": "chunk-1",
                        "documentId": "doc-1",
                        "title": "养护规范",
                        "sectionTitle": "坑槽处置",
                        "sourceType": "KNOWLEDGE",
                        "sourceId": "kg-1",
                        "content": "坑槽应切割清理、分层填补、压实成型，并复核排水。",
                        "score": 0.91,
                    }
                ]
            }
        elif call.toolName == "gis.queryNearbyObjects":
            data = {"diseases": [DISEASE_OBJ], "assessments": []}
        else:
            data = {"items": [DISEASE_OBJ], "count": 1}
        return ToolResult(toolName=call.toolName, success=True, summary="fake " + call.toolName, data=data, count=1, costMs=1)


class SuccessLlmClient:
    async def generate(self, prompt, system_prompt=None):
        return LlmResult.success(
            answer="【基于当前地图对象】LLM 识别当前坑槽为重度病害，建议现场复核范围后切割清理、分层填补并压实。",
            model="fake-qwen",
            cost_ms=12,
            finish_reason="stop",
            raw_response_preview="ok",
        )


class EmptyThenSuccessLlmClient:
    def __init__(self):
        self.calls = 0

    async def generate(self, prompt, system_prompt=None):
        self.calls += 1
        if self.calls == 1:
            return LlmResult.failed(
                error_type="EMPTY_RESPONSE",
                error_message="LLM 返回为空",
                model="fake-qwen",
                cost_ms=9,
                raw_response_preview="",
            )
        return LlmResult.success(
            answer="【基于当前地图对象】精简 Prompt 后返回：坑槽需优先处治，控制雨水侵入并复核周边结构层。",
            model="fake-qwen",
            cost_ms=10,
            finish_reason="stop",
            raw_response_preview="ok-after-retry",
        )


class FailedLlmClient:
    async def generate(self, prompt, system_prompt=None):
        return LlmResult.failed(
            error_type="PROVIDER_ERROR",
            error_message="provider boom",
            model="fake-qwen",
            cost_ms=7,
            raw_response_preview="boom",
        )


class LegacyChatLlmClient:
    async def chat(self, prompt):
        return ""


async def run_case(name, use_llm, client, expected_llm_status, expected_answer_source, expected_retry):
    object.__setattr__(settings, "use_llm", use_llm)
    object.__setattr__(settings, "llm_compact_retry_enabled", True)
    workflow = LangGraphWorkflow(gateway=FakeGateway(), llm_client=client)
    response = await workflow.run(request(), tenant_id="default", trace_id="phase50-16-" + name)
    steps = response.trace.get("steps") or []
    llm_steps = [item for item in steps if item.get("name") == "llm_answer"]
    if not llm_steps:
        raise SystemExit(f"[FAIL] {name}: missing llm_answer trace step")
    llm_step = llm_steps[0]
    if llm_step.get("status") != expected_llm_status:
        raise SystemExit(f"[FAIL] {name}: expected llm status {expected_llm_status}, got {llm_step}")
    answer_meta = response.data.get("answerMeta") or {}
    if answer_meta.get("answerSource") != expected_answer_source:
        raise SystemExit(f"[FAIL] {name}: expected answerSource {expected_answer_source}, got {answer_meta}")
    if bool(answer_meta.get("retriedWithCompactPrompt")) != expected_retry:
        raise SystemExit(f"[FAIL] {name}: retry mismatch, got {answer_meta}")
    if expected_llm_status == "FAILED":
        data = llm_step.get("data") or {}
        if not data.get("errorType") or not data.get("errorMessage"):
            raise SystemExit(f"[FAIL] {name}: failed LLM step lacks diagnostics: {data}")
    if expected_retry and "firstAttempt" not in (llm_step.get("data") or {}):
        raise SystemExit(f"[FAIL] {name}: retry step lacks firstAttempt diagnostics")
    if not response.answer or len(response.answer.strip()) < 80:
        raise SystemExit(f"[FAIL] {name}: final answer is empty or too short: {response.answer!r}")
    if response.data.get("orchestratorProvider") != "langgraph" or response.data.get("orchestratorFallback") is not False:
        raise SystemExit(f"[FAIL] {name}: provider/fallback metadata wrong: {response.data}")


async def main():
    await run_case("disabled", False, SuccessLlmClient(), "SKIPPED", "FALLBACK", False)
    await run_case("success", True, SuccessLlmClient(), "SUCCESS", "LLM", False)
    await run_case("retry", True, EmptyThenSuccessLlmClient(), "SUCCESS", "LLM", True)
    await run_case("failed", True, FailedLlmClient(), "FAILED", "FALLBACK", False)
    await run_case("legacy", False, LegacyChatLlmClient(), "SKIPPED", "FALLBACK", False)


asyncio.run(main())
print("[PASS] Phase50.16 local LangGraph LLM live-closure checks passed")
PY

if [ "${SRMP_PHASE50_16_LIVE:-0}" = "1" ]; then
  if [ "${SRMP_LANGGRAPH_USE_LLM:-false}" != "true" ]; then
    echo "[FAIL] SRMP_PHASE50_16_LIVE=1 requires SRMP_LANGGRAPH_USE_LLM=true" >&2
    exit 1
  fi

  curl -fsS --max-time 180 "${SRMP_JAVA_URL:-http://localhost:8080}/api/agent/orchestrator/ops/llm-probe?probe=true" >/tmp/srmp-phase50-16-llm-probe.json
  BODY='{"message":"分析当前选中病害并给出处置建议","mapContext":{"tenantId":"default","mode":"OBJECT","routeCode":"G210","year":2026,"mapObject":{"objectType":"DISEASE","objectId":"demo-disease-1","routeCode":"G210","year":2026,"diseaseName":"坑槽","severity":"HEAVY","startStake":100.0,"endStake":100.2},"selectedLayers":["DISEASE","ASSESSMENT_RESULT"],"userQuestion":"分析当前选中病害并给出处置建议"},"options":{"useKnowledge":true,"useTools":true,"topK":3,"traceId":"phase50-16-live"}}'
  curl -fsS --max-time 240 -X POST "${SRMP_JAVA_URL:-http://localhost:8080}/api/agent/map-agent/chat" \
    -H 'Content-Type: application/json' -H 'X-Tenant-Id: default' --data "$BODY" >/tmp/srmp-phase50-16-java.json

  PYTHONPATH="$ROOT/srmp-ai-orchestrator" "$PYTHON_BIN" - <<'PY'
import json

probe_payload = json.load(open("/tmp/srmp-phase50-16-llm-probe.json", encoding="utf-8"))
probe = (probe_payload.get("data") or {}).get("body") or probe_payload.get("data") or probe_payload
if probe.get("status") == "SKIPPED":
    raise SystemExit("[FAIL] live LLM probe was skipped: " + json.dumps(probe, ensure_ascii=False)[:1000])
if probe.get("status") != "SUCCESS":
    raise SystemExit("[FAIL] live LLM probe failed: " + json.dumps(probe, ensure_ascii=False)[:1000])

payload = json.load(open("/tmp/srmp-phase50-16-java.json", encoding="utf-8"))
data = payload.get("data") or {}
inner = data.get("data") or {}
if inner.get("orchestratorProvider") != "langgraph":
    raise SystemExit("[FAIL] Java chat did not use LangGraph")
if inner.get("orchestratorFallback") is not False:
    raise SystemExit("[FAIL] Java chat fell back to native")
steps = (data.get("trace") or {}).get("steps") or []
llm_steps = [item for item in steps if item.get("name") == "llm_answer"]
if not llm_steps:
    raise SystemExit("[FAIL] Java response lacks llm_answer trace")
if llm_steps[0].get("status") != "SUCCESS":
    raise SystemExit("[FAIL] live llm_answer did not succeed: " + json.dumps(llm_steps[0], ensure_ascii=False)[:1000])
answer_meta = inner.get("answerMeta") or {}
if answer_meta.get("answerSource") != "LLM":
    raise SystemExit("[FAIL] live answer did not use LLM: " + json.dumps(answer_meta, ensure_ascii=False)[:1000])
print("[PASS] Phase50.16 live Java-routed LangGraph LLM check passed")
PY
fi
