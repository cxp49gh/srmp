# AI Evidence GIS Smoke Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make assessment, disease, and route-summary evidence sources pass strict GIS verification, locate correctly on the map, preserve follow-up context, and pass the target-environment smoke chain.

**Architecture:** Keep the strict source-binding contract unchanged. Fix source identity at the orchestrator boundary, enrich route-summary scope in the Java tool with a real `ROAD_ROUTE` target, enrich verified disease targets with geometry, and derive the disease viewport query in a focused frontend utility. Route-summary location uses a temporary overlay so it communicates statistical scope without turning the route into a persistent selected object.

**Tech Stack:** Java 8, Spring JDBC, JUnit 4, Python 3.11/unittest, Vue 3, TypeScript, Leaflet, Node test runner, Maven, Docker.

---

## File Structure

- Modify `srmp-ai-orchestrator/app/java_tools.py`
  - Select the correct business object ID.
  - Normalize route-summary results into one authoritative source.
- Modify `srmp-ai-orchestrator/tests/test_java_tools.py`
  - Lock assessment ID semantics and route-summary source behavior.
- Modify `srmp-agent/src/main/java/com/smartroad/srmp/agent/tool/impl/MapRegionSummaryTool.java`
  - Resolve a project-scoped route to `ROAD_ROUTE + objectId + stakes`.
- Modify `srmp-agent/src/test/java/com/smartroad/srmp/agent/tool/impl/MapRegionSummaryToolTest.java`
  - Verify route-target enrichment and unresolved-route behavior.
- Modify `srmp-gis/src/main/java/com/smartroad/srmp/gis/service/impl/SourceBindingVerifyServiceImpl.java`
  - Return verified disease geometry.
- Modify `srmp-gis/src/test/java/com/smartroad/srmp/gis/service/impl/SourceBindingVerifyServiceImplTest.java`
  - Verify geometry SQL and resolved target.
- Modify `srmp-web-ui/src/utils/gisSourceLayer.ts`
  - Convert verified GeoJSON into a padded bbox and include it in disease queries.
- Modify `srmp-web-ui/tests/gisSourceLayer.test.mjs`
  - Test Point, line/polygon, invalid geometry, and target query output.
- Modify `srmp-web-ui/src/views/gis/OneMap.vue`
  - Prefer exact object matches before geometry fallback.
  - Render route-summary scope as a temporary overlay.
- Create `srmp-web-ui/tests/oneMapEvidenceLocate.test.mjs`
  - Lock exact-ID matching order and temporary route-summary highlighting.
- Modify `srmp-web-ui/tests/gisUnifiedContextSource.test.mjs`
  - Preserve route-summary title and strict verified target metadata.

### Task 1: Bind assessment evidence to `assessment_result.id`

**Files:**
- Modify: `srmp-ai-orchestrator/tests/test_java_tools.py`
- Modify: `srmp-ai-orchestrator/app/java_tools.py`

- [ ] **Step 1: Write the failing assessment identity test**

Add this test to `JavaToolSourceNormalizationTest`:

```python
def test_assessment_source_uses_result_row_id_not_assessed_object_id(self):
    sources = extract_business_sources([
        ToolResult(
            toolName="gis.queryAssessmentResults",
            success=True,
            summary="查询到 1 条评定结果",
            count=1,
            data={
                "items": [
                    {
                        "id": "assessment-result-1",
                        "object_type": "ROAD_SECTION_LINE",
                        "object_id": "road-section-1",
                        "route_code": "C001140727",
                        "start_stake": 0.324,
                        "end_stake": 10.972,
                        "mqi": 97.254,
                    }
                ]
            },
        )
    ])

    self.assertEqual(1, len(sources))
    source = sources[0]
    self.assertEqual("assessment-result-1", source["sourceId"])
    self.assertEqual("ASSESSMENT_RESULT", source["mapTarget"]["objectType"])
    self.assertEqual("assessment-result-1", source["mapTarget"]["objectId"])
    self.assertEqual(
        "assessment-result-1",
        source["followupContext"]["mapTarget"]["objectId"],
    )
    self.assertEqual("road-section-1", source["raw"]["object_id"])
```

- [ ] **Step 2: Run the test and verify the current bug**

Run:

```bash
PYTHONPATH=srmp-ai-orchestrator \
  srmp-ai-orchestrator/.venv/bin/python -m unittest \
  tests.test_java_tools.JavaToolSourceNormalizationTest.test_assessment_source_uses_result_row_id_not_assessed_object_id -v
```

Run from `srmp-ai-orchestrator/` if module resolution requires it:

```bash
cd srmp-ai-orchestrator
PYTHONPATH=. .venv/bin/python -m unittest \
  tests.test_java_tools.JavaToolSourceNormalizationTest.test_assessment_source_uses_result_row_id_not_assessed_object_id -v
```

Expected: FAIL because `mapTarget.objectId` is `road-section-1`.

- [ ] **Step 3: Add a type-aware business object ID helper**

In `srmp-ai-orchestrator/app/java_tools.py`, add this helper immediately before `_business_source_candidate`:

```python
def _business_object_id(item: Dict[str, Any], object_type: str) -> Any:
    normalized_type = _normalize_object_type(object_type)
    if normalized_type == "ASSESSMENT_RESULT":
        return _first(item, "id", "objectId", "sourceId", "source_id")
    return _first(item, "objectId", "object_id", "id", "sourceId", "source_id")
```

Replace:

```python
object_id = _first(item, "objectId", "object_id", "id", "sourceId", "source_id")
```

with:

```python
object_id = _business_object_id(item, object_type)
```

Keep the existing query-scope fallback only when the result row does not contain a valid ID.

- [ ] **Step 4: Run focused and full source normalization tests**

Run:

```bash
cd srmp-ai-orchestrator
PYTHONPATH=. .venv/bin/python -m unittest \
  tests.test_java_tools.JavaToolSourceNormalizationTest -v
```

Expected: all tests PASS.

- [ ] **Step 5: Commit the assessment identity fix**

```bash
git add srmp-ai-orchestrator/app/java_tools.py \
  srmp-ai-orchestrator/tests/test_java_tools.py
git commit -m "fix(agent): bind assessment sources to result ids"
```

### Task 2: Resolve route summaries to one authoritative route source

**Files:**
- Modify: `srmp-agent/src/test/java/com/smartroad/srmp/agent/tool/impl/MapRegionSummaryToolTest.java`
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/tool/impl/MapRegionSummaryTool.java`
- Modify: `srmp-ai-orchestrator/tests/test_java_tools.py`
- Modify: `srmp-ai-orchestrator/app/java_tools.py`

- [ ] **Step 1: Make the Java test JDBC stub queue route rows**

Replace the `CapturingJdbcTemplate` test stub fields with:

```java
private final List<String> sqlList = new ArrayList<>();
private final java.util.Deque<List<Map<String, Object>>> listResponses =
        new java.util.ArrayDeque<>();
private SqlParameterSource lastParams;

void enqueueList(List<Map<String, Object>> rows) {
    listResponses.addLast(rows);
}
```

Replace `queryForList` with:

```java
@Override
public List<Map<String, Object>> queryForList(
        String sql,
        SqlParameterSource paramSource
) {
    sqlList.add(sql);
    lastParams = paramSource;
    return listResponses.isEmpty()
            ? new ArrayList<Map<String, Object>>()
            : listResponses.removeFirst();
}
```

- [ ] **Step 2: Make the existing route length assertion independent of SQL index**

Add this helper to `CapturingJdbcTemplate`:

```java
String firstSqlContaining(String fragment) {
    for (String sql : sqlList) {
        if (sql.contains(fragment)) {
            return sql;
        }
    }
    return "";
}
```

Replace:

```java
String totalLengthSql = jdbcTemplate.sqlAt(2);
```

with:

```java
String totalLengthSql =
        jdbcTemplate.firstSqlContaining("sum(coalesce(s.length_km");
```

The new route lookup adds one SQL call before aggregate queries, so the existing test must assert intent instead of a fixed index.

- [ ] **Step 3: Write failing Java tests for route target enrichment**

Add:

```java
@Test
public void queryRouteSummaryAddsVerifiedRoadRouteTargetToQueryScope() throws Exception {
    MapRegionSummaryTool tool = new MapRegionSummaryTool();
    CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
    jdbcTemplate.enqueueList(java.util.Collections.singletonList(mapOf(
            "id", "route-1",
            "route_code", "C001140727",
            "start_stake", 0,
            "end_stake", 10.972
    )));
    setField(tool, "namedParameterJdbcTemplate", jdbcTemplate);

    MapAiContext mapContext = new MapAiContext();
    mapContext.setTenantId("tenant-a");
    mapContext.setMode("ROUTE");
    mapContext.setRouteCode("C001140727");
    mapContext.setExtra(mapOf(
            "rawContext", mapOf(
                    "query", mapOf("projectId", "project-a", "sectionTier", "LINE")
            )
    ));
    AiToolContext context = new AiToolContext();
    context.setTenantId("tenant-a");
    context.setMapContext(mapContext);

    AiToolResult result = tool.execute(context, mapOf("limit", 50));

    assertTrue(result.isSuccess());
    Map<?, ?> scope = (Map<?, ?>) ((Map<?, ?>) result.getData()).get("queryScope");
    assertEquals("ROAD_ROUTE", scope.get("objectType"));
    assertEquals("route-1", scope.get("objectId"));
    assertEquals("C001140727", scope.get("routeCode"));
    assertEquals(0, ((Number) scope.get("startStake")).intValue());
    assertEquals(10.972, ((Number) scope.get("endStake")).doubleValue(), 0.0001);
    assertTrue(jdbcTemplate.firstSqlContaining("limit 2")
            .contains("from road_route r"));
}

@Test
public void queryRouteSummaryDoesNotInventTargetWhenRouteIsUnresolved() throws Exception {
    MapRegionSummaryTool tool = new MapRegionSummaryTool();
    CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
    jdbcTemplate.enqueueList(new ArrayList<Map<String, Object>>());
    setField(tool, "namedParameterJdbcTemplate", jdbcTemplate);

    MapAiContext mapContext = new MapAiContext();
    mapContext.setTenantId("tenant-a");
    mapContext.setMode("ROUTE");
    mapContext.setRouteCode("MISSING");
    mapContext.setExtra(mapOf(
            "rawContext", mapOf("query", mapOf("projectId", "project-a"))
    ));
    AiToolContext context = new AiToolContext();
    context.setTenantId("tenant-a");
    context.setMapContext(mapContext);

    AiToolResult result = tool.execute(context, mapOf("limit", 50));

    assertTrue(result.isSuccess());
    Map<?, ?> scope = (Map<?, ?>) ((Map<?, ?>) result.getData()).get("queryScope");
    assertEquals(null, scope.get("objectId"));
    assertEquals(null, scope.get("objectType"));
}
```

- [ ] **Step 4: Run Java tests and verify failure**

Run:

```bash
mvn -pl srmp-agent -am \
  -Dtest=MapRegionSummaryToolTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because `queryScope` has no route object target.

- [ ] **Step 5: Add route-scope resolution to the Java tool**

In `MapRegionSummaryTool`, add:

```java
private Map<String, Object> resolvedQueryScope(
        AiBusinessScope scope,
        MapSqlParameterSource params
) {
    Map<String, Object> queryScope =
            new LinkedHashMap<>(scope.toQueryScope());
    if (safe(scope.getProjectId()).isEmpty()
            || safe(scope.getRouteCode()).isEmpty()) {
        return queryScope;
    }

    List<Map<String, Object>> routes =
            namedParameterJdbcTemplate.queryForList(
                    "select r.id, r.route_code, r.start_stake, r.end_stake "
                            + "from road_route r "
                            + "where r.tenant_id=:tenantId "
                            + "and r.project_id=:projectId "
                            + "and r.route_code=:routeCode "
                            + "and r.deleted=false order by r.id limit 2",
                    params
            );
    if (routes.size() != 1) {
        return queryScope;
    }

    Map<String, Object> route = routes.get(0);
    queryScope.put("objectType", "ROAD_ROUTE");
    queryScope.put("objectId", route.get("id"));
    queryScope.put("routeCode", route.get("route_code"));
    if (route.get("start_stake") != null) {
        queryScope.put("startStake", route.get("start_stake"));
    }
    if (route.get("end_stake") != null) {
        queryScope.put("endStake", route.get("end_stake"));
    }
    return queryScope;
}
```

After the geometry early return, create parameters once:

```java
MapSqlParameterSource p = baseParams(context, args);
Map<String, Object> queryScope = resolvedQueryScope(scope, p);
```

Use this `queryScope` in both non-geometry paths:

- the branch that reuses `map.getRegionSummary()`;
- the branch that runs the aggregate SQL.

Replace each:

```java
data.put("queryScope", scope.toQueryScope());
```

with:

```java
data.put("queryScope", queryScope);
```

Do not invoke route resolution in `executeGeometrySummary`; geometry remains the authoritative target there.

- [ ] **Step 6: Run Java tests and verify pass**

Run:

```bash
mvn -pl srmp-agent -am \
  -Dtest=MapRegionSummaryToolTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 7: Write failing Python route-summary normalization tests**

Replace the old route-only region-summary expectation with:

```python
def test_region_route_scope_becomes_one_road_route_source(self):
    sources = extract_business_sources([
        ToolResult(
            toolName="gis.queryRegionSummary",
            success=True,
            summary="查询到路线统计",
            count=1,
            data={
                "queryScope": {
                    "projectId": "project-a",
                    "objectType": "ROAD_ROUTE",
                    "objectId": "route-1",
                    "routeCode": "C001140727",
                    "startStake": 0,
                    "endStake": 10.972,
                },
                "routeCount": 1,
                "diseaseSummary": {"diseaseCount": 3},
            },
        )
    ])

    self.assertEqual(1, len(sources))
    source = sources[0]
    self.assertEqual("区域统计｜C001140727", source["sourceTitle"])
    self.assertEqual("OBJECT", source["bindingType"])
    self.assertEqual("ROAD_ROUTE", source["mapTarget"]["objectType"])
    self.assertEqual("route-1", source["mapTarget"]["objectId"])
```

Retain a strict unresolved-route test:

```python
def test_unresolved_region_route_scope_stays_non_locatable(self):
    sources = extract_business_sources([
        ToolResult(
            toolName="gis.queryRegionSummary",
            success=True,
            summary="查询到路线统计",
            count=1,
            data={
                "queryScope": {"routeCode": "MISSING"},
                "routeCount": 0,
            },
        )
    ])

    self.assertEqual(1, len(sources))
    self.assertEqual("NONE", sources[0]["bindingType"])
    self.assertNotIn("mapTarget", sources[0])
```

- [ ] **Step 8: Run Python tests and verify failure**

Run:

```bash
cd srmp-ai-orchestrator
PYTHONPATH=. .venv/bin/python -m unittest \
  tests.test_java_tools.JavaToolSourceNormalizationTest -v
```

Expected: FAIL because region summaries currently produce separate summary and scope sources.

- [ ] **Step 9: Normalize each region summary into exactly one source**

Change the region branch in `extract_business_sources` to:

```python
if result.toolName == "gis.queryRegionSummary":
    sources.append(
        normalize_source(
            _region_summary_candidate(result, query_scope),
            origin="BUSINESS_QUERY",
        )
    )
    continue
```

Change `_region_summary_candidate` to:

```python
def _region_summary_candidate(
    result: ToolResult,
    query_scope: Dict[str, Any],
) -> Dict[str, Any]:
    data = result.data if isinstance(result.data, dict) else {}
    scope = query_scope if isinstance(query_scope, dict) else {}
    geometry = data.get("geometry")
    bbox = data.get("bbox")
    route_code = _first(scope, "routeCode", "route_code")
    object_type = _normalize_object_type(
        _first(scope, "objectType", "object_type")
    )
    object_id = _first(scope, "objectId", "object_id")
    return _compact(
        {
            "sourceType": "BUSINESS_DATA",
            "sourceTitle": "｜".join(
                part for part in ["区域统计", str(route_code or "")] if part
            ),
            "toolName": result.toolName,
            "objectType": (
                object_type
                if object_type and object_id
                else "MAP_REGION"
                if geometry not in (None, "", [], {})
                or bbox not in (None, "", [], {})
                else None
            ),
            "objectId": str(object_id) if object_id not in (None, "") else None,
            "routeCode": route_code,
            "startStake": _first(scope, "startStake", "start_stake"),
            "endStake": _first(scope, "endStake", "end_stake"),
            "geometry": geometry,
            "bbox": bbox,
            "sourceId": (
                str(object_id)
                if object_id not in (None, "")
                else f"{result.toolName}:summary"
            ),
            "content": str(result.summary or "")[:500],
            "contentExcerpt": str(result.summary or "")[:500],
            "metadata": {
                "toolName": result.toolName,
                "summary": result.summary,
                "count": result.count,
            },
        }
    )
```

Geometry/bbox remain higher-value range fields because `normalize_source` classifies a source without an object ID as `RANGE`.

- [ ] **Step 10: Run route-summary and complete orchestrator tests**

Run:

```bash
cd srmp-ai-orchestrator
PYTHONPATH=. .venv/bin/python -m unittest \
  tests.test_java_tools \
  tests.test_source_binding \
  tests.test_map_agent_e2e_acceptance -v
```

Expected: PASS.

- [ ] **Step 11: Commit route-summary target work**

```bash
git add \
  srmp-agent/src/main/java/com/smartroad/srmp/agent/tool/impl/MapRegionSummaryTool.java \
  srmp-agent/src/test/java/com/smartroad/srmp/agent/tool/impl/MapRegionSummaryToolTest.java \
  srmp-ai-orchestrator/app/java_tools.py \
  srmp-ai-orchestrator/tests/test_java_tools.py
git commit -m "fix(agent): bind route summaries to route objects"
```

### Task 3: Return verified disease geometry

**Files:**
- Modify: `srmp-gis/src/test/java/com/smartroad/srmp/gis/service/impl/SourceBindingVerifyServiceImplTest.java`
- Modify: `srmp-gis/src/main/java/com/smartroad/srmp/gis/service/impl/SourceBindingVerifyServiceImpl.java`

- [ ] **Step 1: Write the failing disease geometry test**

Add:

```java
@Test
public void diseaseObjectReturnsVerifiedGeometry() {
    jdbc.enqueue(Collections.singletonList(mapOf(
            "id", "disease-1",
            "route_code", "C001140727",
            "start_stake", BigDecimal.ZERO,
            "end_stake", BigDecimal.ZERO,
            "geometry_geojson",
            "{\"type\":\"Point\",\"coordinates\":[112.123,37.456]}"
    )));

    Map<String, Object> result = service.verify(request(
            "project-a",
            "OBJECT",
            mapOf(
                    "objectType", "DISEASE",
                    "objectId", "disease-1"
            )
    ));

    assertEquals("VALID", result.get("bindingStatus"));
    Map<?, ?> target = (Map<?, ?>) result.get("resolvedTarget");
    Map<?, ?> geometry = (Map<?, ?>) target.get("geometry");
    assertEquals("Point", geometry.get("type"));
    assertTrue(jdbc.sqlAt(0).contains("ST_AsGeoJSON(geom)"));
}
```

- [ ] **Step 2: Run the GIS verifier test and verify failure**

Run:

```bash
mvn -pl srmp-gis -am \
  -Dtest=SourceBindingVerifyServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because disease verification does not select or return geometry.

- [ ] **Step 3: Select and parse disease geometry**

Add:

```java
import com.smartroad.srmp.gis.util.GeoJsonParseUtils;
```

Change the disease object SQL to:

```java
return "select id,route_code,start_stake,end_stake,"
        + "ST_AsGeoJSON(geom) as geometry_geojson "
        + "from disease_record "
        + "where tenant_id=:tenantId and project_id=:projectId "
        + "and id=:objectId and deleted=false";
```

After setting `endStake` in the resolved target, add:

```java
Object geometry = GeoJsonParseUtils.parse(
        text(rowValue(row, "geometry_geojson", "geometryGeojson"))
);
if (geometry instanceof Map && !((Map<?, ?>) geometry).isEmpty()) {
    resolved.put("geometry", geometry);
}
```

- [ ] **Step 4: Run GIS tests**

Run:

```bash
mvn -pl srmp-gis -am \
  -Dtest=SourceBindingVerifyServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 5: Commit verified disease geometry**

```bash
git add \
  srmp-gis/src/main/java/com/smartroad/srmp/gis/service/impl/SourceBindingVerifyServiceImpl.java \
  srmp-gis/src/test/java/com/smartroad/srmp/gis/service/impl/SourceBindingVerifyServiceImplTest.java
git commit -m "fix(gis): return geometry for verified disease sources"
```

### Task 4: Build disease bbox queries and temporary route-summary highlights

**Files:**
- Modify: `srmp-web-ui/tests/gisSourceLayer.test.mjs`
- Modify: `srmp-web-ui/src/utils/gisSourceLayer.ts`
- Modify: `srmp-web-ui/src/views/gis/OneMap.vue`
- Create: `srmp-web-ui/tests/oneMapEvidenceLocate.test.mjs`
- Modify: `srmp-web-ui/tests/gisUnifiedContextSource.test.mjs`

- [ ] **Step 1: Write failing geometry bbox tests**

Update the import:

```javascript
import {
  geometryBbox,
  sourceLayerKey,
  sourceTargetQuery
} from '../src/utils/gisSourceLayer.ts'
```

Add:

```javascript
test('geometryBbox pads a Point into a valid viewport', () => {
  assert.deepEqual(
    geometryBbox({
      type: 'Point',
      coordinates: [112.123, 37.456]
    }),
    [112.1225, 37.4555, 112.1235, 37.4565]
  )
})

test('geometryBbox spans nested line and polygon coordinates', () => {
  assert.deepEqual(
    geometryBbox({
      type: 'LineString',
      coordinates: [
        [112.1, 37.2],
        [112.4, 37.5]
      ]
    }),
    [112.1, 37.2, 112.4, 37.5]
  )
})

test('geometryBbox rejects invalid coordinates', () => {
  assert.equal(
    geometryBbox({ type: 'Point', coordinates: ['bad', 37.4] }),
    undefined
  )
})

test('sourceTargetQuery uses verified geometry bbox for disease loading', () => {
  assert.deepEqual(
    sourceTargetQuery(
      { projectId: 'project-1', indexCode: 'MQI' },
      {
        objectType: 'DISEASE',
        routeCode: 'C001140727',
        startStake: 0,
        endStake: 0,
        geometry: {
          type: 'Point',
          coordinates: [112.123, 37.456]
        }
      }
    ),
    {
      projectId: 'project-1',
      indexCode: 'MQI',
      routeCode: 'C001140727',
      stakeStart: 0,
      stakeEnd: 0,
      minLng: 112.1225,
      minLat: 37.4555,
      maxLng: 112.1235,
      maxLat: 37.4565
    }
  )
})
```

- [ ] **Step 2: Run the frontend utility test and verify failure**

Run:

```bash
cd srmp-web-ui
node --experimental-strip-types --test tests/gisSourceLayer.test.mjs
```

Expected: FAIL because `geometryBbox` does not exist and source queries omit bbox.

- [ ] **Step 3: Implement GeoJSON bbox calculation**

Add to `gisSourceLayer.ts`:

```typescript
const DEFAULT_POINT_PADDING = 0.0005

export function geometryBbox(
  geometry: Record<string, any> | null | undefined,
  pointPadding = DEFAULT_POINT_PADDING
): number[] | undefined {
  if (!geometry || typeof geometry !== 'object') return undefined
  const points: Array<[number, number]> = []

  const collect = (value: any) => {
    if (!Array.isArray(value)) return
    if (
      value.length >= 2
      && Number.isFinite(Number(value[0]))
      && Number.isFinite(Number(value[1]))
    ) {
      points.push([Number(value[0]), Number(value[1])])
      return
    }
    value.forEach(collect)
  }

  if (geometry.type === 'GeometryCollection') {
    for (const item of geometry.geometries || []) {
      const bbox = geometryBbox(item, pointPadding)
      if (bbox) {
        points.push([bbox[0], bbox[1]], [bbox[2], bbox[3]])
      }
    }
  } else {
    collect(geometry.coordinates)
  }

  if (!points.length) return undefined
  let minLng = Math.min(...points.map(([lng]) => lng))
  let minLat = Math.min(...points.map(([, lat]) => lat))
  let maxLng = Math.max(...points.map(([lng]) => lng))
  let maxLat = Math.max(...points.map(([, lat]) => lat))
  if (minLng === maxLng) {
    minLng -= pointPadding
    maxLng += pointPadding
  }
  if (minLat === maxLat) {
    minLat -= pointPadding
    maxLat += pointPadding
  }
  return [minLng, minLat, maxLng, maxLat]
}
```

At the end of `sourceTargetQuery`, add:

```typescript
const bbox = target.bbox || geometryBbox(target.geometry)
if (bbox && bbox.length === 4) {
  query.minLng = bbox[0]
  query.minLat = bbox[1]
  query.maxLng = bbox[2]
  query.maxLat = bbox[3]
}
```

- [ ] **Step 4: Run the frontend utility tests**

Run:

```bash
cd srmp-web-ui
node --experimental-strip-types --test tests/gisSourceLayer.test.mjs
```

Expected: PASS.

- [ ] **Step 5: Write a failing OneMap source-location structure test**

Create `srmp-web-ui/tests/oneMapEvidenceLocate.test.mjs`:

```javascript
import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const content = readFileSync(
  new URL('../src/views/gis/OneMap.vue', import.meta.url),
  'utf8'
)

test('verified object lookup prefers exact layer match before geometry fallback', () => {
  const locateStart = content.indexOf(
    'function locateMapTarget(target: GisSourceMapTarget)'
  )
  const exactMatch = content.indexOf(
    'const matched = target.objectId ? findLayerByTarget(target) : null',
    locateStart
  )
  const geometryFallback = content.indexOf(
    'if (target.geometry)',
    locateStart
  )
  assert.ok(exactMatch > locateStart)
  assert.ok(geometryFallback > exactMatch)
})

test('feature matching includes top-level GeoJSON feature id', () => {
  assert.match(
    content,
    /const featureId = firstValue\([\s\S]*?feature\?\.id[\s\S]*?\)/
  )
})

test('route summaries use a temporary source highlight overlay', () => {
  assert.match(content, /function isRouteSummaryTarget\(/)
  assert.match(content, /function highlightRouteSummaryTarget\(/)
  assert.match(content, /aiSourceHighlightLayer = L\.geoJSON/)
  assert.match(
    content,
    /async function handleFeatureClick[\s\S]*?clearAiSourceHighlight\(\)/
  )
  assert.match(
    content,
    /function clearSelection\(\)[\s\S]*?clearAiSourceHighlight\(\)/
  )
})

test('disease source loading refuses a missing verified bbox', () => {
  assert.match(content, /function hasValidSourceBbox\(/)
  assert.match(
    content,
    /病害来源已验证，但缺少可用空间位置/
  )
})
```

Run:

```bash
cd srmp-web-ui
node --test tests/oneMapEvidenceLocate.test.mjs
```

Expected: FAIL because exact matching still follows geometry fallback, top-level `feature.id` is ignored, and route-summary helpers do not exist.

- [ ] **Step 6: Preserve verified source presentation metadata**

In `handleLocateAiSource`, construct the verified target as:

```typescript
const resolvedTarget = applyRecommendedLayerTarget(
  normalizeIncomingMapTarget(
    {
      ...(verification.resolvedTarget || {}),
      title: target.title,
      sourceType: target.sourceType,
      raw: target.raw
    },
    bindingType,
    verification.bindingStatus,
    verification.bindingReason
  ),
  verification.recommendedLayer
)
```

This preserves title only for presentation and highlight mode; all location fields still come from `verification.resolvedTarget`.

- [ ] **Step 7: Prefer exact object matches over geometry fallback**

At the start of `locateMapTarget`, after clearing the old source highlight, add:

```typescript
const matched = target.objectId ? findLayerByTarget(target) : null
if (matched) {
  if (isRouteSummaryTarget(target)) {
    return highlightRouteSummaryTarget(matched)
  }
  const props = {
    ...(matched.feature?.properties || {}),
    layerKey: matched.layerKey,
    objectType: matched.feature?.properties?.objectType
      || matched.feature?.properties?.object_type
      || layerKeyToObjectType(matched.layerKey)
  }
  selectedFeatureProperties.value = props
  selectedDetail.value = props
  highlightLayer(matched.layer)
  zoomToLayer(matched.layer)
  loadObjectDetail(props)
  return true
}
```

Remove the later duplicate `findLayerByTarget` block. Keep geometry and bbox as fallback paths after exact matching.

Also change exact ID extraction in `featureMatchesTarget` to:

```typescript
const featureId = firstValue(
  props.objectId,
  props.object_id,
  props.id,
  props.featureId,
  props.sourceId,
  feature?.id
)
```

Add a disease-query guard near `ensureSourceTargetLayer`:

```typescript
function hasValidSourceBbox(params: GisLayerQuery) {
  const values = [
    params.minLng,
    params.minLat,
    params.maxLng,
    params.maxLat
  ].map(Number)
  return values.every(Number.isFinite)
    && values[0] < values[2]
    && values[1] < values[3]
}
```

Change the disease branch in `ensureSourceTargetLayer` to:

```typescript
if (layerKey === 'disease') {
  params.zoom = ZOOM_DISEASE_DETAIL_MIN
  if (!hasValidSourceBbox(params)) {
    throw new Error('病害来源已验证，但缺少可用空间位置')
  }
}
```

This prevents a verified disease source with missing/invalid geometry from issuing the known-empty no-bbox layer request.

- [ ] **Step 8: Add temporary route-summary overlay helpers**

Add near `clearAiSourceHighlight`:

```typescript
function isRouteSummaryTarget(target: GisSourceMapTarget) {
  return normalizeObjectType(target.objectType) === 'ROAD_ROUTE'
    && String(target.title || '').startsWith('区域统计')
}

function highlightRouteSummaryTarget(matched: {
  layer: L.Layer
  layerKey: string
  feature: any
}) {
  if (!map) return false
  aiSourceHighlightLayer = L.geoJSON(matched.feature as any, {
    style: {
      color: '#f97316',
      weight: 6,
      opacity: 0.95
    }
  }).addTo(map)
  const bounds = (aiSourceHighlightLayer as any).getBounds?.()
  if (bounds?.isValid?.()) {
    map.fitBounds(bounds, { padding: [80, 80], maxZoom: 16 })
  } else {
    zoomToLayer(matched.layer)
  }
  return true
}
```

In the success message branch, use:

```typescript
const routeSummary = isRouteSummaryTarget(resolvedTarget)
ElMessage.success(
  routeSummary
    ? `已验证并高亮统计范围：${resolvedTarget.routeCode || '当前路线'}`
    : layerKey
      ? `已验证来源并定位到${layerKeyText(layerKey)}图层`
      : '已验证并定位到来源关联区域'
)
```

Existing `clearAiSourceHighlight()` calls on new source location, project/layer clearing, and component disposal remain the cleanup mechanism.

Also clear the temporary overlay when the user interacts with a normal map object or explicitly clears selection:

```typescript
async function handleFeatureClick(
  layerKey: string,
  feature: any,
  layer: L.Layer
) {
  if (regionMode.value !== 'NONE') return
  clearAiSourceHighlight()
  // existing feature-click logic follows
}
```

At the beginning of `clearSelection`, add:

```typescript
clearAiSourceHighlight()
```

- [ ] **Step 9: Add a source target metadata regression assertion**

In `gisUnifiedContextSource.test.mjs`, add:

```javascript
const routeSummary = sourceToMapTarget({
  sourceTitle: '区域统计｜C001140727',
  sourceType: 'BUSINESS_DATA',
  bindingType: 'OBJECT',
  bindingStatus: 'UNVERIFIED',
  mapTarget: {
    objectType: 'ROAD_ROUTE',
    objectId: 'route-1',
    routeCode: 'C001140727'
  }
})

assert.equal(routeSummary.title, '区域统计｜C001140727')
assert.equal(routeSummary.objectType, 'ROAD_ROUTE')
assert.equal(routeSummary.objectId, 'route-1')
```

- [ ] **Step 10: Run frontend tests and production build**

Run:

```bash
cd srmp-web-ui
node --experimental-strip-types --test \
  tests/gisSourceLayer.test.mjs \
  tests/gisUnifiedContextSource.test.mjs \
  tests/oneMapEvidenceLocate.test.mjs
npm run build
```

Expected: tests PASS; TypeScript check and Vite build succeed.

- [ ] **Step 11: Commit frontend location behavior**

```bash
git add \
  srmp-web-ui/src/utils/gisSourceLayer.ts \
  srmp-web-ui/tests/gisSourceLayer.test.mjs \
  srmp-web-ui/src/views/gis/OneMap.vue \
  srmp-web-ui/tests/oneMapEvidenceLocate.test.mjs \
  srmp-web-ui/tests/gisUnifiedContextSource.test.mjs
git commit -m "fix(web): locate verified evidence sources on GIS layers"
```

### Task 5: Run the complete local verification suite

**Files:**
- No new files.

- [ ] **Step 1: Run orchestrator tests**

```bash
cd srmp-ai-orchestrator
PYTHONPATH=. .venv/bin/python -m unittest \
  tests.test_java_tools \
  tests.test_source_binding \
  tests.test_business_scope \
  tests.test_map_agent_e2e_acceptance -v
```

Expected: PASS.

- [ ] **Step 2: Run Java module tests**

From repository root:

```bash
mvn -pl srmp-agent,srmp-gis -am \
  -Dtest=MapRegionSummaryToolTest,SourceBindingVerifyServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 3: Run frontend tests and build**

```bash
cd srmp-web-ui
node --experimental-strip-types --test \
  tests/gisSourceLayer.test.mjs \
  tests/gisUnifiedContextSource.test.mjs \
  tests/oneMapEvidenceLocate.test.mjs
npm run build
```

Expected: PASS.

- [ ] **Step 4: Inspect the final diff**

```bash
git diff --check
git status --short
git log --oneline --decorate -6
```

Expected:

- `git diff --check` prints nothing.
- Only intended files are changed or committed.
- The design commit and four implementation commits are present.

### Task 6: Update the running target without changing its environment

**Files:**
- Build artifact: `srmp-admin/target/srmp-admin-1.0.0.jar`
- Build artifact: `srmp-web-ui/dist/`
- Runtime files: `srmp-ai-orchestrator/app/`

The running target has externally configured PostgreSQL, Redis, MinIO, Outline, and LLM values. Do not recreate containers through Compose for this smoke update. Copy verified artifacts into the existing containers and restart them so their current environment remains unchanged.

- [ ] **Step 1: Record current container IDs and environment fingerprints**

Do not print environment values. Run:

```bash
docker inspect srmp-backend srmp-frontend srmp-ai-orchestrator \
  --format '{{.Name}} id={{.Id}} envCount={{len .Config.Env}} image={{.Image}}'
```

Expected: three containers are present and each reports a nonzero environment count.

- [ ] **Step 2: Build backend artifact**

```bash
mvn -pl srmp-admin -am package -DskipTests
test -s srmp-admin/target/srmp-admin-1.0.0.jar
```

Expected: Maven succeeds and the JAR is non-empty.

- [ ] **Step 3: Build frontend artifact**

```bash
cd srmp-web-ui
npm run build
test -s dist/index.html
```

Expected: build succeeds and `dist/index.html` exists.

- [ ] **Step 4: Stop containers and copy artifacts**

From repository root:

```bash
docker stop srmp-backend srmp-frontend srmp-ai-orchestrator
docker cp \
  srmp-admin/target/srmp-admin-1.0.0.jar \
  srmp-backend:/app/srmp-admin.jar
docker cp srmp-web-ui/dist/. \
  srmp-frontend:/usr/share/nginx/html/
docker cp srmp-ai-orchestrator/app/. \
  srmp-ai-orchestrator:/app/app/
```

Expected: all containers stop cleanly and all three copy commands succeed.

- [ ] **Step 5: Start containers and wait for readiness**

```bash
docker start srmp-backend srmp-frontend srmp-ai-orchestrator
```

Then poll:

```bash
for i in $(seq 1 60); do
  backend=$(curl -fsS http://127.0.0.1:8080/actuator/health 2>/dev/null || true)
  runtime=$(curl -fsS http://127.0.0.1:18080/ready 2>/dev/null || true)
  frontend=$(curl -fsS -o /dev/null -w '%{http_code}' http://127.0.0.1:5174/ 2>/dev/null || true)
  if printf '%s' "$backend" | grep -q '"status":"UP"' \
    && printf '%s' "$runtime" | grep -q '"status":"UP"' \
    && [ "$frontend" = "200" ]; then
    break
  fi
  sleep 2
done
curl -fsS http://127.0.0.1:8080/actuator/health
curl -fsS http://127.0.0.1:18080/ready
curl -fsS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:5174/
```

Expected: backend and runtime report `UP`; frontend returns `200`.

- [ ] **Step 6: Confirm container environment fingerprints are unchanged**

Run the same non-secret fingerprint command:

```bash
docker inspect srmp-backend srmp-frontend srmp-ai-orchestrator \
  --format '{{.Name}} id={{.Id}} envCount={{len .Config.Env}} image={{.Image}}'
```

Expected: container IDs, image IDs, and environment counts match Step 1; only restart timestamps changed.

### Task 7: Run target-environment automatic and browser smoke verification

**Files:**
- No new files unless a failure requires a focused regression test.

- [ ] **Step 1: Run automatic route-analysis acceptance**

```bash
./scripts/check-map-agent-e2e.sh \
  --case map.route_analysis \
  --json \
  --fail-fast \
  | tee /tmp/srmp-map-route-analysis.json
```

Expected:

- required tools succeed;
- assessment sources use `assessment_result.id`;
- the route summary is one locatable `ROAD_ROUTE` source;
- all business sources satisfy the strict binding contract;
- command exits zero.

- [ ] **Step 2: Verify source bindings directly**

Use the known target project and extract fresh IDs from the automatic report:

```bash
PROJECT_ID=d20a49cdee904299a4967e196f676c9e
DISEASE_TARGET=$(
  jq -c '.cases[0].sourceBindings[]
    | select(.mapTarget.objectType == "DISEASE")
    | .mapTarget' /tmp/srmp-map-route-analysis.json | head -1
)
ASSESSMENT_TARGET=$(
  jq -c '.cases[0].sourceBindings[]
    | select(.mapTarget.objectType == "ASSESSMENT_RESULT")
    | .mapTarget' /tmp/srmp-map-route-analysis.json | head -1
)
ROUTE_TARGET=$(
  jq -c '.cases[0].sourceBindings[]
    | select(.sourceTitle | startswith("区域统计"))
    | .mapTarget' /tmp/srmp-map-route-analysis.json | head -1
)

for TARGET in "$DISEASE_TARGET" "$ASSESSMENT_TARGET" "$ROUTE_TARGET"; do
  test -n "$TARGET"
  jq -n \
    --arg projectId "$PROJECT_ID" \
    --argjson mapTarget "$TARGET" \
    '{
      projectId: $projectId,
      bindingType: "OBJECT",
      mapTarget: $mapTarget
    }' \
    | curl -fsS \
        -H 'Content-Type: application/json' \
        -H 'X-Tenant-Id: default' \
        --data-binary @- \
        http://127.0.0.1:8080/api/gis/source-binding/verify \
    | jq -e '.data.bindingStatus == "VALID"'
done
```

Expected:

- each response is `VALID`;
- disease `resolvedTarget.geometry` is present;
- assessment object ID equals the result row ID;
- route summary resolves to `ROAD_ROUTE`.

- [ ] **Step 3: Run the browser chain**

Use the in-app browser at:

```text
http://127.0.0.1:5174/gis/one-map
```

Perform:

1. Select the project and route used by the automatic run.
2. Start route analysis.
3. Click a disease source.
4. Confirm `/api/gis/source-binding/verify` succeeds.
5. Confirm the following `/api/gis/diseases` request contains `minLng`, `minLat`, `maxLng`, `maxLat`, and `zoom=17`.
6. Confirm the exact disease marker is highlighted, or verified geometry is shown if the layer cannot return it.
7. Click an assessment source and confirm it verifies and locates.
8. Click the route-summary source and confirm the entire route is fitted and temporarily outlined in orange.
9. Click another source and confirm the route-summary outline clears.
10. Click “追问来源” and confirm the answer echoes the same source ID, object type, route, and stakes.

Expected: every step succeeds without a console error.

- [ ] **Step 4: Capture runtime evidence without secrets**

Run:

```bash
docker logs --since 15m srmp-backend 2>&1 \
  | rg 'source-binding/verify|/api/gis/diseases|ERROR|Exception'
docker logs --since 15m srmp-ai-orchestrator 2>&1 \
  | rg 'gis.queryRegionSummary|gis.queryAssessmentResults|ERROR|Traceback'
```

Expected:

- verification and disease requests are visible;
- no new unexpected exceptions or tracebacks;
- no environment values or credentials are included in the handoff.

- [ ] **Step 5: Final verification checkpoint**

```bash
git status --short
git log --oneline --decorate -8
```

Expected: clean worktree and all planned commits present.
