# Phase50.19 LangGraph Wait Diagnostics Design

## Goal

Improve the GIS one-map AI experience when LangGraph calls take 30 seconds or more, and provide a quick diagnostic path that does not trigger LLM generation.

## Scope

- Keep `/api/agent/map-agent/run` and LangGraph generation behavior unchanged.
- Reuse existing Java orchestrator ops endpoints for quick diagnostics.
- Add frontend-only wait feedback in the one-map AI assistant.
- Add small testable utilities for elapsed-time formatting and diagnostic normalization.

## User Experience

- While an AI request is running, the assistant shows elapsed time.
- After 8 seconds, the assistant explains that LangGraph may still be retrieving evidence or generating with the LLM.
- After completion, assistant metadata includes total elapsed time, LLM status, model when present, and Trace remains available.
- A "状态诊断" action in the assistant fetches runtime health/summary without invoking LLM.

## Data Flow

- Chat/analysis calls still use `mapAgentRun`.
- Diagnostics use:
  - `GET /api/agent/orchestrator/ops/health-detail?includeGateway=false&includeContract=false`
  - `GET /api/agent/orchestrator/ops/summary?recentLimit=3`
- The frontend normalizes the two responses into status chips: Runtime, Tool Gateway, Contract, LLM config, success rate, average cost.

## Non-Goals

- No streaming response.
- No cancellation API.
- No LLM prompt changes.
- No new database table.

## Verification

- Unit tests for elapsed wait feedback and diagnostic summary normalization.
- `npm run build`.
- Playwright smoke for `/gis/one-map`: assistant visible, diagnostic action returns status, no console errors.
