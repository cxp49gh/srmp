# AI Trace Diagnosis Workbench Design

## Background

The current `/agent/ai-traces` page can list AI executions and open execution details, but it still reads like logs. Administrators need to answer a simpler question first: why did this AI run produce this answer, fallback, or warning?

Recent map AI work has already recorded the useful raw material: `answerMeta`, tool calls, evidence, capability, policy checks, plan execution, quality flags, and legacy trace diagnostics. The next step should be a diagnosis layer that turns those fields into a clear operational conclusion.

## Goals

- Put a concise diagnosis conclusion at the top of the trace detail and execution drawer.
- Explain model usage, fallback reason, failed tools, missing evidence, policy divergence, and legacy metadata gaps in administrator-friendly language.
- Make known knowledge-base failures actionable, especially `no embedded chunks`, `no knowledge chunks`, empty query, and pgvector failures.
- Avoid showing confusing runtime warnings like missing `answerMeta` as a user-facing error while a run is still in progress.
- Keep the first implementation frontend-only and derived from existing execution payloads.

## Non-Goals

- Do not change the execution database schema in this iteration.
- Do not redesign the whole AI governance workbench.
- Do not alter agent planning, tool routing, GIS statistics, or solution generation behavior.
- Do not hide raw logs or JSON diagnostics; keep them available after the diagnosis summary.

## Recommended Approach

Add a shared frontend diagnosis model in `srmp-web-ui/src/views/agent/components/aiExecution.ts`.

The normalizer should continue producing `AiExecutionSnapshot`, but the snapshot should gain a `diagnosis` field. This field is derived from existing normalized data, so both `AiTracesPage.vue` and `AiTraceDrawer.vue` can render the same conclusion without duplicating rules.

This approach is preferred because it gives administrators immediate value with low risk. Backend endpoints already expose enough data for the first diagnostic pass, and a frontend-only layer keeps the change reversible if later backend diagnosis APIs are added.

## Diagnosis Model

The diagnosis should include:

- `severity`: `success`, `info`, `warning`, or `danger`.
- `title`: short conclusion, such as "知识库向量未就绪" or "工具调用失败".
- `summary`: one or two sentences explaining what happened.
- `cause`: specific reason extracted from answer meta, tools, evidence, policy checks, or legacy diagnostics.
- `actions`: administrator next steps, reused from existing repair-action logic when possible.
- `tags`: compact facts such as LLM used, fallback, tool failure count, evidence count, or legacy record.

## Diagnosis Rules

Apply the most specific high-value conclusion first:

1. Running execution: show execution progress, and suppress final-only missing metadata warnings.
2. Tool failure: show failed tool names and the first useful error or diagnostic.
3. Knowledge fallback:
   - `no embedded chunks`: explain that documents exist but vector embeddings are missing or unavailable.
   - `no knowledge chunks`: explain that no usable knowledge chunks were found.
   - empty query: explain that the knowledge query was empty.
   - pgvector errors: explain vector search capability or database readiness problems.
4. Missing required business evidence: show that GIS or business tools did not return usable evidence.
5. Plan divergence or failed policy checks: show missing, extra, or blocked tools.
6. Missing `answerMeta` on completed records: mark as old task, old interface, or non-LangGraph pipeline evidence gap.
7. Healthy execution: show that model source, tools, and evidence are complete enough.

## UI Changes

In `/agent/ai-traces`:

- Add a "诊断结论" block at the top of the selected execution detail.
- Keep the current capability, plan, tool, evidence, timeline, and raw JSON sections below it.
- Show diagnosis severity using the existing Element Plus alert/tag patterns.

In `AiTraceDrawer.vue`:

- Place the same diagnosis block near the top, before timeline and raw diagnostics.
- Keep `AnswerSourceAlert`, but let the new diagnosis block explain broader operational causes.

The wording should be administrator-facing and concrete. For example, `no embedded chunks` should render as "知识库向量未就绪", not only as a raw fallback string.

## Testing

Add focused frontend tests around the pure diagnosis builder:

- `no embedded chunks` produces a knowledge-vector diagnosis with repair actions.
- Completed execution without `answerMeta` is diagnosed as legacy or metadata missing.
- Failed tools are prioritized over generic warnings.
- Missing business evidence is visible when a business capability expects it.
- Failed policy checks or plan divergence are visible.
- Healthy execution produces a success diagnosis.

Run the relevant frontend tests and, if practical, a frontend build. Backend tests are not required for this frontend-only iteration.

## Rollout

This is safe to ship behind the current `/agent/ai-traces` page because it only changes presentation of existing data. If later administrators need filtering or aggregation by diagnosis type, the same frontend model can guide a backend persisted diagnosis field.
