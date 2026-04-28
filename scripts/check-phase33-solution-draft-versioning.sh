#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

test -f srmp-admin/src/main/resources/db/phase33_ai_solution_draft_version.sql
grep -R "draft_status" -n srmp-admin/src/main/resources/db/phase33_ai_solution_draft_version.sql srmp-agent/src/main/java >/dev/null
grep -R "ai_solution_task_version" -n srmp-admin/src/main/resources/db srmp-agent/src/main/java >/dev/null
grep -R "AiSolutionDraftService" -n srmp-agent/src/main/java >/dev/null
grep -R "AiSolutionDraftSaveRequest" -n srmp-agent/src/main/java >/dev/null
grep -R "/map-object-drafts" -n srmp-agent/src/main/java srmp-web-ui/src >/dev/null
grep -R "/draft-status" -n srmp-agent/src/main/java srmp-web-ui/src >/dev/null
grep -R "/versions" -n srmp-agent/src/main/java srmp-web-ui/src >/dev/null
grep -R "saveMapObjectSolutionDraft" -n srmp-web-ui/src/api/solution.ts srmp-web-ui/src/views >/dev/null
grep -R "origin_type" -n srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/impl/AiSolutionQualityServiceImpl.java >/dev/null
grep -R "@Transactional" -n srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/impl/AiSolutionDraftServiceImpl.java >/dev/null

echo "[OK] phase33 solution draft versioning hooks exist"
