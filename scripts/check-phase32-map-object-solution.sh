#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

test ! -f srmp-agent/src/main/java/com/smartroad/srmp/agent/map/solution/controller/MapObjectSolutionController.java
grep -R "MapObjectSolutionService" -n srmp-agent/src/main/java >/dev/null
grep -R "MapObjectSolutionQualityChecker" -n srmp-agent/src/main/java >/dev/null
! grep -R "/api/agent/map-object/solution" -n srmp-agent/src/main/java srmp-web-ui/src >/dev/null

test -f srmp-web-ui/src/views/gis/components/SolutionPreviewDialog.vue
! grep -R "generateMapObjectSolution" -n srmp-web-ui/src >/dev/null
grep -R "GENERATE_OBJECT_SOLUTION" -n srmp-web-ui/src srmp-ai-orchestrator/app srmp-agent/src/main/java >/dev/null
grep -R "DISEASE_REVIEW" -n srmp-agent/src/main/java srmp-web-ui/src >/dev/null
grep -R "LOW_SCORE_TREATMENT" -n srmp-agent/src/main/java srmp-web-ui/src >/dev/null

echo "[OK] phase32 map object solution hooks are routed through LangGraph run"
