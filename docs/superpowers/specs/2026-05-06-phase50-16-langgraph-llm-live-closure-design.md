# Phase50.16 LangGraph LLM Live Closure Design

## Background

Phase50.15 has made `srmp-ai-orchestrator` functionally close to the Java native one-map agent in deterministic no-LLM mode. The Java route can already call LangGraph and return `provider=langgraph`, `fallback=false`, tool plans, tool results, knowledge sources, trace steps, and native-compatible answer structure.

The remaining production gap is real model execution. `srmp-ai-orchestrator` has an OpenAI-compatible `LlmClient`, but the current runtime keeps `SRMP_LANGGRAPH_USE_LLM=false` by default and the Python LLM path is thinner than the native Java implementation. Native already records `llm_answer` as `SUCCESS`, `FAILED`, or `SKIPPED`, captures diagnostics, retries once with a compact prompt when the first answer is empty, and falls back to local deterministic answers without leaving the page blank. LangGraph needs the same closure before native can be removed.

## Goals

- Make LangGraph capable of real external LLM calls through the existing OpenAI-compatible configuration.
- Preserve deterministic no-LLM behavior when `SRMP_LANGGRAPH_USE_LLM=false`.
- Align LangGraph LLM trace semantics with native: `llm_answer` must clearly report `SUCCESS`, `FAILED`, or `SKIPPED`.
- Add diagnostics for configuration, request timing, status code, finish reason, empty answers, timeout/errors, retry state, and response preview.
- Retry once with a compact prompt when LLM is enabled but the first attempt returns empty content or an unusable response.
- Return explicit answer metadata so frontend pages can show whether the final answer came from LLM or deterministic fallback.
- Add local fake-LLM regression checks and optional live LLM verification gates.
- Keep native fallback available until the live LangGraph LLM path passes verification.

## Non-Goals

- Do not remove `NativeMapAgentOrchestrator` in Phase50.16.
- Do not make one-click startup require a real paid LLM key. Default startup remains deterministic and runnable offline.
- Do not expose API keys, Authorization headers, or raw full provider responses in runtime config, trace, audit, or frontend diagnostics.
- Do not move Java business tools or GIS database access into Python.
- Do not redesign the one-map assistant UI. Frontend changes are limited to displaying already returned LLM/source/trace metadata consistently.

## Current Gaps

### Python LLM Client

`srmp-ai-orchestrator/app/llm_client.py` currently returns only `Optional[str]`. It does not expose diagnostics, does not distinguish disabled/misconfigured/timeout/provider-error/empty-answer states, and uses hardcoded timeout values.

### Answer Generation Trace

`workflow.py` currently records generic `answer_generate` data with `useLlm` and `answerLength`. If LLM fails, it appends an `answer_generate` step with only the exception text. This is not compatible with the native `llm_answer` trace convention used by existing scripts and frontend diagnostics.

### Retry Behavior

Native retries once with a compact prompt when LLM is enabled and the first response is empty. LangGraph does not retry and can silently fall back without enough evidence for debugging.

### Runtime Observability

`/api/srmp/langgraph/runtime/config` reports whether LLM config exists, but there is no LangGraph-specific LLM health probe. Existing Java `/api/ai/llm/health?probe=true` only validates the Java LLM client, not Python LangGraph.

### Frontend Diagnostics

The AI health pages already understand Java LLM fields such as `status`, `enabled`, `model`, `readTimeoutMs`, `rawResponsePreview`, and `probeAnswerPreview`. The LangGraph ops page does not yet show equivalent LangGraph LLM probe status.

## Design

### LLM Client Result Contract

Replace the Python `LlmClient.chat(prompt) -> Optional[str]` boundary with a result object while keeping a small compatibility wrapper if needed.

The result object should include:

- `answer`: cleaned answer text or empty string.
- `status`: `SUCCESS`, `FAILED`, or `SKIPPED`.
- `enabled`: resolved `SRMP_LANGGRAPH_USE_LLM`.
- `model`: configured model name.
- `baseUrlConfigured`: boolean only, not the URL value unless already considered safe in config.
- `apiKeyConfigured`: boolean only.
- `costMs`: request duration.
- `httpStatus`: provider HTTP status when available.
- `finishReason`: provider finish reason when available.
- `answerChars`: cleaned answer length.
- `rawResponsePreview`: short redacted preview for provider response or message content.
- `errorType`: `DISABLED`, `MISCONFIGURED`, `TIMEOUT`, `HTTP_ERROR`, `PROVIDER_ERROR`, `EMPTY_RESPONSE`, `UNKNOWN`.
- `errorMessage`: short sanitized error message.

The LLM client must never return API keys or Authorization headers in diagnostics.

### Config Additions

Add LangGraph-specific LLM runtime settings:

- `SRMP_LANGGRAPH_LLM_CONNECT_TIMEOUT_SECONDS`, default `10`.
- `SRMP_LANGGRAPH_LLM_READ_TIMEOUT_SECONDS`, default `180`.
- `SRMP_LANGGRAPH_LLM_MAX_TOKENS`, default `2048`.
- `SRMP_LANGGRAPH_LLM_COMPACT_RETRY_ENABLED`, default `true`.
- `SRMP_LANGGRAPH_LLM_RAW_PREVIEW_CHARS`, default `500`.

`docker-compose.langgraph.yml`, runtime config payload, health diagnostics, and README should list these safe fields. Default `SRMP_LANGGRAPH_USE_LLM` remains `false`.

### Trace Semantics

`workflow._answer_generate` will emit a dedicated step:

- `name=node=llm_answer`
- message `大模型回答`
- `status=SKIPPED` when disabled or misconfigured with LLM disabled.
- `status=FAILED` when enabled but provider call fails, times out, or returns empty after retry.
- `status=SUCCESS` when the final LLM attempt returns usable text.
- `count=1` on success, otherwise omitted or `0`.
- `data` contains the LLM result diagnostics plus `retriedWithCompactPrompt` and `firstAttempt`.

`answer_generate` remains a separate deterministic step that reports:

- `answerSource`: `LLM` or `FALLBACK`.
- `llmSuccess`: boolean.
- `fallbackReason`: empty answer, disabled, misconfigured, failed, or quality fallback.
- `answerLength`: final answer length after deterministic enhancers.

### Compact Retry

When `settings.use_llm` is true:

1. Build the normal LangGraph prompt with existing GIS evidence and knowledge sources.
2. Call LLM once.
3. If the cleaned answer is empty and compact retry is enabled, build a compact prompt.
4. Call LLM a second time.
5. If the second attempt succeeds, return that answer and mark `retriedWithCompactPrompt=true`.
6. If both attempts fail, use deterministic fallback and mark `llm_answer=FAILED`.

The compact prompt should keep only the user question, intent, concise map context, top tool summaries, and top knowledge snippets. It should preserve enough evidence to avoid hallucination while reducing prompt length.

### Answer Metadata

LangGraph responses should expose the final answer origin in `data.answerMeta`:

- `answerSource`: `LLM` or `FALLBACK`.
- `llmSuccess`: boolean.
- `llmStatus`: `SUCCESS`, `FAILED`, or `SKIPPED`.
- `retriedWithCompactPrompt`: boolean.
- `fallbackReason`: present when source is `FALLBACK`.
- `llmModel`: configured model name when safe.

For compatibility with existing frontend checks, `data.answerSource` and `data.llmSuccess` may also be duplicated at the top level of `data`.

### LangGraph LLM Probe Endpoint

Add a LangGraph-specific probe endpoint:

`GET /api/srmp/langgraph/debug/llm-probe?probe=false`

Behavior:

- With `probe=false`, return safe config and last known diagnostics without calling the provider.
- With `probe=true`, call the Python LLM client with a tiny prompt that asks for `OK`.
- Return `available`, `status`, `enabled`, `model`, timeout settings, `probeAnswerPreview`, `probeCostMs`, and sanitized diagnostics.
- Return HTTP 200 for diagnostic failures so the ops page can display the problem; only malformed server requests should use HTTP errors.

### Frontend Ops Surface

Enhance `srmp-web-ui/src/views/agent/LangGraphOpsPage.vue` with a compact LangGraph LLM card:

- enabled/configured/model/status;
- read/connect timeout;
- last/probe cost;
- error type/message;
- probe preview;
- a `LLM 探测` action.

This is separate from the Java AI health page so operators can tell whether Java LLM is healthy while LangGraph LLM is disabled, misconfigured, or failing.

### Security And Audit

- Runtime config and audit must store only safe booleans for API key/base-url existence unless the field is already intentionally visible.
- Diagnostics must redact keys using the existing audit redaction key list.
- `rawResponsePreview` must be capped and must not include request headers.
- Live verification scripts must not echo environment secrets.

## Data Flow

1. Frontend calls `/api/agent/map-agent/chat`.
2. Java forwards to LangGraph when provider is `langgraph`.
3. LangGraph normalizes context, recognizes intent, plans tools, executes Java Tool Gateway calls, and fuses evidence.
4. `answer_generate` builds the normal prompt.
5. `llm_answer` calls the external OpenAI-compatible model when enabled.
6. If the first LLM answer is empty, LangGraph retries with a compact prompt.
7. If LLM succeeds, deterministic enhancers polish the LLM answer.
8. If LLM fails or is skipped, deterministic fallback plus enhancers produce the final answer.
9. `quality_guard` removes leaked thinking tags, enforces prefix and length safeguards, and records any final fallback.
10. Response returns `answerMeta`, `trace.steps`, `sources`, `toolResults`, and LangGraph provider metadata.

## Error Handling

- Disabled LLM is `SKIPPED`, not `FAILED`.
- Missing base URL, model, or API key is `FAILED` only when `SRMP_LANGGRAPH_USE_LLM=true`; otherwise it is `SKIPPED`.
- Timeout, HTTP non-2xx, invalid JSON, missing choices, empty content, and provider exceptions are `FAILED`.
- Failed LLM must not make the final answer empty. Deterministic fallback remains mandatory.
- Trace and response metadata must make the fallback reason visible.
- If LLM succeeds but `quality_guard` replaces the answer because it is too short, `answerMeta.answerSource` should become `FALLBACK` or include `qualityFallback=true`.

## Testing

### Local Deterministic Checks

Add `scripts/check-phase50-16-langgraph-llm-live-closure.sh` with fake LLM clients:

- disabled LLM produces `llm_answer=SKIPPED`, `answerSource=FALLBACK`, and a non-empty answer.
- fake success produces `llm_answer=SUCCESS`, `answerSource=LLM`, and expected metadata.
- fake empty first attempt plus success retry produces `retriedWithCompactPrompt=true` and preserves `firstAttempt`.
- fake provider failure produces `llm_answer=FAILED`, diagnostic error fields, and fallback answer.
- probe endpoint response shape can be imported or called locally without secrets.

### Static Checks

The script should assert:

- `llm_client.py` defines a diagnostics/result contract.
- `workflow.py` emits `llm_answer`.
- runtime config includes the new timeout/retry fields.
- `LangGraphOpsPage.vue` has a LangGraph LLM probe surface.

### Optional Live Checks

When `SRMP_PHASE50_16_LIVE=1`:

- require `SRMP_LANGGRAPH_USE_LLM=true`.
- call `/api/srmp/langgraph/debug/llm-probe?probe=true`.
- call Java `/api/agent/map-agent/chat` through the normal frontend/backend path.
- assert `orchestratorProvider=langgraph`, `orchestratorFallback=false`.
- assert `trace.steps` contains `llm_answer`.
- assert `llm_answer.status` is not `SKIPPED`.
- accept either `SUCCESS` or `FAILED` only if failed diagnostics are complete; production acceptance should require `SUCCESS`.

## Rollout

1. Implement diagnostics-rich Python LLM client and compact retry.
2. Add response answer metadata and trace parity.
3. Add LangGraph LLM probe endpoint and ops-page display.
4. Add deterministic fake-LLM verification.
5. Rebuild `backend` and `srmp-ai-orchestrator`.
6. Run no-LLM, fake-LLM, and optional real LLM live checks.
7. Keep native fallback until real LLM live checks pass repeatedly.
8. Start a later native-removal phase only after LangGraph LLM live closure is accepted.

## Acceptance Criteria

- No-LLM default remains runnable and deterministic.
- With LLM disabled, trace includes `llm_answer=SKIPPED` and final answer is non-empty.
- With fake LLM success, trace includes `llm_answer=SUCCESS`, and response metadata reports `answerSource=LLM`.
- With fake empty or failed LLM, trace includes diagnostics and final answer falls back without blank UI.
- LangGraph LLM probe returns safe diagnostics and never leaks secrets.
- Live Java-routed LangGraph smoke can call real LLM when explicitly enabled.
- Frontend ops page shows LangGraph LLM health separately from Java LLM health.
- Existing Phase50.14 and Phase50.15 parity checks continue to pass.
