#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

test -f srmp-admin/src/main/resources/db/phase35_template_effectiveness.sql
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

echo "[OK] phase35 template effectiveness hooks exist"
