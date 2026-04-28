#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

grep -R "MapRegionAnalysisService" -n srmp-gis/src/main/java >/dev/null
grep -R "MapRegionSolutionService" -n srmp-gis/src/main/java >/dev/null
grep -R "/map-region/analysis" -n srmp-gis/src/main/java srmp-web-ui/src >/dev/null
grep -R "/map-region/solution" -n srmp-gis/src/main/java srmp-web-ui/src >/dev/null
grep -R "/map-region/drafts" -n srmp-gis/src/main/java srmp-web-ui/src >/dev/null
grep -R "MAP_REGION_SOLUTION" -n srmp-gis/src/main/java srmp-agent/src/main/java srmp-web-ui/src >/dev/null
grep -R "region_geometry_parse" -n srmp-gis/src/main/java >/dev/null
grep -R "region_spatial_query" -n srmp-gis/src/main/java >/dev/null
grep -R "region_statistics" -n srmp-gis/src/main/java >/dev/null
grep -R "region_hotspot_detect" -n srmp-gis/src/main/java >/dev/null
grep -R "region_business_analysis" -n srmp-gis/src/main/java >/dev/null
grep -R "region_knowledge_retrieve" -n srmp-gis/src/main/java >/dev/null
grep -R "region_solution_generate" -n srmp-gis/src/main/java >/dev/null
grep -R "region_quality_check" -n srmp-gis/src/main/java >/dev/null
grep -R "originType" -n srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/dto srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/impl/AiSolutionDraftServiceImpl.java >/dev/null
grep -R "MAP_REGION" -n srmp-agent/src/main/java srmp-web-ui/src >/dev/null
grep -R "AiTraceDrawer" -n srmp-web-ui/src >/dev/null
grep -R "AiTraceButton" -n srmp-web-ui/src >/dev/null
grep -R "RegionSelectionPanel" -n srmp-web-ui/src >/dev/null
grep -R "analyzeMapRegion" -n srmp-web-ui/src/api srmp-web-ui/src/views >/dev/null
grep -R "generateMapRegionSolution" -n srmp-web-ui/src/api srmp-web-ui/src/views >/dev/null
grep -R "saveMapRegionSolutionDraft" -n srmp-web-ui/src/api srmp-web-ui/src/views >/dev/null
grep -R "trace" -n srmp-agent/src/main/java/com/smartroad/srmp/agent/vo/AgentAnalysisResponse.java srmp-agent/src/main/java/com/smartroad/srmp/agent/map/solution/dto/MapObjectSolutionResponse.java >/dev/null

echo "[OK] phase34 map region and unified trace hooks exist"
