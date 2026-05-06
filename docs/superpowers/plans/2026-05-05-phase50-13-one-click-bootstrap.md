# Phase50.13 One-Click Bootstrap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make one-click Docker startup launch and verify the full SRMP demo stack, including LangGraph runtime, initialized templates, demo data, Trace tables, backend ops proxy, and frontend.

**Architecture:** Keep the existing layered Compose model. Add `docker-compose.langgraph.yml` to the default one-click stack, set Java backend orchestration env vars in Docker mode, persist LangGraph audit records in a named volume, and extend readiness checks with deterministic data/template/runtime probes.

**Tech Stack:** Bash, Docker Compose, PostgreSQL/PostGIS SQL checks, Spring Boot Java backend, FastAPI LangGraph runtime, Vue/Vite frontend.

---

## Files

- Modify `docker-compose.app.yml`: expose Java orchestrator env vars for container startup.
- Modify `docker-compose.langgraph.yml`: enable runtime audit persistence and named volume.
- Modify `scripts/srmp-one-click-start.sh`: include LangGraph in default Docker startup and add `--no-orchestrator`.
- Modify `scripts/srmp-check-ready.sh`: verify templates, direct LangGraph health/ready, Java ops proxy, and native opt-out.
- Modify `scripts/check-one-click-start.sh`: keep the general one-click static gate aligned with the full stack.
- Create `scripts/check-phase50-13-one-click-bootstrap.sh`: phase-specific static acceptance gate.
- Create `docs/phase50_13_one_click_bootstrap.md`: operator-facing phase document.
- Modify `docs/one-click-start-guide.md` and `README.md`: describe the full-stack one-click path and ops URLs.

## Tasks

- [ ] **Task 1: Add the Phase50.13 failing acceptance gate**
  - Create `scripts/check-phase50-13-one-click-bootstrap.sh`.
  - Assert combined Compose contains `backend`, `frontend`, and `srmp-ai-orchestrator`.
  - Assert one-click script references `docker-compose.langgraph.yml`, `--no-orchestrator`, and `srmp-ai-orchestrator`.
  - Assert ready check probes template counts and LangGraph ops endpoints.
  - Run the script before implementation and confirm it fails.

- [ ] **Task 2: Wire default Docker startup to LangGraph**
  - Add backend env vars for `SRMP_AI_ORCHESTRATOR_PROVIDER`, `SRMP_LANGGRAPH_URL`, and LangGraph timeout/fallback controls.
  - Enable LangGraph audit persistence volume in Docker mode.
  - Update one-click script to use a combined Compose file list by default.
  - Add `--no-orchestrator` to force native provider and skip runtime service startup.

- [ ] **Task 3: Extend readiness checks**
  - Add `ORCHESTRATOR_URL` and `--no-orchestrator`.
  - Add database scalar checks for `ai_solution_template`, `ai_solution_template_version`, `ai_trace_log`, and `ai_trace_step`.
  - Add direct LangGraph `/health` and `/ready` checks.
  - Add Java ops proxy checks for `/summary`, `/config`, and `/health-detail`.
  - Keep frontend and backend-only behavior compatible.

- [ ] **Task 4: Update documentation**
  - Add `docs/phase50_13_one_click_bootstrap.md`.
  - Update `docs/one-click-start-guide.md` with the orchestrator URL, ops page, logs, opt-out, and check-only behavior.
  - Update README quick start to mention LangGraph runtime and `/agent/langgraph-ops`.

- [ ] **Task 5: Verify and commit**
  - Run `bash scripts/check-phase50-13-one-click-bootstrap.sh`.
  - Run `bash scripts/check-one-click-start.sh`.
  - Run `python3 -m py_compile srmp-ai-orchestrator/app/*.py`.
  - Run `npm --prefix srmp-web-ui run build`.
  - Run `git diff --check`.
  - Commit as `feat: add Phase50.13 one-click LangGraph bootstrap`.
