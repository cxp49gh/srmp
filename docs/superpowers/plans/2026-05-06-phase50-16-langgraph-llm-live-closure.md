# Phase50.16 LangGraph LLM Live Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `srmp-ai-orchestrator` safely perform real OpenAI-compatible LLM calls with native-compatible trace diagnostics, retry behavior, answer metadata, ops probing, and regression gates.

**Architecture:** Keep Java as the Tool Gateway and keep no-LLM deterministic behavior as the default startup path. Add a diagnostics-rich Python LLM result contract, wire it into `workflow.py` as a dedicated `llm_answer` trace step, expose safe probe/config metadata through FastAPI and Java ops proxy, and show LangGraph LLM health in the existing Vue ops page.

**Tech Stack:** Python 3.11, FastAPI, httpx, LangGraph, Pydantic, Bash/Python acceptance checks, Java 8 Spring Boot ops proxy, Vue 3 + Element Plus frontend.

---

## File Structure

- Modify `srmp-ai-orchestrator/app/config.py`: add LangGraph LLM timeout, token, retry, and preview settings.
- Replace `srmp-ai-orchestrator/app/llm_client.py`: introduce `LlmResult`, diagnostics helpers, `generate()`, compatible `chat()`, and safe probe support.
- Modify `srmp-ai-orchestrator/app/prompt.py`: add compact answer prompt builder used for one retry after empty LLM output.
- Modify `srmp-ai-orchestrator/app/workflow.py`: record `llm_answer`, compute `answerMeta`, preserve legacy fake `chat()` compatibility, and return metadata in `data`.
- Modify `srmp-ai-orchestrator/app/main.py`: include safe LLM config fields and add `/api/srmp/langgraph/debug/llm-probe`.
- Modify `docker-compose.langgraph.yml`: pass the new safe LLM env vars into the Python runtime.
- Modify `srmp-ai-orchestrator/README.md`: document no-LLM default, real LLM enablement, and probe usage.
- Modify `srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/controller/AgentOrchestratorOpsController.java`: proxy `/api/agent/orchestrator/ops/llm-probe` to LangGraph.
- Modify `srmp-web-ui/src/api/orchestrator.ts`: add `probeOrchestratorLlm()`.
- Modify `srmp-web-ui/src/views/agent/LangGraphOpsPage.vue`: add a compact LangGraph LLM card and probe button.
- Create `scripts/check-phase50-16-langgraph-llm-live-closure.sh`: deterministic fake-LLM gate plus optional live verification.

## Task 1: Add The Phase50.16 Acceptance Gate

**Files:**
- Create: `scripts/check-phase50-16-langgraph-llm-live-closure.sh`

- [ ] **Step 1: Write the failing acceptance script**

Create `scripts/check-phase50-16-langgraph-llm-live-closure.sh` with this content:

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
```

- [ ] **Step 2: Run the script and verify it fails before implementation**

Run:

```bash
bash scripts/check-phase50-16-langgraph-llm-live-closure.sh
```

Expected result before implementation:

```text
[FAIL] llm_client.py must define LlmResult
```

- [ ] **Step 3: Commit the failing gate**

Run:

```bash
chmod +x scripts/check-phase50-16-langgraph-llm-live-closure.sh
git add scripts/check-phase50-16-langgraph-llm-live-closure.sh
git commit -m "test: add LangGraph LLM live closure gate"
```

## Task 2: Add Diagnostics-Rich LLM Client And Runtime Config

**Files:**
- Modify: `srmp-ai-orchestrator/app/config.py`
- Replace: `srmp-ai-orchestrator/app/llm_client.py`
- Modify: `docker-compose.langgraph.yml`

- [ ] **Step 1: Add config fields**

In `srmp-ai-orchestrator/app/config.py`, add these fields inside `Settings` immediately after `llm_temperature`:

```python
    llm_connect_timeout_seconds: int = _int_env("SRMP_LANGGRAPH_LLM_CONNECT_TIMEOUT_SECONDS", 10)
    llm_read_timeout_seconds: int = _int_env("SRMP_LANGGRAPH_LLM_READ_TIMEOUT_SECONDS", 180)
    llm_max_tokens: int = _int_env("SRMP_LANGGRAPH_LLM_MAX_TOKENS", 2048)
    llm_compact_retry_enabled: bool = _bool_env("SRMP_LANGGRAPH_LLM_COMPACT_RETRY_ENABLED", True)
    llm_raw_preview_chars: int = _int_env("SRMP_LANGGRAPH_LLM_RAW_PREVIEW_CHARS", 500)
```

- [ ] **Step 2: Replace `llm_client.py`**

Replace `srmp-ai-orchestrator/app/llm_client.py` with this content:

```python
import json
import time
from dataclasses import asdict, dataclass
from typing import Any, Dict, Optional

import httpx

from .config import settings


SYSTEM_PROMPT = "你是公路养护 GIS 智能分析助手，回答必须严谨、可追溯、不能编造。"


@dataclass
class LlmResult:
    answer: str = ""
    status: str = "SKIPPED"
    enabled: bool = False
    model: str = ""
    baseUrlConfigured: bool = False
    apiKeyConfigured: bool = False
    costMs: int = 0
    httpStatus: Optional[int] = None
    finishReason: Optional[str] = None
    answerChars: int = 0
    rawResponsePreview: str = ""
    errorType: str = ""
    errorMessage: str = ""

    @classmethod
    def skipped(cls, reason: str, error_type: str = "DISABLED") -> "LlmResult":
        return cls(
            status="SKIPPED",
            enabled=settings.use_llm,
            model=settings.llm_model,
            baseUrlConfigured=bool(settings.llm_base_url),
            apiKeyConfigured=bool(settings.llm_api_key),
            errorType=error_type,
            errorMessage=reason,
        )

    @classmethod
    def success(
        cls,
        answer: str,
        model: str = "",
        cost_ms: int = 0,
        finish_reason: Optional[str] = None,
        raw_response_preview: str = "",
        http_status: Optional[int] = None,
    ) -> "LlmResult":
        text = answer or ""
        return cls(
            answer=text,
            status="SUCCESS",
            enabled=True,
            model=model or settings.llm_model,
            baseUrlConfigured=bool(settings.llm_base_url),
            apiKeyConfigured=bool(settings.llm_api_key),
            costMs=cost_ms,
            httpStatus=http_status,
            finishReason=finish_reason,
            answerChars=len(text),
            rawResponsePreview=raw_response_preview,
        )

    @classmethod
    def failed(
        cls,
        error_type: str,
        error_message: str,
        model: str = "",
        cost_ms: int = 0,
        raw_response_preview: str = "",
        http_status: Optional[int] = None,
        finish_reason: Optional[str] = None,
    ) -> "LlmResult":
        return cls(
            status="FAILED",
            enabled=True,
            model=model or settings.llm_model,
            baseUrlConfigured=bool(settings.llm_base_url),
            apiKeyConfigured=bool(settings.llm_api_key),
            costMs=cost_ms,
            httpStatus=http_status,
            finishReason=finish_reason,
            rawResponsePreview=raw_response_preview,
            errorType=error_type,
            errorMessage=_sanitize_message(error_message),
        )

    def to_dict(self) -> Dict[str, Any]:
        return {key: value for key, value in asdict(self).items() if value not in (None, "")}


class LlmClient:
    def __init__(self):
        self._last_result: LlmResult = LlmResult.skipped("LLM 未调用")

    def diagnostics(self) -> Dict[str, Any]:
        return self._last_result.to_dict()

    async def chat(self, prompt: str) -> Optional[str]:
        result = await self.generate(prompt)
        return result.answer if result.status == "SUCCESS" and result.answer else None

    async def probe(self) -> Dict[str, Any]:
        result = await self.generate("请只返回 OK", system_prompt="你是健康检查助手，只返回 OK。")
        data = result.to_dict()
        data["available"] = result.status == "SUCCESS"
        data["probeAnswerPreview"] = _preview(result.answer)
        data["probeCostMs"] = result.costMs
        data["connectTimeoutSeconds"] = settings.llm_connect_timeout_seconds
        data["readTimeoutSeconds"] = settings.llm_read_timeout_seconds
        data["maxTokens"] = settings.llm_max_tokens
        data["temperature"] = settings.llm_temperature
        return data

    async def generate(self, prompt: str, system_prompt: Optional[str] = None) -> LlmResult:
        if not settings.use_llm:
            self._last_result = LlmResult.skipped("SRMP_LANGGRAPH_USE_LLM=false", "DISABLED")
            return self._last_result
        missing = []
        if not settings.llm_base_url:
            missing.append("SRMP_LLM_BASE_URL")
        if not settings.llm_api_key:
            missing.append("SRMP_LLM_API_KEY")
        if not settings.llm_model:
            missing.append("SRMP_LLM_MODEL")
        if missing:
            self._last_result = LlmResult.failed("MISCONFIGURED", "缺少配置：" + ", ".join(missing))
            return self._last_result

        started = time.perf_counter()
        url = settings.llm_base_url.rstrip("/") + "/chat/completions"
        headers = {
            "Authorization": f"Bearer {settings.llm_api_key}",
            "Content-Type": "application/json",
        }
        payload = {
            "model": settings.llm_model,
            "temperature": settings.llm_temperature,
            "max_tokens": settings.llm_max_tokens,
            "messages": [
                {"role": "system", "content": system_prompt or SYSTEM_PROMPT},
                {"role": "user", "content": prompt},
            ],
        }
        timeout = httpx.Timeout(
            float(settings.llm_read_timeout_seconds),
            connect=float(settings.llm_connect_timeout_seconds),
        )

        try:
            async with httpx.AsyncClient(timeout=timeout) as client:
                response = await client.post(url, json=payload, headers=headers)
            cost_ms = int((time.perf_counter() - started) * 1000)
            raw_preview = _preview(response.text)
            if response.status_code < 200 or response.status_code >= 300:
                self._last_result = LlmResult.failed(
                    "HTTP_ERROR",
                    "LLM HTTP 状态异常：" + str(response.status_code),
                    cost_ms=cost_ms,
                    raw_response_preview=raw_preview,
                    http_status=response.status_code,
                )
                return self._last_result
            body = response.json()
            self._last_result = _parse_response(body, cost_ms, response.status_code, raw_preview)
            return self._last_result
        except httpx.TimeoutException as exc:
            self._last_result = LlmResult.failed("TIMEOUT", str(exc), cost_ms=int((time.perf_counter() - started) * 1000))
            return self._last_result
        except json.JSONDecodeError as exc:
            self._last_result = LlmResult.failed("PROVIDER_ERROR", "LLM 返回非 JSON：" + str(exc), cost_ms=int((time.perf_counter() - started) * 1000))
            return self._last_result
        except Exception as exc:  # noqa: BLE001
            self._last_result = LlmResult.failed("UNKNOWN", str(exc), cost_ms=int((time.perf_counter() - started) * 1000))
            return self._last_result


def _parse_response(body: Any, cost_ms: int, http_status: int, raw_preview: str) -> LlmResult:
    if not isinstance(body, dict):
        return LlmResult.failed("PROVIDER_ERROR", "LLM 返回结构不是对象", cost_ms=cost_ms, raw_response_preview=raw_preview, http_status=http_status)
    choices = body.get("choices")
    if not isinstance(choices, list) or not choices:
        return LlmResult.failed("EMPTY_RESPONSE", "LLM 返回 choices 为空", cost_ms=cost_ms, raw_response_preview=raw_preview, http_status=http_status)
    first = choices[0] if isinstance(choices[0], dict) else {}
    finish_reason = first.get("finish_reason")
    message = first.get("message") if isinstance(first.get("message"), dict) else {}
    content = message.get("content")
    answer = _clean_answer(content) if isinstance(content, str) else ""
    if not answer:
        return LlmResult.failed(
            "EMPTY_RESPONSE",
            "LLM 返回为空",
            cost_ms=cost_ms,
            raw_response_preview=raw_preview,
            http_status=http_status,
            finish_reason=str(finish_reason) if finish_reason is not None else None,
        )
    return LlmResult.success(
        answer=answer,
        cost_ms=cost_ms,
        finish_reason=str(finish_reason) if finish_reason is not None else None,
        raw_response_preview=_preview(answer) or raw_preview,
        http_status=http_status,
    )


def _clean_answer(content: str) -> str:
    text = content.strip()
    for start, end in (("<think>", "</think>"), ("<thinking>", "</thinking>")):
        while start in text and end in text:
            prefix, rest = text.split(start, 1)
            _, suffix = rest.split(end, 1)
            text = (prefix + suffix).strip()
    return text


def _preview(text: Any) -> str:
    raw = "" if text is None else str(text)
    raw = raw.replace(settings.llm_api_key, "***") if settings.llm_api_key else raw
    limit = max(0, int(settings.llm_raw_preview_chars or 500))
    return raw[:limit]


def _sanitize_message(text: Any) -> str:
    return _preview(text)
```

- [ ] **Step 3: Pass the new env vars to Docker**

In `docker-compose.langgraph.yml`, add these environment entries under `SRMP_LLM_MODEL`:

```yaml
      SRMP_LANGGRAPH_LLM_CONNECT_TIMEOUT_SECONDS: ${SRMP_LANGGRAPH_LLM_CONNECT_TIMEOUT_SECONDS:-10}
      SRMP_LANGGRAPH_LLM_READ_TIMEOUT_SECONDS: ${SRMP_LANGGRAPH_LLM_READ_TIMEOUT_SECONDS:-180}
      SRMP_LANGGRAPH_LLM_MAX_TOKENS: ${SRMP_LANGGRAPH_LLM_MAX_TOKENS:-2048}
      SRMP_LANGGRAPH_LLM_COMPACT_RETRY_ENABLED: ${SRMP_LANGGRAPH_LLM_COMPACT_RETRY_ENABLED:-true}
      SRMP_LANGGRAPH_LLM_RAW_PREVIEW_CHARS: ${SRMP_LANGGRAPH_LLM_RAW_PREVIEW_CHARS:-500}
```

- [ ] **Step 4: Verify Python compilation**

Run:

```bash
srmp-ai-orchestrator/.venv/bin/python -m py_compile srmp-ai-orchestrator/app/config.py srmp-ai-orchestrator/app/llm_client.py
```

Expected result: command exits with status `0`.

- [ ] **Step 5: Commit**

Run:

```bash
git add srmp-ai-orchestrator/app/config.py srmp-ai-orchestrator/app/llm_client.py docker-compose.langgraph.yml
git commit -m "feat: add LangGraph LLM diagnostics client"
```

## Task 3: Wire LLM Trace, Compact Retry, And Answer Metadata

**Files:**
- Modify: `srmp-ai-orchestrator/app/prompt.py`
- Modify: `srmp-ai-orchestrator/app/workflow.py`

- [ ] **Step 1: Add compact prompt builder**

In `srmp-ai-orchestrator/app/prompt.py`, append this function after `build_answer_prompt`:

```python
def build_compact_answer_prompt(
    request: MapAiAgentRequest,
    intent: str,
    context_summary: Dict[str, Any],
    tool_results: List[ToolResult],
    evidence: Dict[str, Any],
) -> str:
    tool_lines = []
    for item in tool_results[: min(settings.max_tool_items_in_prompt, 4)]:
        tool_lines.append(
            f"- {item.toolName}: success={item.success}, count={item.count}, summary={item.summary or item.reason}"
        )
    return "\n".join(
        [
            "你是道路养护一张图 AI 助手。请基于证据给出简洁、可追溯的建议，不要编造。",
            f"意图：{intent}",
            f"上下文：{context_summary}",
            f"证据：{evidence}",
            "工具摘要：",
            "\n".join(tool_lines) if tool_lines else "- 无工具结果",
            f"用户问题：{request.message}",
            "输出不超过 600 字，包含：主要发现、处置建议、数据不足说明。",
        ]
    )
```

- [ ] **Step 2: Import compact prompt and `LlmResult`**

In `srmp-ai-orchestrator/app/workflow.py`, replace:

```python
from .llm_client import LlmClient
from .prompt import build_answer_prompt, fallback_answer
```

with:

```python
from .llm_client import LlmClient, LlmResult
from .prompt import build_answer_prompt, build_compact_answer_prompt, fallback_answer
```

- [ ] **Step 3: Extend `AgentState`**

In `AgentState`, add:

```python
    answer_meta: Dict[str, Any]
```

- [ ] **Step 4: Add LLM helper methods to `LangGraphWorkflow`**

Inside `LangGraphWorkflow`, just before `_answer_generate`, add:

```python
    async def _call_llm(self, prompt: str) -> LlmResult:
        if not settings.use_llm:
            return LlmResult.skipped("SRMP_LANGGRAPH_USE_LLM=false", "DISABLED")
        if hasattr(self.llm_client, "generate"):
            result = await self.llm_client.generate(prompt)
            if isinstance(result, LlmResult):
                return result
            if isinstance(result, dict):
                return LlmResult(**result)
        if hasattr(self.llm_client, "chat"):
            try:
                answer = await self.llm_client.chat(prompt)
            except Exception as exc:  # noqa: BLE001
                return LlmResult.failed("UNKNOWN", str(exc), model=settings.llm_model)
            if answer:
                return LlmResult.success(answer=str(answer), model=settings.llm_model)
        return LlmResult.failed("EMPTY_RESPONSE", "LLM 返回为空", model=settings.llm_model)

    def _record_llm_step(
        self,
        state: AgentState,
        result: LlmResult,
        retried: bool,
        first_attempt: LlmResult,
    ) -> None:
        data = result.to_dict()
        data["retriedWithCompactPrompt"] = retried
        data["firstAttempt"] = first_attempt.to_dict()
        self._step(
            state,
            "llm_answer",
            "大模型回答",
            data,
            status=result.status,
            count=1 if result.status == "SUCCESS" else 0,
            error=result.errorMessage if result.status == "FAILED" else None,
        )

    def _answer_meta(self, result: LlmResult, source: str, retried: bool, fallback_reason: str = "") -> Dict[str, Any]:
        meta = {
            "answerSource": source,
            "llmSuccess": result.status == "SUCCESS" and source == "LLM",
            "llmStatus": result.status,
            "retriedWithCompactPrompt": retried,
            "llmModel": result.model or settings.llm_model,
        }
        if fallback_reason:
            meta["fallbackReason"] = fallback_reason
        if result.errorType:
            meta["errorType"] = result.errorType
        if result.errorMessage:
            meta["errorMessage"] = result.errorMessage
        return meta
```

- [ ] **Step 5: Replace `_answer_generate`**

Replace the existing `_answer_generate` method with:

```python
    async def _answer_generate(self, state: AgentState) -> Dict[str, Any]:
        request = state["request"]
        tool_results = state.get("tool_results", [])
        evidence = state.get("evidence", {})
        context_summary = state.get("context_summary", {})
        intent = state.get("intent", "KNOWLEDGE_QA")

        prompt = build_answer_prompt(request, intent, context_summary, tool_results, evidence)
        first_result = await self._call_llm(prompt)
        result = first_result
        retried = False

        if (
            settings.use_llm
            and settings.llm_compact_retry_enabled
            and result.status != "SUCCESS"
            and result.errorType in {"EMPTY_RESPONSE", "PROVIDER_ERROR", ""}
        ):
            compact_prompt = build_compact_answer_prompt(request, intent, context_summary, tool_results, evidence)
            result = await self._call_llm(compact_prompt)
            retried = True

        self._record_llm_step(state, result, retried, first_result)

        answer = result.answer if result.status == "SUCCESS" and result.answer else ""
        answer_source = "LLM" if answer else "FALLBACK"
        fallback_reason = ""
        if not answer:
            fallback_reason = result.errorType or ("DISABLED" if not settings.use_llm else "EMPTY_RESPONSE")
            answer = fallback_answer(request, intent, context_summary, evidence, tool_results)

        sources = extract_knowledge_sources(tool_results)
        answer = enhance_answer(answer, request, intent, state.get("intent_detail", {}), tool_results, sources)
        answer_meta = self._answer_meta(result, answer_source, retried, fallback_reason)

        self._step(
            state,
            "answer_generate",
            "生成回答",
            {
                "useLlm": settings.use_llm,
                "answerLength": len(answer),
                "answerSource": answer_meta.get("answerSource"),
                "llmSuccess": answer_meta.get("llmSuccess"),
                "fallbackReason": answer_meta.get("fallbackReason"),
            },
        )
        return {"answer": answer, "answer_meta": answer_meta}
```

- [ ] **Step 6: Make `quality_guard` update metadata when it replaces answer**

In `_quality_guard`, after `changed.append("short_answer_fallback")`, add:

```python
            meta = dict(state.get("answer_meta") or {})
            meta["answerSource"] = "FALLBACK"
            meta["llmSuccess"] = False
            meta["qualityFallback"] = True
            meta["fallbackReason"] = "QUALITY_GUARD_SHORT_ANSWER"
            state["answer_meta"] = meta
```

Then change the final return line from:

```python
        return {"answer": answer, "quality": quality}
```

to:

```python
        return {"answer": answer, "quality": quality, "answer_meta": state.get("answer_meta", {})}
```

- [ ] **Step 7: Return answer metadata in API response**

In `_to_response`, before `return MapAiAgentResponse(...)`, add:

```python
        answer_meta = state.get("answer_meta", {})
```

Inside the `data={...}` block, add:

```python
                "answerMeta": answer_meta,
                "answerSource": answer_meta.get("answerSource"),
                "llmSuccess": answer_meta.get("llmSuccess"),
```

- [ ] **Step 8: Run deterministic checks**

Run:

```bash
srmp-ai-orchestrator/.venv/bin/python -m py_compile srmp-ai-orchestrator/app/prompt.py srmp-ai-orchestrator/app/workflow.py
bash scripts/check-phase50-15-langgraph-native-parity.sh
bash scripts/check-phase50-16-langgraph-llm-live-closure.sh
```

Expected result:

```text
[PASS] Phase50.15 local LangGraph native parity checks passed
[PASS] Phase50.16 local LangGraph LLM live-closure checks passed
```

- [ ] **Step 9: Commit**

Run:

```bash
git add srmp-ai-orchestrator/app/prompt.py srmp-ai-orchestrator/app/workflow.py scripts/check-phase50-16-langgraph-llm-live-closure.sh
git commit -m "feat: add LangGraph LLM answer trace parity"
```

## Task 4: Add LangGraph LLM Probe API And Java Ops Proxy

**Files:**
- Modify: `srmp-ai-orchestrator/app/main.py`
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/controller/AgentOrchestratorOpsController.java`

- [ ] **Step 1: Import `LlmResult` helpers are not required**

Keep `srmp-ai-orchestrator/app/main.py` imports as they are. It already imports `LlmClient` and creates `workflow` with a client instance.

- [ ] **Step 2: Add safe LLM config fields**

In `_safe_runtime_config()`, immediately after `llmApiKeyConfigured`, add:

```python
        "llmConnectTimeoutSeconds": settings.llm_connect_timeout_seconds,
        "llmReadTimeoutSeconds": settings.llm_read_timeout_seconds,
        "llmMaxTokens": settings.llm_max_tokens,
        "llmCompactRetryEnabled": settings.llm_compact_retry_enabled,
        "llmRawPreviewChars": settings.llm_raw_preview_chars,
```

- [ ] **Step 3: Add FastAPI LLM probe endpoint**

In `srmp-ai-orchestrator/app/main.py`, after `debug_contract`, add:

```python
@app.get("/api/srmp/langgraph/debug/llm-probe")
async def debug_llm_probe(probe: bool = Query(default=False)) -> dict:
    client = workflow.llm_client
    diagnostics = client.diagnostics() if hasattr(client, "diagnostics") else {}
    data = {
        "enabled": settings.use_llm,
        "status": diagnostics.get("status") or ("READY" if settings.use_llm else "SKIPPED"),
        "available": False,
        "model": settings.llm_model,
        "baseUrlConfigured": bool(settings.llm_base_url),
        "apiKeyConfigured": bool(settings.llm_api_key),
        "connectTimeoutSeconds": settings.llm_connect_timeout_seconds,
        "readTimeoutSeconds": settings.llm_read_timeout_seconds,
        "maxTokens": settings.llm_max_tokens,
        "temperature": settings.llm_temperature,
        "compactRetryEnabled": settings.llm_compact_retry_enabled,
        "diagnostics": diagnostics,
    }
    if probe:
        if hasattr(client, "probe"):
            probe_data = await client.probe()
        else:
            answer = await client.chat("请只返回 OK") if hasattr(client, "chat") else ""
            probe_data = {"status": "SUCCESS" if answer else "FAILED", "available": bool(answer), "probeAnswerPreview": answer or ""}
        data.update(probe_data or {})
        data["available"] = data.get("status") == "SUCCESS"
    return data
```

- [ ] **Step 4: Add Java proxy endpoint**

In `srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/controller/AgentOrchestratorOpsController.java`, after the `contract()` method, add:

```java
    /**
     * 转发 Runtime 的 LangGraph LLM 探测。
     *
     * 与 /api/ai/llm/health 区分：这里验证 Python LangGraph Runtime 自己的 LLM 配置和调用链路。
     */
    @GetMapping("/llm-probe")
    public R<Object> llmProbe(@RequestParam(value = "probe", required = false, defaultValue = "false") Boolean probe) {
        return R.ok(remoteGet("/api/srmp/langgraph/debug/llm-probe?probe=" + Boolean.TRUE.equals(probe), 180000));
    }
```

Then add this overload near the existing `remoteGet(String path)` method:

```java
    private Map<String, Object> remoteGet(String path, int readTimeoutMs) {
        Map<String, Object> data = new LinkedHashMap<>();
        String url = buildUrl(properties.getLanggraphUrl(), path);
        data.put("url", url);
        long start = System.currentTimeMillis();
        try {
            RestTemplate restTemplate = buildRestTemplate(3000, readTimeoutMs);
            ResponseEntity<Object> entity = restTemplate.getForEntity(url, Object.class);
            data.put("ok", entity.getStatusCode().is2xxSuccessful());
            data.put("httpStatus", entity.getStatusCodeValue());
            data.put("costMs", System.currentTimeMillis() - start);
            data.put("body", entity.getBody());
        } catch (Exception e) {
            data.put("ok", false);
            data.put("costMs", System.currentTimeMillis() - start);
            data.put("error", e.getMessage());
        }
        return data;
    }
```

Change the existing `buildRestTemplate()` method from:

```java
    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(8000);
        return new RestTemplate(factory);
    }
```

to:

```java
    private RestTemplate buildRestTemplate() {
        return buildRestTemplate(3000, 8000);
    }

    private RestTemplate buildRestTemplate(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }
```

- [ ] **Step 5: Compile backend module and Python files**

Run:

```bash
srmp-ai-orchestrator/.venv/bin/python -m py_compile srmp-ai-orchestrator/app/main.py
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -pl srmp-agent test
```

Expected result: Python compile exits `0`; Maven ends with `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

Run:

```bash
git add srmp-ai-orchestrator/app/main.py srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/controller/AgentOrchestratorOpsController.java
git commit -m "feat: expose LangGraph LLM probe"
```

## Task 5: Add Frontend LangGraph LLM Ops Surface

**Files:**
- Modify: `srmp-web-ui/src/api/orchestrator.ts`
- Modify: `srmp-web-ui/src/views/agent/LangGraphOpsPage.vue`

- [ ] **Step 1: Add API wrapper**

In `srmp-web-ui/src/api/orchestrator.ts`, after `getOrchestratorContract()`, add:

```ts
export function probeOrchestratorLlm(probe = false): Promise<Record<string, any>> {
  return aiRequest.get('/api/agent/orchestrator/ops/llm-probe', {
    params: { probe }
  })
}
```

- [ ] **Step 2: Import API wrapper**

In `LangGraphOpsPage.vue`, add `probeOrchestratorLlm` to the import list from `../../api/orchestrator`.

- [ ] **Step 3: Add action button**

In the header actions template, after the `配置健康` button, add:

```vue
        <el-button :loading="llmProbeLoading" type="warning" plain @click="probeLangGraphLlm(true)">LLM 探测</el-button>
```

- [ ] **Step 4: Add LangGraph LLM metric card**

In the first `metric-grid`, after the `LangGraph Ready` card, add:

```vue
        <el-card shadow="never" class="metric-card">
          <div class="metric-head">
            <span>LangGraph LLM</span>
            <el-tag :type="langGraphLlmTagType">{{ langGraphLlmStatusText }}</el-tag>
          </div>
          <div class="metric-value">{{ langGraphLlmModel || '-' }}</div>
          <div class="metric-desc">enabled：{{ langGraphLlmEnabled ? 'true' : 'false' }}；retry：{{ langGraphLlmRetry ? 'true' : 'false' }}；{{ langGraphLlmCost }}</div>
        </el-card>
```

- [ ] **Step 5: Add LLM detail panel**

In the first `content-grid`, after the `配置健康` card, add:

```vue
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="card-header">
              <span>LangGraph LLM</span>
              <el-button size="small" text :loading="llmProbeLoading" @click="probeLangGraphLlm(true)">探测</el-button>
            </div>
          </template>
          <div class="diag-list">
            <div class="diag-row">
              <span>状态</span>
              <strong>{{ langGraphLlmStatusText }}</strong>
            </div>
            <div class="diag-row">
              <span>模型</span>
              <strong>{{ langGraphLlmModel || '-' }}</strong>
            </div>
            <div class="diag-row">
              <span>配置</span>
              <strong>base={{ langGraphLlmBaseConfigured ? 'yes' : 'no' }}；key={{ langGraphLlmKeyConfigured ? 'yes' : 'no' }}</strong>
            </div>
            <div class="diag-row">
              <span>超时/Token</span>
              <strong>{{ langGraphLlmConnectTimeout }}s / {{ langGraphLlmReadTimeout }}s / {{ langGraphLlmMaxTokens }}</strong>
            </div>
            <div class="diag-row">
              <span>最近耗时</span>
              <strong>{{ langGraphLlmCost }}</strong>
            </div>
            <div class="diag-row">
              <span>错误</span>
              <strong>{{ langGraphLlmError || '-' }}</strong>
            </div>
            <div class="diag-row">
              <span>响应预览</span>
              <strong>{{ langGraphLlmPreview || '-' }}</strong>
            </div>
          </div>
        </el-card>
```

- [ ] **Step 6: Add reactive state and computed values**

In `<script setup>`, after `const healthLoading = ref(false)`, add:

```ts
const llmProbeLoading = ref(false)
```

After `const persistenceResult = reactive<Record<string, any>>({})`, add:

```ts
const langGraphLlmResult = reactive<Record<string, any>>({})
```

After `const persistenceLastError = computed(...)`, add:

```ts
const langGraphLlmBody = computed(() => value(langGraphLlmResult, ['body']) || langGraphLlmResult)
const langGraphLlmEnabled = computed(() => Boolean(value(langGraphLlmBody.value, ['enabled'])))
const langGraphLlmStatusText = computed(() => String(value(langGraphLlmBody.value, ['status']) || (langGraphLlmEnabled.value ? 'READY' : 'SKIPPED')))
const langGraphLlmTagType = computed(() => {
  const status = langGraphLlmStatusText.value
  if (status === 'SUCCESS') return 'success'
  if (status === 'SKIPPED' || status === 'DISABLED') return 'info'
  if (status === 'FAILED' || status.includes('TIMEOUT') || status.includes('ERROR')) return 'danger'
  return 'warning'
})
const langGraphLlmModel = computed(() => String(value(langGraphLlmBody.value, ['model']) || value(configBody.value, ['safeConfig', 'llmModel']) || ''))
const langGraphLlmRetry = computed(() => Boolean(value(langGraphLlmBody.value, ['compactRetryEnabled']) ?? value(configBody.value, ['safeConfig', 'llmCompactRetryEnabled'])))
const langGraphLlmBaseConfigured = computed(() => Boolean(value(langGraphLlmBody.value, ['baseUrlConfigured']) ?? value(configBody.value, ['safeConfig', 'llmBaseUrlConfigured'])))
const langGraphLlmKeyConfigured = computed(() => Boolean(value(langGraphLlmBody.value, ['apiKeyConfigured']) ?? value(configBody.value, ['safeConfig', 'llmApiKeyConfigured'])))
const langGraphLlmConnectTimeout = computed(() => Number(value(langGraphLlmBody.value, ['connectTimeoutSeconds']) || value(configBody.value, ['safeConfig', 'llmConnectTimeoutSeconds']) || 0))
const langGraphLlmReadTimeout = computed(() => Number(value(langGraphLlmBody.value, ['readTimeoutSeconds']) || value(configBody.value, ['safeConfig', 'llmReadTimeoutSeconds']) || 0))
const langGraphLlmMaxTokens = computed(() => Number(value(langGraphLlmBody.value, ['maxTokens']) || value(configBody.value, ['safeConfig', 'llmMaxTokens']) || 0))
const langGraphLlmCost = computed(() => {
  const cost = value(langGraphLlmBody.value, ['probeCostMs']) ?? value(langGraphLlmBody.value, ['costMs'])
  return cost == null ? '-' : `${cost}ms`
})
const langGraphLlmError = computed(() => String(value(langGraphLlmBody.value, ['errorMessage']) || value(langGraphLlmBody.value, ['diagnostics', 'errorMessage']) || ''))
const langGraphLlmPreview = computed(() => String(value(langGraphLlmBody.value, ['probeAnswerPreview']) || value(langGraphLlmBody.value, ['rawResponsePreview']) || value(langGraphLlmBody.value, ['diagnostics', 'rawResponsePreview']) || ''))
```

- [ ] **Step 7: Load non-probe diagnostics with runtime details**

In `loadRuntimeDetails`, change the `Promise.allSettled` call to include `probeOrchestratorLlm(false)`:

```ts
    const [config, health, persistence, llmProbe] = await Promise.allSettled([
      getOrchestratorConfig(),
      getOrchestratorHealthDetail(true, true),
      getOrchestratorPersistence(),
      probeOrchestratorLlm(false)
    ])
```

Then after the persistence assignment, add:

```ts
    if (llmProbe.status === 'fulfilled') assignReactive(langGraphLlmResult, llmProbe.value || {})
```

- [ ] **Step 8: Add probe action**

Before `loadRecent`, add:

```ts
async function probeLangGraphLlm(runProbe = true) {
  llmProbeLoading.value = true
  try {
    const res = await probeOrchestratorLlm(runProbe)
    assignReactive(langGraphLlmResult, res || {})
    const body = value(res, ['body']) || res || {}
    ElMessage.success(body?.status === 'SUCCESS' ? 'LangGraph LLM 探测通过' : 'LangGraph LLM 探测完成')
  } catch (error: any) {
    assignReactive(langGraphLlmResult, error?.response?.data || { error: error?.message || String(error) })
    ElMessage.error('LangGraph LLM 探测失败')
  } finally {
    llmProbeLoading.value = false
  }
}
```

- [ ] **Step 9: Verify frontend type check/build path**

Run:

```bash
npm --prefix srmp-web-ui run build
```

Expected result: Vite build exits with status `0`.

- [ ] **Step 10: Commit**

Run:

```bash
git add srmp-web-ui/src/api/orchestrator.ts srmp-web-ui/src/views/agent/LangGraphOpsPage.vue
git commit -m "feat: show LangGraph LLM ops diagnostics"
```

## Task 6: Update Docs And Run Full Verification

**Files:**
- Modify: `srmp-ai-orchestrator/README.md`
- Modify: `docs/superpowers/specs/2026-05-06-phase50-16-langgraph-llm-live-closure-design.md` only if implementation intentionally diverged from the approved design.

- [ ] **Step 1: Update README LLM section**

In `srmp-ai-orchestrator/README.md`, replace the existing optional LLM section with:

````markdown
## 可选 LLM 配置

默认 `SRMP_LANGGRAPH_USE_LLM=false`，Runtime 不调用外部模型，只根据 Java Tool Gateway 的 GIS/知识库结果生成确定性兜底答案。这样一键启动和本地回归不依赖付费模型。

需要让 LangGraph Runtime 自己调用 OpenAI-compatible Chat Completion 时配置：

```bash
export SRMP_LANGGRAPH_USE_LLM=true
export SRMP_LLM_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
export SRMP_LLM_API_KEY=你的key
export SRMP_LLM_MODEL=qwen-plus
export SRMP_LANGGRAPH_LLM_CONNECT_TIMEOUT_SECONDS=10
export SRMP_LANGGRAPH_LLM_READ_TIMEOUT_SECONDS=180
export SRMP_LANGGRAPH_LLM_MAX_TOKENS=2048
export SRMP_LANGGRAPH_LLM_COMPACT_RETRY_ENABLED=true
```

验证 Python LangGraph LLM 链路：

```bash
curl -fsS 'http://localhost:8080/api/agent/orchestrator/ops/llm-probe?probe=true' | python3 -m json.tool
```

返回 `status=SUCCESS` 表示 Python Runtime 自己的 LLM 调用可用。返回 `SKIPPED` 表示未启用，返回 `FAILED` 时查看 `errorType`、`errorMessage`、`rawResponsePreview`、`probeCostMs`。
````

- [ ] **Step 2: Run full local verification**

Run:

```bash
bash scripts/check-phase50-14-langgraph-map-agent-parity.sh
bash scripts/check-phase50-15-langgraph-native-parity.sh
bash scripts/check-phase50-16-langgraph-llm-live-closure.sh
bash scripts/check-phase50-12-langgraph-closure.sh
srmp-ai-orchestrator/.venv/bin/python -m py_compile srmp-ai-orchestrator/app/*.py
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH mvn -pl srmp-agent test
npm --prefix srmp-web-ui run build
git diff --check
```

Expected result: every command exits with status `0`.

- [ ] **Step 3: Rebuild services for live smoke**

Run:

```bash
docker compose -f docker-compose.yml -f docker-compose.app.yml -f docker-compose.langgraph.yml up -d --build backend srmp-ai-orchestrator
```

Expected result: both services become healthy/running. Use this check:

```bash
docker compose -f docker-compose.yml -f docker-compose.app.yml -f docker-compose.langgraph.yml ps backend srmp-ai-orchestrator
```

- [ ] **Step 4: Run live no-LLM smoke**

Run:

```bash
SRMP_PHASE50_15_LIVE=1 bash scripts/check-phase50-15-langgraph-native-parity.sh
```

Expected result:

```text
[PASS] Phase50.15 live Java LangGraph parity smoke passed
```

- [ ] **Step 5: Run live real-LLM smoke when env is enabled**

If the current container was started with `SRMP_LANGGRAPH_USE_LLM=true`, run:

```bash
SRMP_PHASE50_16_LIVE=1 SRMP_LANGGRAPH_USE_LLM=true bash scripts/check-phase50-16-langgraph-llm-live-closure.sh
```

Expected result:

```text
[PASS] Phase50.16 live Java-routed LangGraph LLM check passed
```

If the current container keeps `SRMP_LANGGRAPH_USE_LLM=false`, record this exact limitation in the final response:

```text
Live real-LLM smoke was not run because SRMP_LANGGRAPH_USE_LLM=false; local fake-LLM and Java-routed no-LLM parity checks passed.
```

- [ ] **Step 6: Commit docs and verification script updates**

Run:

```bash
git add srmp-ai-orchestrator/README.md
git commit -m "docs: document LangGraph LLM live verification"
```

## Final Review Checklist

- [ ] `trace.steps` contains one `llm_answer` step for disabled, success, retry, and failed LLM paths.
- [ ] `llm_answer.status` is `SKIPPED` only when LLM is disabled or skipped by configuration.
- [ ] Enabled provider errors return `FAILED` diagnostics and never blank the final answer.
- [ ] `data.answerMeta.answerSource` is `LLM` only when the final model answer is used.
- [ ] `data.answerMeta.answerSource` is `FALLBACK` when deterministic fallback or quality guard replaces the answer.
- [ ] `runtime/config`, `debug/llm-probe`, Java ops proxy, frontend API, and frontend page do not expose API keys.
- [ ] Phase50.14 and Phase50.15 scripts still pass after Phase50.16 changes.
- [ ] `deploy/outline/*` untracked runtime files remain unstaged.
