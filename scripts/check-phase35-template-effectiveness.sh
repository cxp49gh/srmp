#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

test -f srmp-admin/src/main/resources/db/srmp_full_init.sql
grep -R "template_meta" -n srmp-admin/src/main/resources/db srmp-agent/src/main/java >/dev/null
grep -R "step_data" -n srmp-admin/src/main/resources/db srmp-agent/src/main/java >/dev/null
grep -R "AiSolutionTemplatePipelineService" -n srmp-agent/src/main/java >/dev/null
grep -R "SolutionTemplateContext" -n srmp-agent/src/main/java >/dev/null
grep -R "TemplatePipelineResult" -n srmp-agent/src/main/java >/dev/null
grep -R "template_match" -n srmp-agent/src/main/java srmp-gis/src/main/java >/dev/null
grep -R "template_variable_build" -n srmp-agent/src/main/java srmp-gis/src/main/java >/dev/null
grep -R "template_render" -n srmp-agent/src/main/java srmp-gis/src/main/java >/dev/null
grep -R "template_validate" -n srmp-agent/src/main/java srmp-gis/src/main/java >/dev/null
grep -R "match-preview" -n srmp-agent/src/main/java srmp-web-ui/src >/dev/null
grep -R "render-preview" -n srmp-agent/src/main/java srmp-web-ui/src >/dev/null
grep -R "TemplateMetaCard" -n srmp-web-ui/src >/dev/null
grep -R "TemplateVariableCheckPanel" -n srmp-web-ui/src >/dev/null
grep -R "TemplateRenderPreviewDialog" -n srmp-web-ui/src >/dev/null
grep -R "templateMeta" -n srmp-web-ui/src srmp-agent/src/main/java srmp-gis/src/main/java >/dev/null
grep -R "map_region_maintenance_advice_default" -n srmp-admin/src/main/resources/db >/dev/null
grep -R "map_object_disease_treatment_default" -n srmp-admin/src/main/resources/db >/dev/null
! grep -R "aiRequest.post('/api/gis/map-region/solution'" -n srmp-web-ui/src/api/gis.ts >/dev/null
grep -R "mapAgentRun" -n srmp-web-ui/src/views/gis srmp-web-ui/src/api/agent.ts >/dev/null
grep -R "REGION_MAINTENANCE_SUGGESTION" -n srmp-admin/src/main/resources/db srmp-web-ui/src >/dev/null

echo "[OK] phase35 template effectiveness hooks exist"
