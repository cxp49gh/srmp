# Phase50.13 One-Click Bootstrap Design

## Background

Phase50.12 closed the LangGraph runtime, Java ops proxy, and frontend ops page loop. The existing one-click startup already starts PostGIS, Redis, MinIO, the Java backend, and the Vue frontend, and it initializes demo schemas and sample templates. The remaining gap is that `srmp-ai-orchestrator` is still started through a separate Compose file, while the default Java container remains effectively independent from the LangGraph runtime unless operators know which environment variables to set.

## Goals

- Make the default Docker one-click path start the full demo stack: database dependencies, Java backend, `srmp-ai-orchestrator`, and frontend.
- Route Java demo containers to LangGraph by default in the one-click path, while preserving an explicit opt-out for native-only startup.
- Keep database initialization idempotent and continue to load business demo data, solution templates, Trace tables, and template effectiveness sample data.
- Upgrade readiness checks to verify demo data thresholds, sample template presence, LangGraph runtime health, Java ops proxy health, and frontend availability.
- Document the full startup and verification path so a fresh checkout can reproduce ordinary AI chat, GIS object analysis, region solution generation, template usage visibility, Trace, and LangGraph ops diagnostics.

## Non-Goals

- Do not change business AI generation behavior.
- Do not require a real LLM key for default startup; LangGraph remains usable in deterministic/no-LLM mode.
- Do not introduce Flyway or Liquibase.
- Do not reset user data by default.
- Do not make local Java/Node development depend on Python LangGraph unless explicitly requested later.

## Design

The one-click script will keep `docker-compose.yml` as the dependency layer and `docker-compose.app.yml` as the Java/frontend app layer. For default Docker startup it will add `docker-compose.langgraph.yml` as a third layer and start `backend`, `srmp-ai-orchestrator`, and `frontend` together.

The Java backend container receives `SRMP_AI_ORCHESTRATOR_PROVIDER=langgraph` and `SRMP_LANGGRAPH_URL=http://srmp-ai-orchestrator:18080` by default. `--no-orchestrator` switches the one-click path back to native Java orchestration and skips LangGraph readiness checks.

The LangGraph container continues to call Java only through the Tool Gateway using `SRMP_JAVA_BASE_URL=http://backend:8080`. Runtime audit persistence is enabled in Docker mode and stored in a named volume so recent diagnostics survive container restarts.

`scripts/srmp-check-ready.sh` becomes the single post-start gate. It checks dependency containers, demo data thresholds, sample template rows, backend APIs, direct LangGraph health/ready endpoints, Java ops proxy endpoints, and the frontend page. Optional expensive AI smoke can be added later, but Phase50.13 keeps the default ready check fast and deterministic.

## User Flow

1. Run `./scripts/srmp-one-click-start.sh`.
2. Open `http://localhost:5173`.
3. Use `/gis/one-map` for object/route/region AI flows.
4. Use `/agent/langgraph-ops` to inspect provider, ready state, tool contract, runtime config, recent calls, replay/export, persistence, and prune controls.
5. Run `./scripts/srmp-one-click-start.sh --check-only` to re-run the same readiness gate.

## Error Handling

- If Docker is missing, startup fails before changing data.
- If PostgreSQL is slow, the startup script waits up to 120 seconds.
- If demo templates are missing, ready check reports the template table counts.
- If LangGraph is unavailable, ready check reports direct runtime failures and Java ops proxy failures separately.
- If `--no-orchestrator` is set, LangGraph checks are skipped and backend provider is forced to `native`.

## Testing

- Add `scripts/check-phase50-13-one-click-bootstrap.sh` as the static acceptance gate.
- Run existing one-click static check.
- Run Bash syntax checks for startup scripts.
- Run Compose config validation for the combined Docker stack when Docker CLI is available.
- Run Python compile for `srmp-ai-orchestrator`.
- Run frontend build to catch API/router regressions.
