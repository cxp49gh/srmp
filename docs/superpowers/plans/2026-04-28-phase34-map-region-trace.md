# Phase 34 Map Region Solution And Unified Trace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build map region drawing, region maintenance suggestion generation, saved region drafts, and unified trace viewing across AI result surfaces.

**Architecture:** Put region-specific backend code in `srmp-gis`: drawing contracts, spatial aggregation, region solution generation endpoints, and region draft proxy endpoints. `srmp-gis` reuses shared AI capabilities from `srmp-agent` by depending on `srmp-agent`; `srmp-agent` keeps common trace, LLM, knowledge, and Phase 33 draft services, but does not get a map-region package. Keep trace persistence in the existing `AiTraceContext` and `AiTraceService`.

**Tech Stack:** Spring Boot 2.7, Java 8, NamedParameterJdbcTemplate, PostgreSQL/PostGIS, Vue 3, Element Plus, Leaflet, Vite.

---

## File Structure

- Create `scripts/check-phase34-map-region-trace.sh`: source-level acceptance check.
- Modify `srmp-gis/pom.xml`: depend on `srmp-agent` so GIS-owned region solution generation can reuse LLM, knowledge, trace, and draft services.
- Create GIS region DTOs and services:
  - `srmp-gis/src/main/java/com/smartroad/srmp/gis/dto/MapRegionAnalysisRequest.java`
  - `srmp-gis/src/main/java/com/smartroad/srmp/gis/dto/MapRegionSolutionRequest.java`
  - `srmp-gis/src/main/java/com/smartroad/srmp/gis/dto/MapRegionSolutionResponse.java`
  - `srmp-gis/src/main/java/com/smartroad/srmp/gis/service/MapRegionAnalysisService.java`
  - `srmp-gis/src/main/java/com/smartroad/srmp/gis/service/MapRegionSolutionService.java`
  - `srmp-gis/src/main/java/com/smartroad/srmp/gis/service/impl/MapRegionAnalysisServiceImpl.java`
  - `srmp-gis/src/main/java/com/smartroad/srmp/gis/service/impl/MapRegionSolutionServiceImpl.java`
- Create `srmp-gis/src/main/java/com/smartroad/srmp/gis/controller/GisMapRegionController.java`: region analysis, region solution, and region draft endpoints under `/api/gis/map-region`.
- Modify `srmp-gis/src/main/java/com/smartroad/srmp/gis/service/impl/GisMapSupportServiceImpl.java`: implement `/api/gis/spatial-query` using the new service.
- Modify trace coverage:
  - `srmp-agent/src/main/java/com/smartroad/srmp/agent/vo/AgentAnalysisResponse.java`
  - `srmp-agent/src/main/java/com/smartroad/srmp/agent/service/impl/AgentAnalysisServiceImpl.java`
  - `srmp-agent/src/main/java/com/smartroad/srmp/agent/map/solution/dto/MapObjectSolutionResponse.java`
  - `srmp-agent/src/main/java/com/smartroad/srmp/agent/map/solution/service/impl/MapObjectSolutionServiceImpl.java`
- Modify Phase 33 draft persistence:
  - `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/dto/AiSolutionDraftSaveRequest.java`
  - `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/AiSolutionDraftService.java`
  - `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/impl/AiSolutionDraftServiceImpl.java`
  - `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/impl/AiSolutionQualityServiceImpl.java`
- Modify frontend APIs:
  - `srmp-web-ui/src/api/gis.ts`
  - `srmp-web-ui/src/api/agent.ts`
  - `srmp-web-ui/src/api/solution.ts`
- Create reusable trace UI:
  - `srmp-web-ui/src/views/agent/components/AiTraceButton.vue`
  - `srmp-web-ui/src/views/agent/components/AiTraceDrawer.vue`
- Modify trace-consuming frontend surfaces:
  - `srmp-web-ui/src/views/agent/AiChatPage.vue`
  - `srmp-web-ui/src/views/gis/components/AgentChatFloat.vue`
  - `srmp-web-ui/src/views/gis/components/SolutionPreviewDialog.vue`
- Create region map UI:
  - `srmp-web-ui/src/views/gis/components/RegionSelectionPanel.vue`
  - modify `srmp-web-ui/src/views/gis/OneMap.vue`

---

### Task 1: Add Phase 34 Source Acceptance Check

**Files:**
- Create: `scripts/check-phase34-map-region-trace.sh`

- [ ] **Step 1: Create the failing source check**

Create `scripts/check-phase34-map-region-trace.sh`:

```bash
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
```

- [ ] **Step 2: Run the red check**

Run:

```bash
bash scripts/check-phase34-map-region-trace.sh
```

Expected: fails before implementation because region services and trace components do not exist.

- [ ] **Step 3: Commit Task 1**

```bash
git add scripts/check-phase34-map-region-trace.sh
git commit -m "feat: add phase34 map region trace check"
```

---

### Task 2: Implement GIS Region Aggregation

**Files:**
- Modify: `srmp-gis/pom.xml`
- Create: `srmp-gis/src/main/java/com/smartroad/srmp/gis/dto/MapRegionAnalysisRequest.java`
- Create: `srmp-gis/src/main/java/com/smartroad/srmp/gis/service/MapRegionAnalysisService.java`
- Create: `srmp-gis/src/main/java/com/smartroad/srmp/gis/service/impl/MapRegionAnalysisServiceImpl.java`
- Modify: `srmp-gis/src/main/java/com/smartroad/srmp/gis/service/impl/GisMapSupportServiceImpl.java`

- [ ] **Step 1: Add `srmp-agent` dependency to `srmp-gis`**

In `srmp-gis/pom.xml`, add this dependency inside `<dependencies>`:

```xml
<dependency>
    <groupId>com.smartroad</groupId>
    <artifactId>srmp-agent</artifactId>
    <version>${project.version}</version>
</dependency>
```

- [ ] **Step 2: Add region analysis request DTO**

Create `MapRegionAnalysisRequest.java`:

```java
package com.smartroad.srmp.gis.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class MapRegionAnalysisRequest {
    private Map<String, Object> geometry;
    private Map<String, Object> query;
    private List<String> layers;
    private Map<String, Object> options;
}
```

- [ ] **Step 3: Add region analysis service contract**

Create `MapRegionAnalysisService.java`:

```java
package com.smartroad.srmp.gis.service;

import com.smartroad.srmp.gis.dto.MapRegionAnalysisRequest;

import java.util.Map;

public interface MapRegionAnalysisService {
    Map<String, Object> analyze(MapRegionAnalysisRequest request);
}
```

- [ ] **Step 4: Implement geometry validation and SQL aggregation**

Create `MapRegionAnalysisServiceImpl.java` with these public methods and SQL responsibilities:

```java
package com.smartroad.srmp.gis.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroad.srmp.gis.dto.MapRegionAnalysisRequest;
import com.smartroad.srmp.gis.service.MapRegionAnalysisService;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class MapRegionAnalysisServiceImpl implements MapRegionAnalysisService {

    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> analyze(MapRegionAnalysisRequest request) {
        validatePolygon(request == null ? null : request.getGeometry());
        MapSqlParameterSource params = baseParams(request);
        String regionSql = "ST_SetSRID(ST_GeomFromGeoJSON(:geometryJson), 4326)";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("geometry", request.getGeometry());
        result.put("query", request.getQuery() == null ? new LinkedHashMap<>() : request.getQuery());
        result.put("layers", request.getLayers() == null ? Collections.emptyList() : request.getLayers());
        result.put("areaKm2", firstValue("select round((ST_Area(" + regionSql + "::geography) / 1000000.0)::numeric, 4)", params));
        result.put("routeCount", firstValue("select count(*) from road_route where tenant_id=:tenantId and deleted=false " + routeFilter() + spatialFilter("geom", regionSql), params));
        result.put("sectionCount", firstValue("select count(*) from road_section where tenant_id=:tenantId and deleted=false " + routeFilter() + spatialFilter("geom", regionSql), params));
        result.put("unitCount", firstValue("select count(*) from road_evaluation_unit where tenant_id=:tenantId and deleted=false " + routeFilter() + spatialFilter("geom", regionSql), params));
        result.put("diseaseSummary", diseaseSummary(params, regionSql));
        result.put("assessmentSummary", assessmentSummary(params, regionSql));
        result.put("hotspots", hotspots(params, regionSql));
        result.put("sourcePrecision", "POSTGIS");
        return result;
    }
}
```

Add helper methods in the same class:

```java
private void validatePolygon(Map<String, Object> geometry) {
    if (geometry == null || !"Polygon".equals(String.valueOf(geometry.get("type")))) {
        throw new IllegalArgumentException("geometry 只支持 GeoJSON Polygon");
    }
    Object coordinates = geometry.get("coordinates");
    if (!(coordinates instanceof List) || ((List<?>) coordinates).isEmpty()) {
        throw new IllegalArgumentException("Polygon coordinates 不能为空");
    }
    Object ring = ((List<?>) coordinates).get(0);
    if (!(ring instanceof List) || ((List<?>) ring).size() < 4) {
        throw new IllegalArgumentException("Polygon 外环至少需要 4 个坐标点");
    }
    Object first = ((List<?>) ring).get(0);
    Object last = ((List<?>) ring).get(((List<?>) ring).size() - 1);
    if (!String.valueOf(first).equals(String.valueOf(last))) {
        throw new IllegalArgumentException("Polygon 外环必须闭合");
    }
}

private MapSqlParameterSource baseParams(MapRegionAnalysisRequest request) {
    Map<String, Object> query = request == null || request.getQuery() == null ? new LinkedHashMap<>() : request.getQuery();
    return new MapSqlParameterSource()
            .addValue("tenantId", TenantContextHolder.getTenantId())
            .addValue("geometryJson", toJson(request.getGeometry()))
            .addValue("routeCode", safe(query.get("routeCode")))
            .addValue("year", safe(query.get("year")));
}

private String routeFilter() {
    return " and (nullif(:routeCode, '') is null or route_code = nullif(:routeCode, '')) ";
}

private String yearFilter() {
    return " and (nullif(:year, '') is null or cast(year as text) = nullif(:year, '')) ";
}

private String spatialFilter(String column, String regionSql) {
    return " and " + column + " is not null and ST_Intersects(" + column + ", " + regionSql + ") ";
}
```

Use these query helpers:

```java
private Map<String, Object> diseaseSummary(MapSqlParameterSource params, String regionSql) {
    Map<String, Object> summary = queryOne(
            "select count(*) as disease_count, " +
                    "sum(case when severity='HEAVY' then 1 else 0 end) as heavy_count, " +
                    "sum(case when severity='MEDIUM' then 1 else 0 end) as medium_count, " +
                    "sum(case when severity='LIGHT' then 1 else 0 end) as light_count " +
                    "from disease_record where tenant_id=:tenantId and deleted=false " +
                    routeFilter() + spatialFilter("geom", regionSql),
            params);
    List<Map<String, Object>> byType = namedParameterJdbcTemplate.queryForList(
            "select disease_name, severity, count(*) as count, coalesce(sum(quantity),0) as quantity " +
                    "from disease_record where tenant_id=:tenantId and deleted=false " +
                    routeFilter() + spatialFilter("geom", regionSql) +
                    "group by disease_name, severity order by count desc limit 10",
            params);
    summary.put("byType", byType);
    return summary;
}

private Map<String, Object> assessmentSummary(MapSqlParameterSource params, String regionSql) {
    Map<String, Object> summary = queryOne(
            "select count(*) as assessment_count, round(avg(mqi),3) as avg_mqi, round(avg(pqi),3) as avg_pqi, round(avg(pci),3) as avg_pci, " +
                    "sum(case when grade in ('POOR','BAD') then 1 else 0 end) as poor_bad_count " +
                    "from assessment_result ar left join road_evaluation_unit u on u.tenant_id=ar.tenant_id and u.id=ar.unit_id " +
                    "where ar.tenant_id=:tenantId and ar.deleted=false " +
                    "and (nullif(:routeCode, '') is null or ar.route_code = nullif(:routeCode, '')) " +
                    yearFilter().replace("year", "ar.year") +
                    " and u.geom is not null and ST_Intersects(u.geom, " + regionSql + ")",
            params);
    summary.put("poorBadRate", rate(summary.get("poor_bad_count"), summary.get("assessment_count")));
    return summary;
}

private List<Map<String, Object>> hotspots(MapSqlParameterSource params, String regionSql) {
    return namedParameterJdbcTemplate.queryForList(
            "select route_code, min(start_stake) as start_stake, max(end_stake) as end_stake, count(*) as disease_count, " +
                    "sum(case when severity='HEAVY' then 1 else 0 end) as heavy_count " +
                    "from disease_record where tenant_id=:tenantId and deleted=false " +
                    routeFilter() + spatialFilter("geom", regionSql) +
                    "group by route_code order by heavy_count desc, disease_count desc limit 5",
            params);
}
```

Add utility methods:

```java
private Object firstValue(String sql, MapSqlParameterSource params) {
    return namedParameterJdbcTemplate.queryForObject(sql, params, Object.class);
}

private Map<String, Object> queryOne(String sql, MapSqlParameterSource params) {
    List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(sql, params);
    return rows.isEmpty() ? new LinkedHashMap<>() : new LinkedHashMap<>(rows.get(0));
}

private BigDecimal rate(Object numerator, Object denominator) {
    BigDecimal n = toBigDecimal(numerator);
    BigDecimal d = toBigDecimal(denominator);
    if (d.compareTo(BigDecimal.ZERO) == 0) {
        return BigDecimal.ZERO;
    }
    return n.multiply(new BigDecimal("100")).divide(d, 2, RoundingMode.HALF_UP);
}

private BigDecimal toBigDecimal(Object value) {
    if (value == null) {
        return BigDecimal.ZERO;
    }
    return new BigDecimal(String.valueOf(value));
}

private String safe(Object value) {
    return value == null ? "" : String.valueOf(value).trim();
}

private String toJson(Object value) {
    try {
        return objectMapper.writeValueAsString(value);
    } catch (Exception e) {
        throw new IllegalArgumentException("GeoJSON 序列化失败：" + e.getMessage(), e);
    }
}
```

- [ ] **Step 5: Wire `/api/gis/spatial-query`**

Modify `GisMapSupportServiceImpl`:

```java
@Resource
private MapRegionAnalysisService mapRegionAnalysisService;
```

Replace `spatialQuery` body:

```java
@Override
public GeoJsonFeatureCollectionVO spatialQuery(Map<String, Object> query) {
    MapRegionAnalysisRequest request = new MapRegionAnalysisRequest();
    request.setGeometry(query == null ? null : (Map<String, Object>) query.get("geometry"));
    request.setQuery(query == null ? null : (Map<String, Object>) query.get("query"));
    request.setLayers(query == null ? null : (List<String>) query.get("layers"));
    request.setOptions(query == null ? null : (Map<String, Object>) query.get("options"));
    mapRegionAnalysisService.analyze(request);
    return new GeoJsonFeatureCollectionVO();
}
```

This keeps the old return type stable while validating and executing the aggregation path. Region solution generation will consume the richer `MapRegionAnalysisService.analyze` result directly.

- [ ] **Step 6: Run backend compile**

```bash
/tmp/codex-maven/apache-maven-3.9.6/bin/mvn -pl srmp-gis,srmp-agent -am package -DskipTests
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit Task 2**

```bash
git add srmp-gis/pom.xml srmp-gis/src/main/java/com/smartroad/srmp/gis/dto/MapRegionAnalysisRequest.java srmp-gis/src/main/java/com/smartroad/srmp/gis/service/MapRegionAnalysisService.java srmp-gis/src/main/java/com/smartroad/srmp/gis/service/impl/MapRegionAnalysisServiceImpl.java srmp-gis/src/main/java/com/smartroad/srmp/gis/service/impl/GisMapSupportServiceImpl.java
git commit -m "feat: aggregate map region statistics"
```

---

### Task 3: Add Region Solution Generation With Trace

**Files:**
- Create: `srmp-gis/src/main/java/com/smartroad/srmp/gis/dto/MapRegionSolutionRequest.java`
- Create: `srmp-gis/src/main/java/com/smartroad/srmp/gis/dto/MapRegionSolutionResponse.java`
- Create: `srmp-gis/src/main/java/com/smartroad/srmp/gis/service/MapRegionSolutionService.java`
- Create: `srmp-gis/src/main/java/com/smartroad/srmp/gis/service/impl/MapRegionSolutionServiceImpl.java`
- Create: `srmp-gis/src/main/java/com/smartroad/srmp/gis/controller/GisMapRegionController.java`

- [ ] **Step 1: Add request and response DTOs**

Create `MapRegionSolutionRequest.java`:

```java
package com.smartroad.srmp.gis.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class MapRegionSolutionRequest {
    private String solutionType;
    private Map<String, Object> geometry;
    private Map<String, Object> query;
    private List<String> layers;
    private Map<String, Object> options;
}
```

Create `MapRegionSolutionResponse.java`:

```java
package com.smartroad.srmp.gis.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class MapRegionSolutionResponse {
    private String solutionType;
    private String title;
    private String markdown;
    private Map<String, Object> regionSummary;
    private Map<String, Object> qualityCheck;
    private List<Map<String, Object>> sourceSummaries;
    private Map<String, Object> trace;
}
```

- [ ] **Step 2: Add service contract and controller**

Create `MapRegionSolutionService.java`:

```java
package com.smartroad.srmp.gis.service;

import com.smartroad.srmp.gis.dto.MapRegionSolutionRequest;
import com.smartroad.srmp.gis.dto.MapRegionSolutionResponse;

public interface MapRegionSolutionService {
    MapRegionSolutionResponse generate(MapRegionSolutionRequest request);
}
```

Create `GisMapRegionController.java`:

```java
package com.smartroad.srmp.gis.controller;

import com.smartroad.srmp.agent.solution.dto.AiSolutionDraftSaveRequest;
import com.smartroad.srmp.agent.solution.service.AiSolutionDraftService;
import com.smartroad.srmp.common.core.R;
import com.smartroad.srmp.gis.dto.MapRegionAnalysisRequest;
import com.smartroad.srmp.gis.dto.MapRegionSolutionRequest;
import com.smartroad.srmp.gis.dto.MapRegionSolutionResponse;
import com.smartroad.srmp.gis.service.MapRegionAnalysisService;
import com.smartroad.srmp.gis.service.MapRegionSolutionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/api/gis/map-region")
public class GisMapRegionController {

    @Resource
    private MapRegionSolutionService mapRegionSolutionService;

    @Resource
    private MapRegionAnalysisService mapRegionAnalysisService;

    @Resource
    private AiSolutionDraftService aiSolutionDraftService;

    @PostMapping("/analysis")
    public R<Map<String, Object>> analysis(@RequestBody MapRegionAnalysisRequest request) {
        return R.ok(mapRegionAnalysisService.analyze(request));
    }

    @PostMapping("/solution")
    public R<MapRegionSolutionResponse> solution(@RequestBody MapRegionSolutionRequest request) {
        return R.ok(mapRegionSolutionService.generate(request));
    }

    @PostMapping("/drafts")
    public R<Map<String, Object>> saveDraft(@RequestBody AiSolutionDraftSaveRequest request) {
        return R.ok(aiSolutionDraftService.saveMapRegionDraft(request));
    }
}
```

- [ ] **Step 3: Implement service trace skeleton**

Create `MapRegionSolutionServiceImpl.java` with these injected dependencies:

```java
package com.smartroad.srmp.gis.service.impl;

import com.smartroad.srmp.agent.knowledge.dto.KnowledgeSearchRequest;
import com.smartroad.srmp.agent.knowledge.service.KnowledgeService;
import com.smartroad.srmp.agent.knowledge.vo.KnowledgeSearchResult;
import com.smartroad.srmp.agent.llm.LlmClient;
import com.smartroad.srmp.agent.outline.service.OutlineService;
import com.smartroad.srmp.agent.outline.vo.OutlineSearchResult;
import com.smartroad.srmp.agent.rag.RagOptions;
import com.smartroad.srmp.agent.trace.AiTraceContext;
import com.smartroad.srmp.agent.trace.service.AiTraceService;
import com.smartroad.srmp.gis.dto.MapRegionAnalysisRequest;
import com.smartroad.srmp.gis.dto.MapRegionSolutionRequest;
import com.smartroad.srmp.gis.dto.MapRegionSolutionResponse;
import com.smartroad.srmp.gis.service.MapRegionAnalysisService;
import com.smartroad.srmp.gis.service.MapRegionSolutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

@Slf4j
@Service
public class MapRegionSolutionServiceImpl implements MapRegionSolutionService {

    @Resource
    private MapRegionAnalysisService mapRegionAnalysisService;

    @Resource
    private KnowledgeService knowledgeService;

    @Resource
    private OutlineService outlineService;

    @Resource
    private LlmClient llmClient;

    @Resource
    private AiTraceService aiTraceService;

    @Override
    public MapRegionSolutionResponse generate(MapRegionSolutionRequest request) {
        AiTraceContext trace = AiTraceContext.start("MAP_REGION_SOLUTION", buildTraceMessage(request));
        MapRegionSolutionResponse response = new MapRegionSolutionResponse();
        response.setSolutionType(safe(request == null ? null : request.getSolutionType()).isEmpty()
                ? "REGION_MAINTENANCE_SUGGESTION"
                : safe(request.getSolutionType()));
        try {
            MapRegionAnalysisRequest analysisRequest = toAnalysisRequest(request);
            Map<String, Object> regionSummary = runRegionAnalysis(trace, analysisRequest);
            List<Map<String, Object>> hotspots = runHotspotStep(trace, regionSummary);
            String businessMarkdown = runBusinessAnalysis(trace, request, regionSummary, hotspots);
            Map<String, Object> sources = runKnowledgeRetrieve(trace, request, businessMarkdown);
            String markdown = runSolutionGenerate(trace, request, regionSummary, businessMarkdown, sources);
            Map<String, Object> quality = runQualityCheck(trace, regionSummary, markdown, sources);

            response.setTitle(buildTitle(request, regionSummary));
            response.setMarkdown(markdown);
            response.setRegionSummary(regionSummary);
            response.setQualityCheck(quality);
            response.setSourceSummaries(buildSourceSummaries(regionSummary, sources, trace));
            trace.setMode(Boolean.TRUE.equals(sources.get("llmSuccess")) ? "MAP_REGION_LLM" : "MAP_REGION_FALLBACK");
            trace.setStatus("SUCCESS");
            trace.setFallback(!Boolean.TRUE.equals(sources.get("llmSuccess")));
            return response;
        } catch (Exception e) {
            trace.setMode("FAILED");
            trace.setStatus("FAILED");
            trace.setFallback(true);
            trace.setError(e.getMessage());
            throw e;
        } finally {
            trace.finish();
            response.setTrace(trace.toMap());
            try {
                aiTraceService.save(trace);
            } catch (Exception saveError) {
                log.warn("[MAP-REGION] save trace failed traceId={} error={}", trace.getTraceId(), saveError.getMessage(), saveError);
            }
        }
    }
}
```

- [ ] **Step 4: Add region trace step methods**

Add these methods to `MapRegionSolutionServiceImpl`:

```java
private Map<String, Object> runRegionAnalysis(AiTraceContext trace, MapRegionAnalysisRequest request) {
    AiTraceContext.StepTimer geometryTimer = trace.step("region_geometry_parse", "解析框选范围");
    geometryTimer.success(1);

    AiTraceContext.StepTimer spatialTimer = trace.step("region_spatial_query", "查询区域内对象");
    Map<String, Object> summary = mapRegionAnalysisService.analyze(request);
    int objectCount = intValue(summary.get("routeCount")) + intValue(summary.get("sectionCount")) + intValue(summary.get("unitCount"));
    Map<String, Object> disease = mapValue(summary.get("diseaseSummary"));
    objectCount += intValue(disease.get("disease_count"));
    spatialTimer.success(objectCount);

    AiTraceContext.StepTimer statisticsTimer = trace.step("region_statistics", "区域统计");
    statisticsTimer.success(objectCount);
    return summary;
}

private List<Map<String, Object>> runHotspotStep(AiTraceContext trace, Map<String, Object> regionSummary) {
    AiTraceContext.StepTimer timer = trace.step("region_hotspot_detect", "热点识别");
    Object raw = regionSummary.get("hotspots");
    List<Map<String, Object>> hotspots = raw instanceof List ? (List<Map<String, Object>>) raw : new ArrayList<>();
    timer.success(hotspots.size());
    return hotspots;
}

private String runBusinessAnalysis(AiTraceContext trace, MapRegionSolutionRequest request, Map<String, Object> summary, List<Map<String, Object>> hotspots) {
    AiTraceContext.StepTimer timer = trace.step("region_business_analysis", "业务分析");
    String markdown = buildBusinessMarkdown(request, summary, hotspots);
    timer.success(1);
    return markdown;
}
```

- [ ] **Step 5: Add knowledge retrieval and solution generation methods**

Add:

```java
private Map<String, Object> runKnowledgeRetrieve(AiTraceContext trace, MapRegionSolutionRequest request, String businessMarkdown) {
    AiTraceContext.StepTimer timer = trace.step("region_knowledge_retrieve", "知识库检索");
    RagOptions options = RagOptions.autoByQuestion("区域养护建议", request == null ? null : request.getOptions());
    List<KnowledgeSearchResult> knowledgeSources = new ArrayList<>();
    List<OutlineSearchResult> outlineSources = new ArrayList<>();
    if (options.isUseKnowledge()) {
        KnowledgeSearchRequest searchRequest = new KnowledgeSearchRequest();
        searchRequest.setQuery(buildKnowledgeQuery(request, businessMarkdown));
        searchRequest.setTopK(options.getTopK());
        knowledgeSources = knowledgeService.search(searchRequest);
    }
    if (options.isUseOutline()) {
        outlineSources = outlineService.search(buildKnowledgeQuery(request, businessMarkdown), options.getTopK());
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("knowledgeSources", knowledgeSources);
    result.put("outlineSources", outlineSources);
    result.put("llmSuccess", false);
    timer.success(knowledgeSources.size() + outlineSources.size());
    return result;
}

private String runSolutionGenerate(AiTraceContext trace, MapRegionSolutionRequest request, Map<String, Object> summary, String businessMarkdown, Map<String, Object> sources) {
    AiTraceContext.StepTimer timer = trace.step("region_solution_generate", "生成区域养护建议");
    String prompt = buildPrompt(summary, businessMarkdown, sources);
    String answer = llmClient.chat("你是智路养护平台区域养护方案生成助手。资料不足时必须说明，输出 Markdown 草稿。", prompt);
    boolean success = answer != null && answer.trim().length() > 0;
    sources.put("llmSuccess", success);
    timer.success(1);
    return success ? sanitize(answer) : businessMarkdown;
}
```

- [ ] **Step 6: Add quality and source methods**

Add:

```java
private Map<String, Object> runQualityCheck(AiTraceContext trace, Map<String, Object> summary, String markdown, Map<String, Object> sources) {
    AiTraceContext.StepTimer timer = trace.step("region_quality_check", "质量检查");
    List<Map<String, Object>> items = new ArrayList<>();
    int score = 100;
    score -= qualityItem(items, summary.get("geometry") != null, "REGION_GEOMETRY", "已包含区域范围", 20);
    score -= qualityItem(items, !mapValue(summary.get("diseaseSummary")).isEmpty(), "REGION_STATISTICS", "已包含区域统计", 20);
    score -= qualityItem(items, containsAny(markdown, "热点", "重点"), "REGION_HOTSPOT", "已包含热点识别", 15);
    score -= qualityItem(items, containsAny(markdown, "养护", "处置", "策略"), "REGION_STRATEGY", "已包含养护策略", 20);
    score -= qualityItem(items, containsAny(markdown, "优先", "近期", "P1", "P2"), "REGION_PRIORITY", "已包含优先级建议", 15);
    score -= qualityItem(items, containsAny(markdown, "人工审核", "复核", "草稿"), "REGION_REVIEW_NOTICE", "已包含人工审核提示", 10);
    if (score < 0) {
        score = 0;
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("passed", score >= 80 && items.stream().noneMatch(i -> "ERROR".equals(i.get("level"))));
    result.put("score", score);
    result.put("level", score >= 90 ? "A" : score >= 80 ? "B" : score >= 60 ? "C" : "D");
    result.put("summary", "区域方案质量校验" + (Boolean.TRUE.equals(result.get("passed")) ? "通过" : "需复核") + "，评分 " + score + "。");
    result.put("items", items);
    result.put("originType", "MAP_REGION");
    result.put("checkedAt", new Date());
    timer.success(items.size());
    return result;
}

private int qualityItem(List<Map<String, Object>> items, boolean passed, String code, String message, int penalty) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("level", passed ? "OK" : (penalty >= 20 ? "ERROR" : "WARN"));
    item.put("code", code);
    item.put("message", passed ? message : "缺少或需补充：" + message);
    item.put("penalty", passed ? 0 : penalty);
    items.add(item);
    return passed ? 0 : penalty;
}
```

- [ ] **Step 7: Add utility methods**

Add:

```java
private MapRegionAnalysisRequest toAnalysisRequest(MapRegionSolutionRequest request) {
    if (request == null) {
        throw new IllegalArgumentException("请求不能为空");
    }
    MapRegionAnalysisRequest analysis = new MapRegionAnalysisRequest();
    analysis.setGeometry(request.getGeometry());
    analysis.setQuery(request.getQuery());
    analysis.setLayers(request.getLayers());
    analysis.setOptions(request.getOptions());
    return analysis;
}

private String buildTraceMessage(MapRegionSolutionRequest request) {
    Map<String, Object> query = request == null || request.getQuery() == null ? new LinkedHashMap<>() : request.getQuery();
    return "区域养护建议 " + safe(query.get("routeCode")) + " " + safe(query.get("year"));
}

private String buildTitle(MapRegionSolutionRequest request, Map<String, Object> summary) {
    Map<String, Object> query = request == null || request.getQuery() == null ? new LinkedHashMap<>() : request.getQuery();
    String route = safe(query.get("routeCode")).isEmpty() ? "选区" : safe(query.get("routeCode"));
    return route + " 区域养护建议草稿";
}

private String buildBusinessMarkdown(MapRegionSolutionRequest request, Map<String, Object> summary, List<Map<String, Object>> hotspots) {
    Map<String, Object> disease = mapValue(summary.get("diseaseSummary"));
    Map<String, Object> assessment = mapValue(summary.get("assessmentSummary"));
    StringBuilder sb = new StringBuilder();
    sb.append("# ").append(buildTitle(request, summary)).append("\n\n");
    sb.append("## 一、区域概况\n\n");
    sb.append("- 选区面积：").append(safe(summary.get("areaKm2"))).append(" km2\n");
    sb.append("- 涉及路线：").append(safe(summary.get("routeCount"))).append(" 条\n");
    sb.append("- 涉及路段：").append(safe(summary.get("sectionCount"))).append(" 个\n");
    sb.append("- 评定单元：").append(safe(summary.get("unitCount"))).append(" 个\n\n");
    sb.append("## 二、区域统计\n\n");
    sb.append("- 病害数量：").append(safe(disease.get("disease_count"))).append("\n");
    sb.append("- 重度病害：").append(safe(disease.get("heavy_count"))).append("\n");
    sb.append("- 平均 MQI：").append(safe(assessment.get("avg_mqi"))).append("\n");
    sb.append("- 平均 PQI：").append(safe(assessment.get("avg_pqi"))).append("\n");
    sb.append("- 平均 PCI：").append(safe(assessment.get("avg_pci"))).append("\n\n");
    sb.append("## 三、热点识别\n\n");
    if (hotspots.isEmpty()) {
        sb.append("当前选区未识别到明显病害热点。\n\n");
    } else {
        for (int i = 0; i < hotspots.size(); i++) {
            Map<String, Object> item = hotspots.get(i);
            sb.append(i + 1).append(". ").append(safe(item.get("route_code")))
                    .append("，病害 ").append(safe(item.get("disease_count")))
                    .append("，重度 ").append(safe(item.get("heavy_count"))).append("\n");
        }
        sb.append("\n");
    }
    sb.append("## 四、养护策略\n\n");
    sb.append("建议优先复核重度病害集中、PCI 或 MQI 偏低的局部区域，按病害集中程度安排近期处置和预防性养护。\n\n");
    sb.append("## 五、优先级建议\n\n");
    sb.append("P1：重度病害集中或低 PCI 区域；P2：中度病害集中区域；P3：轻度病害和常规巡查区域。\n\n");
    sb.append("## 六、风险提示\n\n");
    sb.append("本建议为 AI 生成草稿，需由养护技术人员结合现场复核、预算和交通组织条件人工审核。\n");
    return sb.toString();
}
```

Add the small helpers:

```java
private String buildKnowledgeQuery(MapRegionSolutionRequest request, String businessMarkdown) {
    return "区域养护建议 " + buildTraceMessage(request) + " " + shortText(businessMarkdown, 300);
}

private String buildPrompt(Map<String, Object> summary, String businessMarkdown, Map<String, Object> sources) {
    return "请基于区域统计、热点和知识库来源生成区域养护建议草稿。\n\n【区域统计】\n" + summary +
            "\n\n【业务分析】\n" + businessMarkdown +
            "\n\n【来源】\n" + sources;
}

private List<Map<String, Object>> buildSourceSummaries(Map<String, Object> summary, Map<String, Object> sources, AiTraceContext trace) {
    List<Map<String, Object>> result = new ArrayList<>();
    result.add(source("MAP_REGION", "地图框选区域", trace.getTraceId(), "", "区域面积 " + safe(summary.get("areaKm2")) + " km2"));
    result.add(source("BUSINESS_DATA", "区域业务统计", trace.getTraceId(), "", "病害、评定和热点聚合统计"));
    result.add(source("TRACE", "AI Trace", trace.getTraceId(), "", trace.getTraceId()));
    return result;
}

private Map<String, Object> source(String type, String title, String id, String url, String excerpt) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("sourceType", type);
    item.put("sourceTitle", title);
    item.put("sourceId", id);
    item.put("sourceUrl", url);
    item.put("contentExcerpt", excerpt);
    return item;
}

private Map<String, Object> mapValue(Object value) {
    return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<>();
}

private int intValue(Object value) {
    if (value == null) {
        return 0;
    }
    try {
        return Integer.parseInt(String.valueOf(value));
    } catch (Exception e) {
        return 0;
    }
}

private boolean containsAny(String text, String... words) {
    String value = text == null ? "" : text;
    for (String word : words) {
        if (value.contains(word)) {
            return true;
        }
    }
    return false;
}

private String shortText(String text, int max) {
    if (text == null) {
        return "";
    }
    return text.length() <= max ? text : text.substring(0, max);
}

private String sanitize(String text) {
    return text == null ? "" : text.replaceAll("(?is)<think>.*?</think>", "").trim();
}

private String safe(Object value) {
    return value == null ? "" : String.valueOf(value).trim();
}
```

- [ ] **Step 8: Run backend compile**

```bash
/tmp/codex-maven/apache-maven-3.9.6/bin/mvn -pl srmp-agent,srmp-gis -am package -DskipTests
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 9: Commit Task 3**

```bash
git add srmp-gis/src/main/java/com/smartroad/srmp/gis/dto/MapRegionSolutionRequest.java srmp-gis/src/main/java/com/smartroad/srmp/gis/dto/MapRegionSolutionResponse.java srmp-gis/src/main/java/com/smartroad/srmp/gis/service/MapRegionSolutionService.java srmp-gis/src/main/java/com/smartroad/srmp/gis/service/impl/MapRegionSolutionServiceImpl.java srmp-gis/src/main/java/com/smartroad/srmp/gis/controller/GisMapRegionController.java
git commit -m "feat: generate map region solution with trace"
```

---

### Task 4: Persist MAP_REGION Drafts

**Files:**
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/dto/AiSolutionDraftSaveRequest.java`
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/AiSolutionDraftService.java`
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/impl/AiSolutionDraftServiceImpl.java`
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/impl/AiSolutionQualityServiceImpl.java`

- [ ] **Step 1: Extend save DTO**

Add these fields to `AiSolutionDraftSaveRequest`:

```java
private String originType;
private String objectType;
private String objectId;
private Map<String, Object> regionSummary;
private Map<String, Object> trace;
```

- [ ] **Step 2: Extend service contract**

Add to `AiSolutionDraftService`:

```java
Map<String, Object> saveMapRegionDraft(AiSolutionDraftSaveRequest request);
```

Do not add a new agent-side controller endpoint for region drafts. Task 3 already exposes the GIS-owned proxy endpoint `POST /api/gis/map-region/drafts`, which delegates to this service method.

- [ ] **Step 3: Refactor draft save implementation to accept origin type**

In `AiSolutionDraftServiceImpl`, replace `saveMapObjectDraft` body with:

```java
@Override
@Transactional
public Map<String, Object> saveMapObjectDraft(AiSolutionDraftSaveRequest request) {
    return saveDraft(request, "MAP_OBJECT");
}

@Override
@Transactional
public Map<String, Object> saveMapRegionDraft(AiSolutionDraftSaveRequest request) {
    return saveDraft(request, "MAP_REGION");
}
```

Create `saveDraft` by moving the current save logic into a private method:

```java
private Map<String, Object> saveDraft(AiSolutionDraftSaveRequest request, String originType) {
    validateSave(request, originType);
    String tenantId = TenantContextHolder.getTenantId();
    String taskId = uuid();
    Map<String, Object> mapObject = request.getMapObject() == null ? new LinkedHashMap<>() : request.getMapObject();
    Map<String, Object> objectSummary = request.getRegionSummary() != null
            ? request.getRegionSummary()
            : (request.getObjectSummary() == null ? new LinkedHashMap<>() : request.getObjectSummary());
    Map<String, Object> quality = "MAP_REGION".equals(originType)
            ? normalizeRegionQuality(request.getQualityCheck())
            : normalizeMapObjectQuality(request.getQualityCheck());
    List<Map<String, Object>> sourceSnapshot = buildSourceSnapshot(request);

    MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", taskId)
            .addValue("tenantId", tenantId)
            .addValue("solutionType", safe(request.getSolutionType()))
            .addValue("title", safe(request.getTitle()))
            .addValue("routeCode", safe(request.getRouteCode()))
            .addValue("year", request.getYear())
            .addValue("templateId", safe(request.getTemplateId()))
            .addValue("templateVersion", safe(request.getTemplateVersion()))
            .addValue("status", "SUCCESS")
            .addValue("requestJson", toJson(buildRequestJson(request), "{}"))
            .addValue("resultContent", request.getMarkdown())
            .addValue("qualityResult", toJson(quality, "{}"))
            .addValue("originType", originType)
            .addValue("objectType", "MAP_REGION".equals(originType) ? "MAP_REGION" : firstString(objectSummary, mapObject, "objectType", "object_type", "type", "layerType", "assessment_object_type"))
            .addValue("objectId", "MAP_REGION".equals(originType) ? firstString(request.getTrace(), mapObject, "traceId", "id") : firstString(objectSummary, mapObject, "objectId", "object_id", "id"))
            .addValue("mapObject", toJson(mapObject, "{}"))
            .addValue("objectSummary", toJson(objectSummary, "{}"))
            .addValue("draftStatus", "DRAFT")
            .addValue("currentVersionNo", 1);

    namedParameterJdbcTemplate.update(
            "insert into ai_solution_task(" +
                    "id, tenant_id, solution_type, title, route_code, year, template_id, template_version, status, request_json, result_content, quality_result, created_at, updated_at, origin_type, object_type, object_id, map_object, object_summary, draft_status, current_version_no" +
                    ") values (" +
                    ":id, :tenantId, :solutionType, :title, :routeCode, :year, :templateId, :templateVersion, :status, cast(:requestJson as jsonb), :resultContent, cast(:qualityResult as jsonb), now(), now(), :originType, :objectType, :objectId, cast(:mapObject as jsonb), cast(:objectSummary as jsonb), :draftStatus, :currentVersionNo" +
                    ")",
            params);

    insertSources(tenantId, taskId, sourceSnapshot);
    insertVersion(tenantId, taskId, 1, request.getTitle(), request.getMarkdown(), quality, mapObject, objectSummary, sourceSnapshot, "创建草稿");
    return loadTask(tenantId, taskId);
}
```

- [ ] **Step 4: Update validation and request JSON**

Change validation signature:

```java
private void validateSave(AiSolutionDraftSaveRequest request, String originType) {
    if (request == null) {
        throw new IllegalArgumentException("请求不能为空");
    }
    if (safe(request.getSolutionType()).isEmpty()) {
        throw new IllegalArgumentException("solutionType 不能为空");
    }
    if (safe(request.getTitle()).isEmpty()) {
        throw new IllegalArgumentException("title 不能为空");
    }
    if (safe(request.getMarkdown()).isEmpty()) {
        throw new IllegalArgumentException("markdown 不能为空");
    }
    if (request.getMapObject() == null || request.getMapObject().isEmpty()) {
        throw new IllegalArgumentException("mapObject 不能为空");
    }
    if ("MAP_REGION".equals(originType) && (request.getRegionSummary() == null || request.getRegionSummary().isEmpty())) {
        throw new IllegalArgumentException("regionSummary 不能为空");
    }
}
```

Add to `buildRequestJson`:

```java
json.put("originType", request.getOriginType());
json.put("objectType", request.getObjectType());
json.put("objectId", request.getObjectId());
json.put("regionSummary", request.getRegionSummary() == null ? new LinkedHashMap<>() : request.getRegionSummary());
json.put("trace", request.getTrace() == null ? new LinkedHashMap<>() : request.getTrace());
```

- [ ] **Step 5: Add region quality normalization**

Add:

```java
private Map<String, Object> normalizeRegionQuality(Map<String, Object> qualityCheck) {
    if (qualityCheck != null && qualityCheck.get("score") != null && qualityCheck.get("items") instanceof List) {
        Map<String, Object> result = new LinkedHashMap<>(qualityCheck);
        result.put("originType", "MAP_REGION");
        return result;
    }
    Map<String, Object> result = new LinkedHashMap<>();
    List<Map<String, Object>> items = new ArrayList<>();
    items.add(item("WARN", "REGION_QUALITY_IMPORTED", "保存时未收到完整区域质量结果，需重新校验", 10));
    result.put("passed", false);
    result.put("score", 70);
    result.put("level", "C");
    result.put("summary", "区域方案质量结果不完整，建议重新校验。");
    result.put("items", items);
    result.put("originType", "MAP_REGION");
    result.put("checkedAt", new Date());
    return result;
}
```

- [ ] **Step 6: Extend quality service for MAP_REGION**

In `AiSolutionQualityServiceImpl.check`, add before generic route checks:

```java
if ("MAP_REGION".equals(asString(task.get("origin_type")))) {
    Map<String, Object> result = checkMapRegionTask(task, sources, content);
    saveQualityResult(taskId, result);
    return result;
}
```

Add:

```java
private Map<String, Object> checkMapRegionTask(Map<String, Object> task, List<Map<String, Object>> sources, String content) {
    List<Map<String, Object>> items = new ArrayList<>();
    int score = 100;
    score -= addQualityItem(items, !asString(task.get("object_summary")).isEmpty(), "REGION_STATISTICS", "区域统计", 20);
    score -= addQualityItem(items, containsAny(content, "热点", "重点"), "REGION_HOTSPOT", "热点分析", 15);
    score -= addQualityItem(items, containsAny(content, "养护", "处置", "策略"), "REGION_STRATEGY", "养护策略", 20);
    score -= addQualityItem(items, containsAny(content, "优先", "P1", "P2", "近期"), "REGION_PRIORITY", "优先级", 15);
    score -= addQualityItem(items, sources.stream().anyMatch(s -> "MAP_REGION".equals(asString(s.get("source_type")))), "REGION_SOURCE", "区域来源", 10);
    score -= addQualityItem(items, containsAny(content, "人工审核", "复核", "草稿"), "REGION_REVIEW_NOTICE", "人工审核提示", 10);
    if (score < 0) {
        score = 0;
    }
    boolean passed = score >= 80 && items.stream().noneMatch(i -> "ERROR".equals(i.get("level")));
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("passed", passed);
    result.put("score", score);
    result.put("level", score >= 90 ? "A" : score >= 80 ? "B" : score >= 60 ? "C" : "D");
    result.put("items", items);
    result.put("summary", "区域方案质量校验" + (passed ? "通过" : "未通过") + "，评分 " + score + "。");
    result.put("originType", "MAP_REGION");
    result.put("checkedAt", new Date());
    return result;
}
```

- [ ] **Step 7: Run backend compile**

```bash
/tmp/codex-maven/apache-maven-3.9.6/bin/mvn -pl srmp-agent,srmp-gis -am package -DskipTests
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Commit Task 4**

```bash
git add srmp-agent/src/main/java/com/smartroad/srmp/agent/solution
git commit -m "feat: persist map region solution drafts"
```

---

### Task 5: Add Trace Coverage To Existing AI Endpoints

**Files:**
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/vo/AgentAnalysisResponse.java`
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/service/impl/AgentAnalysisServiceImpl.java`
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/map/solution/dto/MapObjectSolutionResponse.java`
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/map/solution/service/impl/MapObjectSolutionServiceImpl.java`

- [ ] **Step 1: Add trace fields to response DTOs**

In `AgentAnalysisResponse` add:

```java
private Map<String, Object> trace;
```

In `MapObjectSolutionResponse` add:

```java
private Map<String, Object> trace;
```

- [ ] **Step 2: Add trace dependencies to analysis service**

In `AgentAnalysisServiceImpl` add imports and resource:

```java
import com.smartroad.srmp.agent.trace.AiTraceContext;
import com.smartroad.srmp.agent.trace.service.AiTraceService;
import lombok.extern.slf4j.Slf4j;
```

Annotate class:

```java
@Slf4j
@Service
public class AgentAnalysisServiceImpl implements AgentAnalysisService {
```

Inject:

```java
@Resource
private AiTraceService aiTraceService;
```

- [ ] **Step 3: Add traced wrapper helper**

Add:

```java
private AgentAnalysisResponse traced(String requestType, String message, java.util.function.Supplier<AgentAnalysisResponse> supplier) {
    AiTraceContext trace = AiTraceContext.start(requestType, message);
    try {
        AgentAnalysisResponse response = supplier.get();
        trace.setMode(response.getMode());
        trace.setStatus("SUCCESS");
        trace.setFallback(!"LLM".equals(response.getMode()));
        trace.step("business_analysis", "业务数据分析").success(1);
        trace.step("analysis_generate", "生成分析结果").success(1);
        trace.finish();
        response.setTrace(trace.toMap());
        saveTrace(trace);
        return response;
    } catch (Exception e) {
        trace.setMode("FAILED");
        trace.setStatus("FAILED");
        trace.setFallback(true);
        trace.setError(e.getMessage());
        trace.finish();
        saveTrace(trace);
        throw e;
    }
}

private void saveTrace(AiTraceContext trace) {
    try {
        aiTraceService.save(trace);
    } catch (Exception e) {
        log.warn("[AI-ANALYSIS] save trace failed traceId={} error={}", trace.getTraceId(), e.getMessage(), e);
    }
}
```

- [ ] **Step 4: Refactor public analysis methods**

Move each current public method body into a private method before replacing the public method. Use these exact private method names and preserve the existing business statements inside each extracted method. In the extracted `generateAssessmentReportInternal`, replace calls to public analysis methods with `analyzeRouteInternal`, `analyzeDiseaseInternal`, and `analyzeAssessmentInternal` so one report request creates one trace instead of nested traces.

After extraction, the public methods become:

```java
@Override
public AgentAnalysisResponse analyzeRoute(AgentAnalysisRequest request) {
    return traced("ROUTE_ANALYSIS", traceMessage("路线分析", request), () -> analyzeRouteInternal(request));
}

@Override
public AgentAnalysisResponse analyzeDisease(AgentAnalysisRequest request) {
    return traced("DISEASE_ANALYSIS", traceMessage("病害分析", request), () -> analyzeDiseaseInternal(request));
}

@Override
public AgentAnalysisResponse analyzeAssessment(AgentAnalysisRequest request) {
    return traced("ASSESSMENT_ANALYSIS", traceMessage("评定分析", request), () -> analyzeAssessmentInternal(request));
}

@Override
public AgentAnalysisResponse generateAssessmentReport(AgentAnalysisRequest request) {
    return traced("ASSESSMENT_REPORT", traceMessage("评定报告", request), () -> generateAssessmentReportInternal(request));
}
```

Add:

```java
private String traceMessage(String label, AgentAnalysisRequest request) {
    return label + " " + nvl(request == null ? null : request.getRouteCode(), "全部路线") + " " + nvl(request == null ? null : request.getYear(), "全部年度");
}
```

- [ ] **Step 5: Add trace to map-object solution service**

In `MapObjectSolutionServiceImpl`, inject `AiTraceService`, import `AiTraceContext`, and wrap `generate`:

```java
AiTraceContext trace = AiTraceContext.start("MAP_OBJECT_SOLUTION", safe(request == null ? null : request.getSolutionType()));
try {
    AiTraceContext.StepTimer contextTimer = trace.step("map_object_context", "地图对象上下文");
    MapObjectContext ctx = resolveContext(request);
    contextTimer.success(ctx != null && ctx.isPresent() ? 1 : 0);

    AiTraceContext.StepTimer solutionTimer = trace.step("map_object_solution_generate", "生成对象方案");
    MapObjectSolutionResponse response = buildResponse(request, ctx);
    solutionTimer.success(1);

    AiTraceContext.StepTimer qualityTimer = trace.step("map_object_quality_check", "质量检查");
    qualityTimer.success(response.getQualityCheck() == null || response.getQualityCheck().getItems() == null ? 0 : response.getQualityCheck().getItems().size());

    trace.setMode("MAP_OBJECT_LOCAL");
    trace.setStatus("SUCCESS");
    trace.setFallback(true);
    trace.finish();
    response.setTrace(trace.toMap());
    aiTraceService.save(trace);
    return response;
} catch (Exception e) {
    trace.setMode("FAILED");
    trace.setStatus("FAILED");
    trace.setFallback(true);
    trace.setError(e.getMessage());
    trace.finish();
    throw e;
}
```

Move the current response-building logic into:

```java
private MapObjectSolutionResponse buildResponse(MapObjectSolutionRequest request, MapObjectContext ctx) {
    // move the current generate method body after resolveContext into this method
}
```

- [ ] **Step 6: Run backend compile**

```bash
/tmp/codex-maven/apache-maven-3.9.6/bin/mvn -pl srmp-agent -am package -DskipTests
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit Task 5**

```bash
git add srmp-agent/src/main/java/com/smartroad/srmp/agent/vo/AgentAnalysisResponse.java srmp-agent/src/main/java/com/smartroad/srmp/agent/service/impl/AgentAnalysisServiceImpl.java srmp-agent/src/main/java/com/smartroad/srmp/agent/map/solution/dto/MapObjectSolutionResponse.java srmp-agent/src/main/java/com/smartroad/srmp/agent/map/solution/service/impl/MapObjectSolutionServiceImpl.java
git commit -m "feat: expose trace for AI analysis"
```

---

### Task 6: Add Frontend APIs And Reusable Trace UI

**Files:**
- Modify: `srmp-web-ui/src/api/gis.ts`
- Modify: `srmp-web-ui/src/api/agent.ts`
- Modify: `srmp-web-ui/src/api/solution.ts`
- Create: `srmp-web-ui/src/views/agent/components/AiTraceButton.vue`
- Create: `srmp-web-ui/src/views/agent/components/AiTraceDrawer.vue`

- [ ] **Step 1: Add region API types**

In `srmp-web-ui/src/api/gis.ts`, add:

```ts
import type { AiSolutionDraftSaveRequest } from './solution'

export interface MapRegionAnalysisRequest {
  geometry: Record<string, any>
  query?: Record<string, any>
  layers?: string[]
  options?: Record<string, any>
}

export function spatialQuery(data: MapRegionAnalysisRequest): Promise<Record<string, any>> {
  return request.post('/api/gis/spatial-query', data)
}

export function analyzeMapRegion(data: MapRegionAnalysisRequest): Promise<Record<string, any>> {
  return request.post('/api/gis/map-region/analysis', data)
}

export interface MapRegionSolutionRequest {
  solutionType?: string
  geometry: Record<string, any>
  query?: Record<string, any>
  layers?: string[]
  options?: Record<string, any>
}

export interface MapRegionSolutionResponse {
  solutionType: string
  title: string
  markdown: string
  regionSummary?: Record<string, any>
  qualityCheck?: Record<string, any>
  sourceSummaries?: Record<string, any>[]
  trace?: Record<string, any>
}

export function generateMapRegionSolution(data: MapRegionSolutionRequest): Promise<MapRegionSolutionResponse> {
  return request.post('/api/gis/map-region/solution', data)
}

export function saveMapRegionSolutionDraft(data: AiSolutionDraftSaveRequest): Promise<Record<string, any>> {
  return request.post('/api/gis/map-region/drafts', data)
}
```

In `srmp-web-ui/src/api/agent.ts`, extend `MapObjectSolutionResponse`:

```ts
trace?: Record<string, any>
```

In `srmp-web-ui/src/api/solution.ts`, extend `AiSolutionDraftSaveRequest`:

```ts
originType?: string
objectType?: string
objectId?: string
regionSummary?: Record<string, any>
trace?: Record<string, any>
```

- [ ] **Step 2: Create `AiTraceButton.vue`**

Create:

```vue
<template>
  <el-button v-if="traceId" size="small" plain @click="$emit('open', trace)">
    查看 Trace
  </el-button>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  trace?: Record<string, any> | null
}>()

defineEmits<{
  (e: 'open', value: Record<string, any>): void
}>()

const traceId = computed(() => props.trace?.traceId || props.trace?.trace_id || '')
</script>
```

- [ ] **Step 3: Create `AiTraceDrawer.vue`**

Create:

```vue
<template>
  <el-drawer
    :model-value="visible"
    title="AI Trace"
    size="560px"
    @update:model-value="$emit('update:visible', $event)"
  >
    <el-empty v-if="!trace" description="暂无 Trace" />
    <template v-else>
      <el-descriptions :column="1" border size="small" class="trace-meta">
        <el-descriptions-item label="TraceId">{{ trace.traceId || trace.trace_id }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ trace.requestType || trace.request_type }}</el-descriptions-item>
        <el-descriptions-item label="模式">{{ trace.mode }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ trace.status }}</el-descriptions-item>
        <el-descriptions-item label="总耗时">{{ trace.totalCostMs || trace.total_cost_ms || 0 }} ms</el-descriptions-item>
        <el-descriptions-item label="降级">{{ trace.fallback }}</el-descriptions-item>
      </el-descriptions>

      <el-timeline>
        <el-timeline-item
          v-for="(step, index) in normalizedSteps"
          :key="step.id || `${step.step_name || step.name}-${index}`"
          :type="timelineType(step.status)"
          :timestamp="`${step.cost_ms ?? step.costMs ?? 0}ms`"
        >
          <div class="step-title">
            <strong>{{ step.step_label || step.label || step.step_name || step.name }}</strong>
            <el-tag size="small" :type="tagType(step.status)">{{ step.status }}</el-tag>
          </div>
          <div class="step-meta">count={{ step.hit_count ?? step.count ?? '-' }}</div>
          <div v-if="step.error_message || step.error" class="error">{{ step.error_message || step.error }}</div>
        </el-timeline-item>
      </el-timeline>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  visible: boolean
  trace?: Record<string, any> | null
}>()

defineEmits<{
  (e: 'update:visible', value: boolean): void
}>()

const normalizedSteps = computed(() => {
  const steps = props.trace?.steps
  return Array.isArray(steps) ? steps : []
})

function tagType(status: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED' || status === 'TIMEOUT') return 'danger'
  if (status === 'SKIPPED') return 'info'
  return 'warning'
}

function timelineType(status: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED' || status === 'TIMEOUT') return 'danger'
  return 'info'
}
</script>

<style scoped>
.trace-meta {
  margin-bottom: 16px;
}

.step-title {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.step-meta {
  color: #64748b;
  font-size: 12px;
}

.error {
  margin-top: 6px;
  color: #dc2626;
  word-break: break-all;
}
</style>
```

- [ ] **Step 4: Run frontend build**

```bash
npm run build
```

Expected: build passes with the existing Vite large chunk warning only.

- [ ] **Step 5: Commit Task 6**

```bash
git add srmp-web-ui/src/api/gis.ts srmp-web-ui/src/api/agent.ts srmp-web-ui/src/api/solution.ts srmp-web-ui/src/views/agent/components/AiTraceButton.vue srmp-web-ui/src/views/agent/components/AiTraceDrawer.vue
git commit -m "feat: add trace UI and region APIs"
```

---

### Task 7: Add Trace Entry Points To Existing Frontend AI Surfaces

**Files:**
- Modify: `srmp-web-ui/src/views/agent/AiChatPage.vue`
- Modify: `srmp-web-ui/src/views/gis/components/AgentChatFloat.vue`
- Modify: `srmp-web-ui/src/views/gis/components/SolutionPreviewDialog.vue`

- [ ] **Step 1: Update AI chat page messages**

In `AiChatPage.vue`, change message type:

```ts
interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  trace?: Record<string, any> | null
}
```

Change messages declaration:

```ts
const messages = ref<ChatMessage[]>([])
const traceDrawerVisible = ref(false)
const activeTrace = ref<Record<string, any> | null>(null)
```

Import:

```ts
import AiTraceButton from './components/AiTraceButton.vue'
import AiTraceDrawer from './components/AiTraceDrawer.vue'
```

Add to assistant message render block below `.content`:

```vue
<AiTraceButton :trace="item.trace" @open="openTrace" />
```

Add drawer at the end of template:

```vue
<AiTraceDrawer v-model:visible="traceDrawerVisible" :trace="activeTrace" />
```

Add method:

```ts
function openTrace(trace: Record<string, any>) {
  activeTrace.value = trace
  traceDrawerVisible.value = true
}
```

When pushing assistant result:

```ts
messages.value.push({
  role: 'assistant',
  content: result?.answer || JSON.stringify(result),
  trace: result?.data?.trace || null
})
```

- [ ] **Step 2: Update AgentChatFloat trace entry**

In `AgentChatFloat.vue`, extend `MessageItem`:

```ts
trace?: Record<string, any> | null
```

Import trace components:

```ts
import AiTraceButton from '../../agent/components/AiTraceButton.vue'
import AiTraceDrawer from '../../agent/components/AiTraceDrawer.vue'
```

Add state:

```ts
const traceDrawerVisible = ref(false)
const activeTrace = ref<Record<string, any> | null>(null)
```

In message render block below message meta:

```vue
<AiTraceButton :trace="item.trace" @open="openTrace" />
```

When pushing assistant message in `send`, add:

```ts
trace: payload.data?.trace || payload.trace || null
```

Add drawer after `SolutionPreviewDialog`:

```vue
<AiTraceDrawer v-model:visible="traceDrawerVisible" :trace="activeTrace" />
```

Add:

```ts
function openTrace(trace: Record<string, any>) {
  activeTrace.value = trace
  traceDrawerVisible.value = true
}
```

- [ ] **Step 3: Update solution preview trace and region summary support**

In `SolutionPreviewDialog.vue`, add optional prop:

```ts
trace?: Record<string, any> | null
```

Update imports and solution type:

```ts
import type { MapObjectSolutionResponse } from '../../../api/agent'
import type { MapRegionSolutionResponse } from '../../../api/gis'
import AiTraceButton from '../../agent/components/AiTraceButton.vue'
import AiTraceDrawer from '../../agent/components/AiTraceDrawer.vue'

type PreviewSolution = MapObjectSolutionResponse | MapRegionSolutionResponse
```

Change `solution` prop type to `PreviewSolution | null`.

Add state:

```ts
const traceDrawerVisible = ref(false)
const activeTrace = computed(() => props.trace || props.solution?.trace || null)
```

Extend `summaryLabels` with region keys:

```ts
areaKm2: '面积 km2',
routeCount: '路线',
sectionCount: '路段',
unitCount: '评定单元',
diseaseCount: '病害',
heavyCount: '重度病害',
avgMqi: '平均 MQI',
avgPci: '平均 PCI'
```

Replace `summaryItems` with:

```ts
const summaryItems = computed(() => {
  const summary = (props.solution as any)?.objectSummary || (props.solution as any)?.regionSummary || {}
  const disease = summary.diseaseSummary || {}
  const assessment = summary.assessmentSummary || {}
  const flat = {
    ...summary,
    diseaseCount: disease.disease_count || disease.diseaseCount,
    heavyCount: disease.heavy_count || disease.heavyCount,
    avgMqi: assessment.avg_mqi || assessment.avgMqi,
    avgPci: assessment.avg_pci || assessment.avgPci
  }
  return Object.keys(summaryLabels)
    .map((key) => ({ key, label: summaryLabels[key], value: formatValue((flat as any)[key]) }))
    .filter((item) => item.value)
})
```

Add normalized quality items:

```ts
const qualityItems = computed(() => {
  const items = props.solution?.qualityCheck?.items
  return Array.isArray(items)
    ? items.map((item: any, index: number) => ({
        key: item.name || item.code || index,
        label: item.name || item.code || item.message || '检查项',
        passed: item.passed === true || item.level === 'OK'
      }))
    : []
})
```

Change the quality item render loop to use `qualityItems`:

```vue
<span
  v-for="item in qualityItems"
  :key="item.key"
  :class="['quality-item', { passed: item.passed }]"
>
  {{ item.label }}
</span>
```

Add footer button:

```vue
<AiTraceButton :trace="activeTrace" @open="traceDrawerVisible = true" />
```

Add drawer:

```vue
<AiTraceDrawer v-model:visible="traceDrawerVisible" :trace="activeTrace" />
```

- [ ] **Step 4: Run frontend build**

```bash
npm run build
```

Expected: build passes with the existing Vite large chunk warning only.

- [ ] **Step 5: Commit Task 7**

```bash
git add srmp-web-ui/src/views/agent/AiChatPage.vue srmp-web-ui/src/views/gis/components/AgentChatFloat.vue srmp-web-ui/src/views/gis/components/SolutionPreviewDialog.vue
git commit -m "feat: show trace on AI results"
```

---

### Task 8: Add Region Drawing And Region Panel To OneMap

**Files:**
- Create: `srmp-web-ui/src/views/gis/components/RegionSelectionPanel.vue`
- Modify: `srmp-web-ui/src/views/gis/OneMap.vue`

- [ ] **Step 1: Create `RegionSelectionPanel.vue`**

Create:

```vue
<template>
  <transition name="drawer-up">
    <div v-if="visible" class="region-selection-panel srmp-card">
      <div class="panel-header">
        <div>
          <strong>区域摘要</strong>
          <el-tag size="small" class="type-tag">{{ geometryTypeLabel }}</el-tag>
        </div>
        <button type="button" @click="$emit('clear')">×</button>
      </div>

      <div class="summary-grid">
        <div v-for="item in displayItems" :key="item.key" class="summary-item">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </div>
      </div>

      <div v-if="hotspots.length" class="hotspots">
        <strong>热点</strong>
        <span v-for="(item, index) in hotspots" :key="index">
          {{ item.route_code || item.routeCode || '区域' }}：病害 {{ item.disease_count || item.diseaseCount || 0 }}
        </span>
      </div>

      <div class="actions">
        <el-button size="small" @click="$emit('clear')">清除选区</el-button>
        <el-button size="small" type="primary" :loading="loading" @click="$emit('generate')">生成区域养护建议</el-button>
        <el-button v-if="trace?.traceId" size="small" plain @click="$emit('trace')">查看 AI Trace</el-button>
      </div>
    </div>
  </transition>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  visible: boolean
  geometryType: 'RECTANGLE' | 'POLYGON'
  summary?: Record<string, any> | null
  trace?: Record<string, any> | null
  loading?: boolean
}>()

defineEmits<{
  (e: 'clear'): void
  (e: 'generate'): void
  (e: 'trace'): void
}>()

const geometryTypeLabel = computed(() => props.geometryType === 'RECTANGLE' ? '矩形' : '多边形')

const displayItems = computed(() => {
  const summary = props.summary || {}
  const disease = summary.diseaseSummary || {}
  const assessment = summary.assessmentSummary || {}
  return [
    { key: 'areaKm2', label: '面积 km2', value: format(summary.areaKm2) },
    { key: 'routeCount', label: '路线', value: format(summary.routeCount) },
    { key: 'sectionCount', label: '路段', value: format(summary.sectionCount) },
    { key: 'unitCount', label: '评定单元', value: format(summary.unitCount) },
    { key: 'diseaseCount', label: '病害', value: format(disease.disease_count || disease.diseaseCount) },
    { key: 'heavyCount', label: '重度病害', value: format(disease.heavy_count || disease.heavyCount) },
    { key: 'avgMqi', label: '平均 MQI', value: format(assessment.avg_mqi || assessment.avgMqi) },
    { key: 'avgPci', label: '平均 PCI', value: format(assessment.avg_pci || assessment.avgPci) }
  ]
})

const hotspots = computed(() => {
  const value = props.summary?.hotspots
  return Array.isArray(value) ? value.slice(0, 3) : []
})

function format(value: any) {
  return value === null || value === undefined || value === '' ? '-' : value
}
</script>

<style scoped>
.region-selection-panel {
  position: absolute;
  left: 50%;
  bottom: 88px;
  z-index: 935;
  width: min(820px, calc(100vw - 360px));
  max-height: 300px;
  padding: 14px;
  transform: translateX(-50%);
  border: 1px solid rgba(226, 232, 240, 0.9);
  background: rgba(255, 255, 255, 0.97);
  overflow: auto;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.panel-header button {
  border: none;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  font-size: 22px;
}

.type-tag {
  margin-left: 8px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(120px, 1fr));
  gap: 8px;
}

.summary-item {
  padding: 8px;
  border-radius: 8px;
  background: #f8fafc;
}

.summary-item span {
  display: block;
  margin-bottom: 4px;
  color: #64748b;
  font-size: 12px;
}

.summary-item strong {
  color: #0f172a;
  font-size: 13px;
}

.hotspots {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 10px;
  color: #475569;
  font-size: 13px;
}

.actions {
  margin-top: 12px;
  text-align: right;
}
</style>
```

- [ ] **Step 2: Add OneMap state and imports**

In `OneMap.vue`, import:

```ts
import RegionSelectionPanel from './components/RegionSelectionPanel.vue'
import SolutionPreviewDialog from './components/SolutionPreviewDialog.vue'
import AiTraceDrawer from '../agent/components/AiTraceDrawer.vue'
import { analyzeMapRegion, generateMapRegionSolution, saveMapRegionSolutionDraft, type MapRegionSolutionResponse } from '../../api/gis'
```

Add state:

```ts
const regionMode = ref<'NONE' | 'RECTANGLE' | 'POLYGON'>('NONE')
const regionGeometry = ref<Record<string, any> | null>(null)
const regionSummary = ref<Record<string, any> | null>(null)
const regionSolution = ref<MapRegionSolutionResponse | null>(null)
const regionLoading = ref(false)
const regionSummaryLoading = ref(false)
const regionPreviewVisible = ref(false)
const regionTraceDrawerVisible = ref(false)
const regionDraftSaving = ref(false)
const regionSavedTask = ref<Record<string, any> | null>(null)
const polygonPoints = ref<L.LatLng[]>([])
let regionLayer: L.Layer | null = null
let rectangleStart: L.LatLng | null = null
```

- [ ] **Step 3: Add region controls and panel to template**

Add a small region tool group near the AI button:

```vue
<div class="region-tool srmp-card">
  <el-button size="small" :type="regionMode === 'RECTANGLE' ? 'primary' : undefined" @click="startRegionDraw('RECTANGLE')">矩形</el-button>
  <el-button size="small" :type="regionMode === 'POLYGON' ? 'primary' : undefined" @click="startRegionDraw('POLYGON')">多边形</el-button>
  <el-button size="small" @click="clearRegion">清除</el-button>
</div>
```

Add after `ObjectDetailDrawer`:

```vue
<RegionSelectionPanel
  :visible="!!regionGeometry"
  :geometry-type="regionMode === 'POLYGON' ? 'POLYGON' : 'RECTANGLE'"
  :summary="regionSummary"
  :trace="regionSolution?.trace || null"
  :loading="regionLoading || regionSummaryLoading"
  @generate="generateRegionSolution"
  @trace="regionTraceDrawerVisible = true"
  @clear="clearRegion"
/>
```

Change `MapStatisticsBar` visibility:

```vue
<MapStatisticsBar
  v-if="!regionGeometry"
  v-model:collapsed="statisticsCollapsed"
  :value="statistics"
  :class="{ 'with-agent': agentVisible }"
/>
```

Add trace drawer:

```vue
<AiTraceDrawer v-model:visible="regionTraceDrawerVisible" :trace="regionSolution?.trace || null" />
```

Add solution preview dialog:

```vue
<SolutionPreviewDialog
  v-model:visible="regionPreviewVisible"
  :solution="regionSolution"
  :trace="regionSolution?.trace || null"
  :save-loading="regionDraftSaving"
  :saved-task="regionSavedTask"
  @save="saveRegionDraft"
/>
```

- [ ] **Step 4: Add drawing methods**

Add:

```ts
function startRegionDraw(mode: 'RECTANGLE' | 'POLYGON') {
  regionMode.value = mode
  polygonPoints.value = []
  rectangleStart = null
  detailVisible.value = false
  selectedDetail.value = null
  ElMessage.info(mode === 'RECTANGLE' ? '拖拽地图绘制矩形选区' : '点击地图绘制多边形，双击结束')
}

function clearRegion() {
  if (regionLayer) {
    map.removeLayer(regionLayer)
    regionLayer = null
  }
  regionMode.value = 'NONE'
  regionGeometry.value = null
  regionSummary.value = null
  regionSolution.value = null
  regionSavedTask.value = null
  regionPreviewVisible.value = false
  polygonPoints.value = []
  rectangleStart = null
}

function setRegionLayer(layer: L.Layer, geometry: Record<string, any>) {
  if (regionLayer) {
    map.removeLayer(regionLayer)
  }
  regionLayer = layer
  regionLayer.addTo(map)
  regionGeometry.value = geometry
  regionSummary.value = buildClientRegionSummary(geometry)
  loadRegionSummary(geometry)
}
```

Register map events in `onMounted` after tile layer setup:

```ts
map.on('mousedown', handleRegionMouseDown)
map.on('mouseup', handleRegionMouseUp)
map.on('click', handleRegionClick)
map.on('dblclick', handleRegionDoubleClick)
```

Add event handlers:

```ts
function handleRegionMouseDown(event: L.LeafletMouseEvent) {
  if (regionMode.value !== 'RECTANGLE') return
  rectangleStart = event.latlng
}

function handleRegionMouseUp(event: L.LeafletMouseEvent) {
  if (regionMode.value !== 'RECTANGLE' || !rectangleStart) return
  const bounds = L.latLngBounds(rectangleStart, event.latlng)
  const layer = L.rectangle(bounds, { color: '#0ea5e9', weight: 2, fillOpacity: 0.12 })
  setRegionLayer(layer, boundsToPolygon(bounds))
  rectangleStart = null
}

function handleRegionClick(event: L.LeafletMouseEvent) {
  if (regionMode.value !== 'POLYGON') return
  polygonPoints.value.push(event.latlng)
  if (regionLayer) {
    map.removeLayer(regionLayer)
  }
  regionLayer = L.polyline(polygonPoints.value, { color: '#0ea5e9', weight: 2 })
  regionLayer.addTo(map)
}

function handleRegionDoubleClick() {
  if (regionMode.value !== 'POLYGON') return
  if (polygonPoints.value.length < 3) {
    ElMessage.warning('多边形至少需要 3 个点')
    return
  }
  const layer = L.polygon(polygonPoints.value, { color: '#0ea5e9', weight: 2, fillOpacity: 0.12 })
  setRegionLayer(layer, pointsToPolygon(polygonPoints.value))
}
```

Add geometry helpers:

```ts
function boundsToPolygon(bounds: L.LatLngBounds) {
  const sw = bounds.getSouthWest()
  const ne = bounds.getNorthEast()
  const nw = L.latLng(ne.lat, sw.lng)
  const se = L.latLng(sw.lat, ne.lng)
  return pointsToPolygon([sw, se, ne, nw])
}

function pointsToPolygon(points: L.LatLng[]) {
  const coords = points.map((p) => [Number(p.lng.toFixed(7)), Number(p.lat.toFixed(7))])
  const first = coords[0]
  const last = coords[coords.length - 1]
  if (first[0] !== last[0] || first[1] !== last[1]) {
    coords.push([first[0], first[1]])
  }
  return { type: 'Polygon', coordinates: [coords] }
}

function buildClientRegionSummary(geometry: Record<string, any>) {
  return {
    geometry,
    areaKm2: '-',
    routeCount: '-',
    sectionCount: '-',
    unitCount: '-',
    diseaseSummary: {},
    assessmentSummary: {},
    hotspots: []
  }
}
```

- [ ] **Step 5: Add region generate and save methods**

Add:

```ts
async function loadRegionSummary(geometry: Record<string, any>) {
  regionSummaryLoading.value = true
  try {
    const result = await analyzeMapRegion({
      geometry,
      query: { ...query },
      layers: activeLayerNames(),
      options: { useBusinessData: true }
    })
    regionSummary.value = result || buildClientRegionSummary(geometry)
  } catch (error: any) {
    regionSummary.value = buildClientRegionSummary(geometry)
    ElMessage.warning(error?.message || '区域统计摘要获取失败')
  } finally {
    regionSummaryLoading.value = false
  }
}

async function generateRegionSolution() {
  if (!regionGeometry.value) {
    ElMessage.warning('请先绘制区域')
    return
  }
  regionLoading.value = true
  try {
    const result = await generateMapRegionSolution({
      solutionType: 'REGION_MAINTENANCE_SUGGESTION',
      geometry: regionGeometry.value,
      query: { ...query },
      layers: activeLayerNames(),
      options: { useBusinessData: true, useKnowledge: true, useOutline: false, topK: 5 }
    })
    regionSolution.value = result
    regionSummary.value = result.regionSummary || regionSummary.value
    regionSavedTask.value = null
    regionPreviewVisible.value = true
    ElMessage.success('区域养护建议已生成')
  } catch (error: any) {
    ElMessage.error(error?.message || '生成区域养护建议失败')
  } finally {
    regionLoading.value = false
  }
}

function activeLayerNames() {
  const result: string[] = []
  if (layers.roadSection) result.push('ROAD_SECTION')
  if (layers.evaluationUnit) result.push('EVALUATION_UNIT')
  if (layers.disease) result.push('DISEASE')
  if (layers.assessment || layers.assessmentResult) result.push('ASSESSMENT_RESULT')
  return result
}

async function saveRegionDraft() {
  if (!regionSolution.value || !regionGeometry.value) return
  regionDraftSaving.value = true
  try {
    const saved = await saveMapRegionSolutionDraft({
      originType: 'MAP_REGION',
      objectType: 'MAP_REGION',
      objectId: regionSolution.value.trace?.traceId || '',
      solutionType: regionSolution.value.solutionType,
      title: regionSolution.value.title,
      markdown: regionSolution.value.markdown,
      routeCode: String(query.routeCode || ''),
      year: Number(query.year) || undefined,
      mapObject: {
        objectType: 'MAP_REGION',
        geometry: regionGeometry.value,
        drawingMode: regionMode.value
      },
      regionSummary: regionSolution.value.regionSummary || {},
      qualityCheck: regionSolution.value.qualityCheck || {},
      trace: regionSolution.value.trace || {},
      sourceSummaries: regionSolution.value.sourceSummaries || [],
      requestContext: { query: { ...query }, layers: activeLayerNames() }
    })
    regionSavedTask.value = saved
    ElMessage.success('区域方案草稿已保存')
  } finally {
    regionDraftSaving.value = false
  }
}
```

- [ ] **Step 6: Add region tool styles**

Add to `OneMap.vue` styles:

```css
.region-tool {
  position: absolute;
  right: 96px;
  bottom: 44px;
  z-index: 940;
  display: flex;
  gap: 8px;
  padding: 8px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  background: rgba(255, 255, 255, 0.96);
}
```

- [ ] **Step 7: Run frontend build**

```bash
npm run build
```

Expected: build passes with the existing Vite large chunk warning only.

- [ ] **Step 8: Commit Task 8**

```bash
git add srmp-web-ui/src/views/gis/components/RegionSelectionPanel.vue srmp-web-ui/src/views/gis/OneMap.vue
git commit -m "feat: add map region drawing panel"
```

---

### Task 9: Final Verification And PR

**Files:**
- Verify all Phase 34 files.

- [ ] **Step 1: Run source acceptance check**

```bash
bash scripts/check-phase34-map-region-trace.sh
```

Expected:

```text
[OK] phase34 map region and unified trace hooks exist
```

- [ ] **Step 2: Run backend compile**

```bash
/tmp/codex-maven/apache-maven-3.9.6/bin/mvn -pl srmp-agent,srmp-gis -am package -DskipTests
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Run frontend build**

```bash
npm run build
```

Expected: build passes with the existing Vite large chunk warning only.

- [ ] **Step 4: Run whitespace check**

```bash
git diff --check
```

Expected: no output.

- [ ] **Step 5: Verify local config changes are not staged**

```bash
git status --short
```

Expected: these user-kept local files remain uncommitted and unstaged:

```text
.claude/settings.local.json
srmp-admin/src/main/resources/application-demo.yml
srmp-admin/src/main/resources/application-dev.yml
```

- [ ] **Step 6: Push and create PR**

```bash
git push -u origin codex/phase34-map-region-trace
```

Open a PR with summary:

```text
Phase 34 adds polygon/rectangle map region selection, backend region aggregation, region maintenance suggestion generation, MAP_REGION draft persistence, and unified trace viewing across AI result surfaces.
```

---

## Self-Review

- Spec coverage: region drawing, bottom region panel, backend spatial aggregation, region solution generation, fixed region trace steps, trace coverage for existing AI calls, MAP_REGION draft persistence, and final verification are all covered by tasks.
- Specificity scan: this plan avoids deferred wording and defines exact files, method names, endpoints, trace step names, commands, and expected verification output.
- Type consistency: backend uses `MapRegionSolutionRequest`, `MapRegionSolutionResponse`, `MapRegionAnalysisRequest`, and `originType = MAP_REGION`; frontend uses the same `geometry`, `regionSummary`, `qualityCheck`, `sourceSummaries`, and `trace` property names.
