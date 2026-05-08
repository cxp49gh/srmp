# Phase50.15 LangGraph Native Parity Design

## Background

Phase50.13 makes the default Docker path start `srmp-ai-orchestrator` and routes the Java backend to LangGraph. A live `/api/agent/map-agent/chat` smoke now returns `provider=langgraph`, `fallback=false`, tool plans, tool results, knowledge sources, and trace steps. The remaining gap is functional parity: the Python LangGraph runtime can run the one-map agent loop, but it does not yet cover every behavior that `NativeMapAgentOrchestrator` provides.

The desired end state is that LangGraph implements the full native one-map AI capability. After parity tests and live verification pass, the Java native orchestrator can be removed. Until then, native remains only as a safety net.

## Goals

- Make `srmp-ai-orchestrator` cover the complete one-map AI assistant behavior currently implemented by `NativeMapAgentOrchestrator`.
- Align LangGraph intent names, tool planning, answer structure, trace semantics, sources, and fallback behavior with the Java native contract.
- Keep Java as the business Tool Gateway during parity. LangGraph must not connect directly to SRMP business tables.
- Add deterministic parity checks for ordinary chat, knowledge QA, disease object analysis, assessment result analysis, road route/section analysis, nearby analysis, region analysis, template verification, and solution-generation intent recognition.
- Make the default configured path fail visibly when LangGraph falls back to native during parity verification.
- Define a separate removal gate for deleting or disabling native after LangGraph parity is proven.

## Non-Goals

- Do not delete `NativeMapAgentOrchestrator` in Phase50.15.
- Do not move GIS SQL or template persistence directly into Python.
- Do not enable uncontrolled write tools in LangGraph. Save/generate operations must either remain read-only capability hints or call existing Java APIs through explicit gated tools.
- Do not redesign the frontend chat UI in this phase. Frontend work is limited to consuming already returned `trace`, `sources`, `toolResults`, and provider/fallback metadata.
- Do not require a real external LLM for deterministic acceptance tests. Tests must pass in the no-LLM fallback mode.

## Current Gaps

### Intent Parity

Native uses these business intents:

- `OBJECT_ANALYSIS`
- `REGION_ANALYSIS`
- `NEARBY_ANALYSIS`
- `KNOWLEDGE_QA`
- `SOLUTION_GENERATE`
- `TEMPLATE_VERIFY`
- `TASK_SAVE`
- `GENERAL_CHAT`

LangGraph currently uses partially different labels such as `ASSESSMENT_ANALYSIS` and `MAINTENANCE_ADVICE`. Phase50.15 will make LangGraph emit the native intent set at the API boundary. It may keep internal subtypes in `data.intentDetail`, but `response.intent` must remain compatible with Java and frontend expectations.

### Tool Planning Parity

Native tool planning is object-type driven:

- route objects call assessment and disease query tools;
- road section objects call assessment, disease, and stake-range disease tools;
- disease objects call nearby-object tools;
- assessment/evaluation-unit objects call assessment and stake-range disease tools;
- region analysis calls region summary;
- template verification calls template match;
- explicit solution generation calls solution draft capability;
- knowledge is used by default when `useKnowledge` is absent or true.

LangGraph will implement the same planning rules and add structured plan reasons. `toolPlan` must be present in both `response.data.toolPlan` and trace steps so the frontend and ops page can explain why each tool was used.

### Answer Parity

Native includes answer enhancement for:

- disease objects, including crack, pothole, subsidence, rutting, and repair damage;
- assessment result objects, including MQI/PQI/PCI/RQI/RDI interpretation and unit disease linkage;
- road route and road section objects, including route/section-level risk and maintenance priorities;
- crack terminology enrichment when the current object or user question is crack-related;
- final answer polishing.

LangGraph will add Python answer-enhancement modules with equivalent deterministic behavior. LLM output remains optional; when LLM is disabled or empty, fallback answers must still include the same business sections and object-specific advice expected from native.

### Trace And Observability Parity

The returned trace must remain useful for ordinary chat, object analysis, route analysis, and region analysis:

- normalized request and context build;
- intent recognition;
- context enrichment;
- tool planning;
- tool execution;
- evidence fusion;
- answer generation;
- quality guard;
- errors and skipped steps.

Each step should include status, elapsed time, count, and useful data. Runtime audit records should preserve the request, response summary, provider, fallback flag, trace id, intent, tool counts, source count, and error message.

## Design

### Runtime Package Boundaries

`srmp-ai-orchestrator/app/workflow.py` should remain the graph coordinator. It should not accumulate all business rules. New focused modules will be introduced:

- `intent.py`: native-compatible intent recognition and intent detail extraction.
- `planner.py`: native-compatible tool planning with object-type helpers.
- `answer_enhancers.py`: deterministic business answer enhancements.
- `evidence.py`: source/tool/result normalization helpers used by prompts, fallback answers, and trace data.

`workflow.py` will call these modules from existing graph nodes. This keeps the graph readable and makes parity tests possible without starting FastAPI.

### API Contract

The Java-facing `MapAiAgentResponse` contract remains stable:

- `answer`
- `mode=LANGGRAPH_AGENT`
- `intent`
- `mapContext`
- `toolResults`
- `knowledgeSources`
- `sources`
- `trace`
- `data`

`data` will include:

- `orchestratorProvider=langgraph`
- `orchestratorFallback=false`
- `intent`
- `intentDetail`
- `toolPlan`
- `toolResults`
- `toolTotalCount`
- `toolSuccessCount`
- `toolFailedCount`
- `sourceCount`
- `mapObjectUsed`
- `regionUsed`
- `readOnly`
- `parityVersion`

Unknown or future fields must not break Java deserialization.

### Read-Only And Write-Like Operations

Phase50.15 keeps LangGraph read-only by default. For `SOLUTION_GENERATE`, `TASK_SAVE`, and template-related flows:

- LangGraph may plan `template.match` and `solution.generateDraft` only if those tools are in the runtime allowlist.
- Write-like tools stay blocked unless both Java Tool Gateway and LangGraph explicitly allow writes.
- The assistant answer must not claim a task was saved when write tools are disabled.
- The frontend save buttons continue to call existing Java save APIs after user confirmation.

This preserves safety while still allowing LangGraph to understand and explain solution-generation or save intent.

### Native Removal Gate

Native can be removed only after all conditions pass:

- parity script passes in local no-LLM mode;
- live Java endpoint returns `provider=langgraph` and `fallback=false` for all parity payloads;
- frontend one-map assistant displays answer, trace, sources, and tool results for object, route, and region flows;
- ops page shows no recent native fallback during the parity run;
- one-click startup ready check validates LangGraph provider and Tool Gateway contract;
- there is a documented rollback plan to restore the prior provider behavior if production deployment fails.

After this gate, a later phase can delete or disable `NativeMapAgentOrchestrator`, simplify `AgentOrchestratorRouter`, and remove native fallback configuration.

## Data Flow

1. Frontend calls `/api/agent/map-agent/chat`.
2. Java controller receives the same request contract as today.
3. Java `RemoteLangGraphOrchestrator` forwards the request to `srmp-ai-orchestrator`.
4. LangGraph normalizes request and context.
5. LangGraph recognizes a native-compatible intent.
6. LangGraph plans Java Tool Gateway calls.
7. LangGraph executes allowed tools through Java only.
8. LangGraph fuses GIS and knowledge evidence.
9. LangGraph generates an LLM answer or deterministic fallback answer.
10. LangGraph applies deterministic native-parity answer enhancers.
11. LangGraph returns the Java-compatible response.
12. Java returns the response to the frontend without native fallback.

## Error Handling

- If request normalization fails, return a failed LangGraph response with trace details instead of silently returning partial data.
- If one tool fails, continue with successful tools and record the failed tool in `data.evidence.failedTools`.
- If all tools fail, return a conservative answer that names the failed tools and missing data.
- If LLM returns empty content, use deterministic fallback and mark the trace step accordingly.
- If LangGraph response cannot be converted by Java, that is a parity failure and must be covered by contract tests.
- During Phase50.15, Java native fallback may still run in production configuration, but acceptance tests must treat any fallback as a failure.

## Testing

### Unit And Contract Tests

- Add Python tests or shell-driven Python checks for intent recognition parity.
- Add planner tests for route, section, disease, assessment, region, template, solution, and general-chat payloads.
- Add answer enhancer tests for disease, assessment, route, and section objects.
- Keep Java contract tests that ensure LangGraph source/tool fields do not break DTO conversion.

### Runtime Parity Script

Add `scripts/check-phase50-15-langgraph-native-parity.sh` with deterministic payloads:

- ordinary chat;
- knowledge QA;
- disease object analysis;
- assessment result analysis;
- road route analysis;
- road section analysis;
- nearby analysis;
- region polygon analysis;
- template verification;
- solution-generation request.

The script should assert:

- direct LangGraph response uses native-compatible intent;
- Java `/api/agent/map-agent/chat` returns `orchestratorProvider=langgraph`;
- `orchestratorFallback=false`;
- expected tool plan entries exist;
- `toolResults`, `sources`, and `trace.steps` are present when expected;
- answer includes required object-specific section markers.

### Live Smoke

After code changes, rebuild `backend` and `srmp-ai-orchestrator`, then run the parity script against `localhost:8080` and `localhost:5173` proxy paths.

## Rollout

1. Implement parity behind the existing LangGraph provider.
2. Keep native fallback enabled for manual use while tests mature.
3. Make parity checks part of the local verification list.
4. After user acceptance, run a short period with LangGraph as default and fallback metrics monitored.
5. In a later phase, remove native fallback and simplify Java orchestration.

## Acceptance Criteria

- The Phase50.15 parity script passes.
- Direct LangGraph and Java-routed LangGraph responses use the native intent set.
- Disease, assessment, route/section, and region answers contain deterministic business guidance in no-LLM mode.
- Java does not fallback to native during parity payloads.
- Frontend receives and can display `trace`, `sources`, `toolResults`, and provider metadata.
- No direct Python database access is introduced.
