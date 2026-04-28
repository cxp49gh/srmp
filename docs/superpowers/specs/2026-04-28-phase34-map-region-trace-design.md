# Phase 34 Map Region Solution And Unified Trace Design

## Background

Phase 32 added map-object solution previews. Phase 33 persisted those previews as versioned AI solution tasks. Phase 34 extends the same product language from a single selected object to a user-drawn map region:

```text
draw region -> region summary -> AI region maintenance suggestion -> preview -> save draft -> trace review
```

The current `/gis/one-map` page already uses a bottom `ObjectDetailDrawer` for "AI 分析此对象" and a right floating `AgentChatFloat` for AI chat. Phase 34 must keep that interaction model instead of introducing a competing right-side summary panel.

The current AI trace stack already has:

- `ai_trace_log`
- `ai_trace_step`
- `AiTraceContext`
- `/agent/ai-traces`

Phase 34 expands trace coverage and UI entry points, but does not introduce a second trace model.

## Goals

1. Support drawing a rectangle or arbitrary polygon on `/gis/one-map`.
2. Normalize rectangle selection to GeoJSON `Polygon` so the backend has one geometry contract.
3. Show selected-region statistics in a bottom panel consistent with `ObjectDetailDrawer`.
4. Generate a region maintenance suggestion from selected geometry, current map query, active layers, business data, and optional knowledge sources.
5. Return and persist AI trace for region generation using the existing `AiTraceContext` and trace tables.
6. Make trace viewable from all AI result surfaces:
   - normal chat;
   - single-object AI analysis;
   - single-object solution preview;
   - route/disease/assessment analysis;
   - region solution preview.
7. Allow generated region suggestions to be saved into the Phase 33 solution task stack with `origin_type = MAP_REGION`.

## Non-Goals

Phase 34 does not implement:

- work order conversion;
- region approval workflow;
- map layer editing;
- long-running async AI job queue;
- multi-user collaborative drawing;
- a new trace table or a separate trace monitoring page.

## User Flow

```text
1. Open /gis/one-map
2. Click "框选区域"
3. Choose rectangle or polygon drawing mode
4. Draw region on map
5. Bottom region panel displays region summary
6. Click "生成区域养护建议"
7. Preview dialog shows the generated suggestion
8. User can copy, download, save draft, or view AI Trace
9. Saved draft appears in the existing solution task page
```

Object selection and region selection are mutually exclusive:

- clicking a map object shows `ObjectDetailDrawer` and clears or collapses region selection;
- drawing a region shows `RegionSelectionPanel` and hides `ObjectDetailDrawer`.

## Frontend Design

### OneMap Interaction

Add region drawing controls to the existing map toolbar or a small map tool group:

- `框选区域`
- `矩形`
- `多边形`
- `清除选区`

The drawing tool creates a GeoJSON `Polygon`. Rectangle selection is converted into a five-point closed polygon:

```json
{
  "type": "Polygon",
  "coordinates": [[[lng1, lat1], [lng2, lat1], [lng2, lat2], [lng1, lat2], [lng1, lat1]]]
}
```

Polygon selection must require at least three distinct points before it can be submitted.

### RegionSelectionPanel

Create a bottom-centered panel consistent with `ObjectDetailDrawer`:

```text
srmp-web-ui/src/views/gis/components/RegionSelectionPanel.vue
```

It displays:

- selected geometry type: rectangle or polygon;
- approximate selected area;
- route count;
- road section count;
- evaluation unit count;
- disease count;
- heavy disease count;
- average MQI / PQI / PCI;
- poor/bad rate;
- hotspot summary.

Actions:

- `生成区域养护建议`
- `查看 AI Trace`, shown after generation when `trace.traceId` exists;
- `清除选区`

When region selection exists, `MapStatisticsBar` should be hidden or collapsed because the bottom region panel becomes the active statistics surface. Clearing the region restores the global statistics bar.

### Solution Preview

Reuse the existing preview dialog pattern instead of creating a separate region-only preview. The dialog needs to accept region solution fields in addition to map-object solution fields:

- `solutionType`
- `title`
- `markdown`
- `regionSummary`
- `qualityCheck`
- `trace`

The save button calls the Phase 34 save endpoint or a generalized Phase 33 draft endpoint that stores `origin_type = MAP_REGION`.

### Unified Trace Drawer

Create reusable trace UI components:

```text
srmp-web-ui/src/views/agent/components/AiTraceButton.vue
srmp-web-ui/src/views/agent/components/AiTraceDrawer.vue
```

`AiTraceDrawer` displays the same fields used by `/agent/ai-traces`:

- step label or step name;
- status: `SUCCESS`, `FAILED`, `SKIPPED`, `TIMEOUT`;
- cost in milliseconds;
- hit count;
- error message.

All AI result surfaces should use these components when a trace is available:

- `AiChatPage.vue`: each assistant message can expose its own trace.
- `AgentChatFloat.vue`: latest AI answer can expose trace.
- `SolutionPreviewDialog.vue`: object and region solution previews can expose trace.
- analysis result pages or panels: route/disease/assessment analysis can expose trace after backend returns it.
- `AiTracesPage.vue`: remains the full historical trace monitor.

## Backend Design

### Region Solution Endpoint

Add a dedicated endpoint:

```http
POST /api/agent/map-region/solution
```

Request:

```json
{
  "solutionType": "REGION_MAINTENANCE_SUGGESTION",
  "geometry": {
    "type": "Polygon",
    "coordinates": []
  },
  "query": {
    "routeCode": "G210",
    "year": 2026,
    "indexCode": "MQI",
    "grade": ""
  },
  "layers": ["ROAD_SECTION", "EVALUATION_UNIT", "DISEASE", "ASSESSMENT_RESULT"],
  "options": {
    "useBusinessData": true,
    "useKnowledge": true,
    "useOutline": false,
    "topK": 5
  }
}
```

Response:

```json
{
  "solutionType": "REGION_MAINTENANCE_SUGGESTION",
  "title": "G210 选区养护建议草稿",
  "markdown": "# G210 选区养护建议草稿\n\n## 一、区域概况\n\n本次选区覆盖 G210 部分路段。",
  "regionSummary": {},
  "qualityCheck": {},
  "sourceSummaries": [],
  "trace": {
    "traceId": "ai-20260428193000123-region1"
  }
}
```

### Region Spatial Aggregation

`/api/gis/spatial-query` is currently a stub. Phase 34 should either implement it directly or introduce a focused region aggregation service used by both GIS and Agent:

```text
MapRegionAnalysisService
```

Required aggregation output:

- route count;
- road section count;
- evaluation unit count;
- disease count;
- heavy disease count;
- disease distribution by type/severity;
- average MQI / PQI / PCI;
- poor/bad assessment count and rate;
- top hotspot list, ordered by disease density, heavy disease count, or low PCI/MQI.

Geometry validation rules:

- only GeoJSON `Polygon` is accepted in Phase 34;
- polygon must be closed;
- polygon must have at least four coordinates including the closing coordinate;
- invalid geometry returns a validation error before AI generation.

### Spatial Query Strategy

The backend should perform database aggregation, not rely on only the currently loaded frontend features. This keeps trace and saved drafts audit-friendly.

If PostGIS geometry is available for the target table, use `ST_Intersects` with the selected polygon. If a table lacks usable geometry in the demo dataset, fall back to route/year filters and clearly mark the affected source summary as `SPATIAL_FALLBACK`. The trace step should remain successful only when the fallback is intentional and recorded in source summaries.

### Region Hotspot Detection

Hotspot detection should use deterministic rules before LLM generation:

- high heavy-disease count;
- high disease density;
- low average PCI/MQI;
- poor/bad grade concentration;
- repeated object overlap on the same route or stake range.

The result should be structured and included in `regionSummary.hotspots`.

### Region Business Analysis

Build a business analysis markdown block from region summary:

- selected area and filter conditions;
- key statistics;
- main disease types and severity;
- low-score or poor/bad objects;
- hotspot interpretation;
- suggested priorities.

This markdown becomes part of the prompt and also works as the local fallback answer when LLM generation is unavailable.

### Knowledge Retrieval

When options enable knowledge or Outline, query the same knowledge stack used by existing RAG:

- query text should include route, year, main disease types, low indicators, and "区域养护建议";
- sources must be returned as source summaries;
- the trace step count should equal retrieved knowledge plus Outline hit count.

### Region Solution Generation

The generated markdown must include:

- region overview;
- key statistics;
- main problems;
- hotspot analysis;
- maintenance strategy;
- priority recommendation;
- risk notice;
- manual review notice.

If the LLM returns empty or fails, return the deterministic business analysis as fallback and mark trace `fallback = true`.

### Region Quality Check

Quality result must match `SolutionQualityPanel` shape:

```json
{
  "passed": true,
  "score": 90,
  "level": "A",
  "summary": "区域方案质量校验通过，评分 90。",
  "items": [
    {
      "level": "OK",
      "code": "REGION_STATISTICS",
      "message": "已包含区域统计",
      "penalty": 0
    }
  ]
}
```

Minimum checks:

- has valid region geometry;
- has region statistics;
- has hotspot analysis;
- has maintenance strategy;
- has priority recommendation;
- has manual review notice;
- has source summaries.

## Unified Trace Design

Trace uses the existing schema and `AiTraceContext` only. Phase 34 adds coverage and UI entry points; it does not change the trace persistence model.

### Trace Coverage

The following backend calls must return a `trace` map in response data:

- `/api/agent/chat`
- `/api/agent/map-object/solution`
- `/api/agent/analyze/route`
- `/api/agent/analyze/disease`
- `/api/agent/analyze/assessment`
- `/api/agent/report/assessment`
- `/api/agent/map-region/solution`

Existing chat already returns `data.trace`. The other endpoints should follow the same convention:

```json
{
  "data": {
    "trace": {
      "traceId": "ai-20260428193000123-region1",
      "requestType": "MAP_REGION_SOLUTION",
      "mode": "MAP_REGION_LLM",
      "status": "SUCCESS",
      "fallback": false,
      "totalCostMs": 1234,
      "steps": []
    }
  }
}
```

### Region Trace Steps

Region generation must record these fixed steps:

| Step name | Label |
|---|---|
| `region_geometry_parse` | 解析框选范围 |
| `region_spatial_query` | 查询区域内对象 |
| `region_statistics` | 区域统计 |
| `region_hotspot_detect` | 热点识别 |
| `region_business_analysis` | 业务分析 |
| `region_knowledge_retrieve` | 知识库检索 |
| `region_solution_generate` | 生成区域养护建议 |
| `region_quality_check` | 质量检查 |

Step status uses the existing values:

```text
SUCCESS / FAILED / SKIPPED / TIMEOUT
```

`hit_count` should be meaningful:

- spatial query: selected object count;
- statistics: counted business objects;
- hotspot detection: hotspot count;
- knowledge retrieval: source count;
- quality check: quality item count.

### Request Types And Modes

Use request types to distinguish trace categories in `/agent/ai-traces`:

- `AGENT_CHAT`
- `MAP_OBJECT_SOLUTION`
- `ROUTE_ANALYSIS`
- `DISEASE_ANALYSIS`
- `ASSESSMENT_ANALYSIS`
- `ASSESSMENT_REPORT`
- `MAP_REGION_SOLUTION`

Suggested region modes:

- `MAP_REGION_LLM`
- `MAP_REGION_LOCAL`
- `MAP_REGION_FALLBACK`
- `FAILED`

## Draft Persistence

Region solution drafts should reuse Phase 33 persistence.

For saved region drafts:

- `ai_solution_task.origin_type = MAP_REGION`
- `ai_solution_task.object_type = MAP_REGION`
- `ai_solution_task.object_id` can be a generated region id or trace id
- `ai_solution_task.map_object` stores the selected region GeoJSON and drawing metadata
- `ai_solution_task.object_summary` stores `regionSummary`
- `ai_solution_task.quality_result` stores normalized region quality result
- `ai_solution_source` stores:
  - `MAP_REGION`
  - `BUSINESS_DATA`
  - `KNOWLEDGE`
  - `OUTLINE`
  - `TRACE`
- `ai_solution_task_version.source_snapshot` includes trace id and source summaries

The preview endpoint should not automatically save a draft. Saving happens only when the user clicks `保存草稿`.

## Error Handling

- Invalid geometry: reject before spatial query and return a user-readable message.
- Empty region: return a region summary with zero counts and skip solution generation unless the user explicitly retries with broader filters.
- Spatial query partial fallback: mark source summary as `SPATIAL_FALLBACK` and include a trace step warning via `error_message` only if the fallback indicates reduced precision.
- LLM failure: return deterministic region business analysis as fallback and set trace fallback.
- Trace persistence failure: return the generated solution but log the save failure, matching existing chat behavior.

## Testing And Acceptance

Acceptance path:

1. Open `/gis/one-map`.
2. Click `框选区域`.
3. Draw a rectangle.
4. Bottom `RegionSelectionPanel` appears and global `MapStatisticsBar` is hidden or collapsed.
5. Draw an arbitrary polygon.
6. Region summary refreshes.
7. Click `生成区域养护建议`.
8. Preview dialog shows markdown, quality result, and a trace entry.
9. Open trace drawer and verify all region trace steps appear.
10. Save the draft.
11. Open `/agent/solution-tasks` and verify the saved task has `origin_type = MAP_REGION`.
12. Open `/agent/ai-traces` and find the same trace id.
13. Normal chat, object analysis, object solution, and route analysis each expose a `查看 Trace` entry when trace data exists.

Verification commands should include:

```bash
bash scripts/check-phase34-map-region-trace.sh
/tmp/codex-maven/apache-maven-3.9.6/bin/mvn -pl srmp-agent,srmp-gis -am package -DskipTests
npm run build
git diff --check
```

## Open Implementation Notes

- The design intentionally keeps trace persistence centralized in `AiTraceService`.
- Region drawing should be implemented without changing the single-object click behavior.
- If adding Leaflet draw helpers introduces dependency weight or style conflicts, implement a focused drawing tool with Leaflet map events first.
- The region panel should reuse the existing bottom-drawer visual language rather than introducing a right-side statistics drawer.
