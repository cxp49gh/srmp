# Phase35 Template Effectiveness Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a unified template generation pipeline so route reports, map-object solutions, and map-region solutions all expose consistent `templateMeta`, source records, and Trace steps.

**Architecture:** Add a backend template pipeline in `srmp-agent` that owns template matching, variable construction, rendering, validation, and template provenance. Existing route/object/region services prepare business context, call the pipeline, and keep their current analysis/quality responsibilities. The frontend consumes one shared `templateMeta` contract through reusable components in preview dialogs, task details, and template management.

**Tech Stack:** Spring Boot 2.7, Java 8, Lombok, NamedParameterJdbcTemplate, PostgreSQL JSONB, Vue 3, Element Plus, Vite, Bash verification scripts.

---

## File Structure

### Backend DDL

- Create `srmp-admin/src/main/resources/db/phase35_template_effectiveness.sql`
  - Adds `object_type`, `origin_type`, `is_default`, `priority` to `ai_solution_template`.
  - Adds `change_note`, `created_by` to `ai_solution_template_version`.
  - Adds `template_meta` to `ai_solution_task`.
  - Adds `step_data` to `ai_trace_step`.
  - Adds indexes for template matching and task template metadata.

### Backend Template Pipeline

- Create `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/template/SolutionTemplateContext.java`
  - Carries normalized generation context from route/object/region services into the pipeline.
- Create `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/template/TemplatePipelineResult.java`
  - Carries rendered markdown, `templateMeta`, variables, warnings, and source summaries back to callers.
- Create `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/AiSolutionTemplatePipelineService.java`
  - Defines the unified template generation entry point.
- Create `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/impl/AiSolutionTemplatePipelineServiceImpl.java`
  - Implements match, variable build, render, validate, fallback, source summary, and Trace step recording.
- Modify `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/template/MarkdownTemplateRenderer.java`
  - Adds a structured render result that reports missing and unused variables.

### Backend Template Management

- Modify `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/dto/AiSolutionTemplateRequest.java`
  - Adds `originType`, `objectType`, `isDefault`, `priority`, and `changeNote`.
- Modify `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/dto/AiSolutionTemplateQuery.java`
  - Adds `originType`, `objectType`, and `isDefault`.
- Create `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/dto/AiSolutionTemplateMatchPreviewRequest.java`
- Create `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/dto/AiSolutionTemplateRenderPreviewRequest.java`
- Create `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/dto/AiSolutionTemplateStatusRequest.java`
- Create `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/dto/AiSolutionTemplateVersionRequest.java`
- Modify `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/AiSolutionTemplateService.java`
  - Adds `matchPreview`, `renderPreview`, `updateStatus`, `setDefault`, and `createVersion`.
- Modify `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/impl/AiSolutionTemplateServiceImpl.java`
  - Persists new template fields and implements preview/status/version APIs through the pipeline.
- Modify `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/controller/AiSolutionTemplateController.java`
  - Exposes the new template management endpoints.

### Backend Trace and Persistence

- Modify `srmp-agent/src/main/java/com/smartroad/srmp/agent/trace/AiTraceStep.java`
  - Adds `data`.
- Modify `srmp-agent/src/main/java/com/smartroad/srmp/agent/trace/AiTraceContext.java`
  - Adds step helper overloads for step data.
- Modify `srmp-agent/src/main/java/com/smartroad/srmp/agent/trace/service/impl/AiTraceServiceImpl.java`
  - Persists and reads `step_data`.
- Modify `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/dto/AiSolutionDraftSaveRequest.java`
  - Adds `templateMeta`.
- Modify `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/impl/AiSolutionDraftServiceImpl.java`
  - Writes `template_meta`, includes it in task detail, and includes it in task version snapshots.
- Modify `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/impl/AiSolutionGenerateServiceImpl.java`
  - Uses the pipeline for route report rendering.
- Modify `srmp-agent/src/main/java/com/smartroad/srmp/agent/map/solution/dto/MapObjectSolutionResponse.java`
  - Adds `templateMeta` and `sourceSummaries`.
- Modify `srmp-agent/src/main/java/com/smartroad/srmp/agent/map/solution/service/impl/MapObjectSolutionServiceImpl.java`
  - Uses the pipeline for map-object markdown generation.
- Modify `srmp-gis/src/main/java/com/smartroad/srmp/gis/dto/MapRegionSolutionResponse.java`
  - Adds `templateMeta`.
- Modify `srmp-gis/src/main/java/com/smartroad/srmp/gis/service/impl/MapRegionSolutionServiceImpl.java`
  - Uses the pipeline for region markdown generation after region analysis and source retrieval.

### Frontend

- Modify `srmp-web-ui/src/api/solution.ts`
  - Adds template fields and API calls.
- Modify `srmp-web-ui/src/api/agent.ts`
  - Adds template metadata to map-object solution response typing.
- Modify `srmp-web-ui/src/api/gis.ts`
  - Adds template metadata to region solution response typing.
- Create `srmp-web-ui/src/views/agent/components/TemplateMetaCard.vue`
  - Displays matched template, version, fallback reason, missing variables, and warnings.
- Create `srmp-web-ui/src/views/agent/components/TemplateVariableCheckPanel.vue`
  - Displays variables, missing variables, and unused variables consistently.
- Create `srmp-web-ui/src/views/agent/components/TemplateRenderPreviewDialog.vue`
  - Shows match preview and rendered markdown from template management.
- Modify `srmp-web-ui/src/views/gis/components/SolutionPreviewDialog.vue`
  - Shows `TemplateMetaCard` above quality check for object and region previews.
- Modify `srmp-web-ui/src/views/agent/SolutionTasksPage.vue`
  - Shows template metadata and source records in task detail.
- Modify `srmp-web-ui/src/views/agent/SolutionTemplatesPage.vue`
  - Adds origin/object filters and fields, status/default/priority controls, create-version, match-preview, render-preview.

### Verification

- Create `scripts/check-phase35-template-effectiveness.sh`
  - Greps for DDL, pipeline service, preview endpoints, template metadata fields, Trace template steps, and frontend template components.

---

## Task 1: DDL and Verification Skeleton

**Files:**
- Create: `srmp-admin/src/main/resources/db/phase35_template_effectiveness.sql`
- Create: `scripts/check-phase35-template-effectiveness.sh`

- [ ] **Step 1: Write the failing verification script**

Create `scripts/check-phase35-template-effectiveness.sh`:

```bash
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
```

- [ ] **Step 2: Run the script to verify it fails**

Run:

```bash
bash scripts/check-phase35-template-effectiveness.sh
```

Expected: FAIL because `phase35_template_effectiveness.sql` and pipeline classes do not exist yet.

- [ ] **Step 3: Add the Phase35 DDL**

Create `srmp-admin/src/main/resources/db/phase35_template_effectiveness.sql`:

```sql
-- Phase 35: AI solution template effectiveness and unified template metadata
-- psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/phase35_template_effectiveness.sql

ALTER TABLE ai_solution_template
ADD COLUMN IF NOT EXISTS object_type VARCHAR(100),
ADD COLUMN IF NOT EXISTS origin_type VARCHAR(100),
ADD COLUMN IF NOT EXISTS is_default BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS priority INTEGER DEFAULT 0;

ALTER TABLE ai_solution_template_version
ADD COLUMN IF NOT EXISTS change_note VARCHAR(500),
ADD COLUMN IF NOT EXISTS created_by VARCHAR(64);

ALTER TABLE ai_solution_task
ADD COLUMN IF NOT EXISTS template_meta JSONB;

ALTER TABLE ai_trace_step
ADD COLUMN IF NOT EXISTS step_data JSONB;

CREATE INDEX IF NOT EXISTS idx_ai_solution_template_match
ON ai_solution_template(tenant_id, origin_type, object_type, solution_type, status, priority DESC, updated_at DESC)
WHERE deleted = false;

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_solution_template_default
ON ai_solution_template(
    tenant_id,
    COALESCE(origin_type, ''),
    COALESCE(object_type, ''),
    solution_type
)
WHERE is_default = true AND deleted = false;

CREATE INDEX IF NOT EXISTS idx_ai_solution_task_template
ON ai_solution_task(tenant_id, template_id, template_version);

UPDATE ai_solution_template
SET origin_type = COALESCE(origin_type, 'ROUTE_REPORT'),
    object_type = COALESCE(object_type, 'ROAD_ROUTE'),
    priority = COALESCE(priority, 0),
    is_default = COALESCE(is_default, false)
WHERE solution_type = 'ROAD_ASSESSMENT_REPORT'
  AND deleted = false;
```

- [ ] **Step 4: Run the script to verify it still fails at the next missing hook**

Run:

```bash
bash scripts/check-phase35-template-effectiveness.sh
```

Expected: FAIL at `AiSolutionTemplatePipelineService`.

- [ ] **Step 5: Commit**

```bash
git add srmp-admin/src/main/resources/db/phase35_template_effectiveness.sql scripts/check-phase35-template-effectiveness.sh
git commit -m "feat: add phase35 template effectiveness schema"
```

---

## Task 2: Trace Step Data Support

**Files:**
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/trace/AiTraceStep.java`
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/trace/AiTraceContext.java`
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/trace/service/impl/AiTraceServiceImpl.java`

- [ ] **Step 1: Write the failing source check**

Run:

```bash
grep -R "step_data" -n srmp-agent/src/main/java/com/smartroad/srmp/agent/trace
```

Expected: FAIL because Trace step data is not persisted yet.

- [ ] **Step 2: Add `data` to `AiTraceStep`**

Modify `srmp-agent/src/main/java/com/smartroad/srmp/agent/trace/AiTraceStep.java`:

```java
package com.smartroad.srmp.agent.trace;
import lombok.Data;
import java.util.Map;
@Data
public class AiTraceStep {
    private String name;
    private String label;
    private String status;
    private Long costMs;
    private Integer count;
    private String error;
    private Map<String, Object> data;
}
```

- [ ] **Step 3: Add step-data helper overloads**

Modify `AiTraceContext.StepTimer` in `srmp-agent/src/main/java/com/smartroad/srmp/agent/trace/AiTraceContext.java` so the class contains these overloads:

```java
public void success() { success(null, null); }
public void success(Integer count) { success(count, null); }
public void success(Integer count, Map<String, Object> data) { finish("SUCCESS", count, null, data); }
public void skipped() { skipped(null); }
public void skipped(Map<String, Object> data) { finish("SKIPPED", 0, null, data); }
public void failed(Throwable error) { failed(error, null); }
public void failed(Throwable error, Map<String, Object> data) { finish("FAILED", null, error == null ? null : error.getMessage(), data); }
public void timeout(Throwable error) { timeout(error, null); }
public void timeout(Throwable error, Map<String, Object> data) { finish("TIMEOUT", null, error == null ? null : error.getMessage(), data); }
private void finish(String status, Integer count, String error, Map<String, Object> data) {
    AiTraceStep step = new AiTraceStep();
    step.setName(name);
    step.setLabel(label);
    step.setStatus(status);
    step.setCostMs(System.currentTimeMillis() - startMs);
    step.setCount(count);
    step.setError(error);
    step.setData(data);
    context.addStep(step);
}
```

- [ ] **Step 4: Persist and read `step_data`**

Modify `srmp-agent/src/main/java/com/smartroad/srmp/agent/trace/service/impl/AiTraceServiceImpl.java`:

```java
private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

private String toJson(Object value) {
    try {
        return objectMapper.writeValueAsString(value == null ? new LinkedHashMap<>() : value);
    } catch (Exception e) {
        return "{}";
    }
}
```

Change the step insert SQL to:

```java
namedParameterJdbcTemplate.update("insert into ai_trace_step(id,tenant_id,trace_id,step_name,step_label,status,cost_ms,hit_count,error_message,step_data,created_at) values(:id,:tenantId,:traceId,:stepName,:stepLabel,:status,:costMs,:hitCount,:errorMessage,cast(:stepData as jsonb),now())",
    new MapSqlParameterSource().addValue("id", uuid()).addValue("tenantId", tenantId).addValue("traceId", trace.getTraceId())
    .addValue("stepName", step.getName()).addValue("stepLabel", step.getLabel()).addValue("status", step.getStatus())
    .addValue("costMs", step.getCostMs()==null?null:step.getCostMs().intValue()).addValue("hitCount", step.getCount()).addValue("errorMessage", step.getError())
    .addValue("stepData", toJson(step.getData())));
```

Change the step select SQL to:

```java
"select id,tenant_id,trace_id,step_name,step_label,status,cost_ms,hit_count,error_message,step_data,created_at from ai_trace_step where tenant_id=:tenantId and trace_id=:traceId order by created_at asc"
```

- [ ] **Step 5: Run backend compile**

Run:

```bash
/tmp/codex-maven/apache-maven-3.9.6/bin/mvn -pl srmp-agent -am package -DskipTests
```

Expected: SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add srmp-agent/src/main/java/com/smartroad/srmp/agent/trace/AiTraceStep.java srmp-agent/src/main/java/com/smartroad/srmp/agent/trace/AiTraceContext.java srmp-agent/src/main/java/com/smartroad/srmp/agent/trace/service/impl/AiTraceServiceImpl.java
git commit -m "feat: store AI trace step data"
```

---

## Task 3: Template Pipeline Contracts and Renderer Validation

**Files:**
- Create: `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/template/SolutionTemplateContext.java`
- Create: `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/template/TemplatePipelineResult.java`
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/template/MarkdownTemplateRenderer.java`
- Create: `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/AiSolutionTemplatePipelineService.java`

- [ ] **Step 1: Write the failing source check**

Run:

```bash
grep -R "class SolutionTemplateContext" -n srmp-agent/src/main/java
```

Expected: FAIL because the context contract does not exist yet.

- [ ] **Step 2: Create `SolutionTemplateContext`**

Create `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/template/SolutionTemplateContext.java`:

```java
package com.smartroad.srmp.agent.solution.template;

import com.smartroad.srmp.agent.trace.AiTraceContext;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class SolutionTemplateContext {
    private String tenantId;
    private String originType;
    private String objectType;
    private String solutionType;
    private String routeCode;
    private Integer year;
    private String title;
    private String fallbackMarkdown;
    private String templateId;
    private String templateCode;
    private Map<String, Object> mapObject = new LinkedHashMap<>();
    private Map<String, Object> objectSummary = new LinkedHashMap<>();
    private Map<String, Object> regionSummary = new LinkedHashMap<>();
    private Map<String, Object> businessData = new LinkedHashMap<>();
    private List<Map<String, Object>> knowledgeSources = new ArrayList<>();
    private List<Map<String, Object>> outlineSources = new ArrayList<>();
    private Map<String, Object> options = new LinkedHashMap<>();
    private AiTraceContext trace;
}
```

- [ ] **Step 3: Create `TemplatePipelineResult`**

Create `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/template/TemplatePipelineResult.java`:

```java
package com.smartroad.srmp.agent.solution.template;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class TemplatePipelineResult {
    private String markdown;
    private String renderedMarkdown;
    private Map<String, Object> templateMeta = new LinkedHashMap<>();
    private Map<String, Object> variables = new LinkedHashMap<>();
    private List<String> missingVariables = new ArrayList<>();
    private List<String> unusedVariables = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private List<Map<String, Object>> sourceSummaries = new ArrayList<>();
}
```

- [ ] **Step 4: Add structured render support**

Modify `MarkdownTemplateRenderer` so it contains the nested result class and method:

```java
@lombok.Data
public static class RenderResult {
    private String renderedMarkdown;
    private java.util.Map<String, Object> variables = new java.util.LinkedHashMap<>();
    private java.util.List<String> missingVariables = new java.util.ArrayList<>();
    private java.util.List<String> unusedVariables = new java.util.ArrayList<>();
    private java.util.List<String> warnings = new java.util.ArrayList<>();
}

public RenderResult renderWithCheck(String content, Map<String, Object> variables) {
    RenderResult result = new RenderResult();
    String rendered = content == null ? "" : content;
    Map<String, Object> safeVariables = variables == null ? new java.util.LinkedHashMap<>() : variables;
    java.util.List<String> declared = new TemplateVariableParser().parse(rendered);
    java.util.Set<String> declaredSet = new java.util.LinkedHashSet<>(declared);

    for (String key : declared) {
        Object value = safeVariables.get(key);
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            result.getMissingVariables().add(key);
            result.getWarnings().add("变量缺失：" + key);
            continue;
        }
        rendered = rendered.replace("{{" + key + "}}", String.valueOf(value));
        rendered = rendered.replace("{{ " + key + " }}", String.valueOf(value));
        result.getVariables().put(key, value);
    }

    for (Map.Entry<String, Object> entry : safeVariables.entrySet()) {
        if (!declaredSet.contains(entry.getKey()) && entry.getValue() != null && !String.valueOf(entry.getValue()).trim().isEmpty()) {
            result.getUnusedVariables().add(entry.getKey());
        }
    }

    result.setRenderedMarkdown(rendered);
    return result;
}
```

Keep the existing `render(String, Map<String, Object>)` method for Phase21 compatibility.

- [ ] **Step 5: Create the pipeline interface**

Create `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/AiSolutionTemplatePipelineService.java`:

```java
package com.smartroad.srmp.agent.solution.service;

import com.smartroad.srmp.agent.solution.template.SolutionTemplateContext;
import com.smartroad.srmp.agent.solution.template.TemplatePipelineResult;

public interface AiSolutionTemplatePipelineService {
    TemplatePipelineResult generate(SolutionTemplateContext context);
}
```

- [ ] **Step 6: Run backend compile**

Run:

```bash
/tmp/codex-maven/apache-maven-3.9.6/bin/mvn -pl srmp-agent -am package -DskipTests
```

Expected: SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/template/SolutionTemplateContext.java srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/template/TemplatePipelineResult.java srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/template/MarkdownTemplateRenderer.java srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/AiSolutionTemplatePipelineService.java
git commit -m "feat: add solution template pipeline contracts"
```

---

## Task 4: Template Pipeline Implementation

**Files:**
- Create: `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/impl/AiSolutionTemplatePipelineServiceImpl.java`

- [ ] **Step 1: Write the failing source check**

Run:

```bash
grep -R "template_match" -n srmp-agent/src/main/java/com/smartroad/srmp/agent/solution
```

Expected: FAIL because the pipeline does not record template Trace steps yet.

- [ ] **Step 2: Create pipeline implementation skeleton**

Create `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/impl/AiSolutionTemplatePipelineServiceImpl.java` with:

```java
package com.smartroad.srmp.agent.solution.service.impl;

import com.smartroad.srmp.agent.solution.service.AiSolutionTemplatePipelineService;
import com.smartroad.srmp.agent.solution.template.MarkdownTemplateRenderer;
import com.smartroad.srmp.agent.solution.template.SolutionTemplateContext;
import com.smartroad.srmp.agent.solution.template.TemplatePipelineResult;
import com.smartroad.srmp.agent.trace.AiTraceContext;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AiSolutionTemplatePipelineServiceImpl implements AiSolutionTemplatePipelineService {
    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Resource
    private MarkdownTemplateRenderer markdownTemplateRenderer;

    @Override
    public TemplatePipelineResult generate(SolutionTemplateContext context) {
        SolutionTemplateContext safeContext = context == null ? new SolutionTemplateContext() : context;
        String tenantId = safe(safeContext.getTenantId()).isEmpty() ? TenantContextHolder.getTenantId() : safe(safeContext.getTenantId());
        AiTraceContext trace = safeContext.getTrace();

        Map<String, Object> template = matchTemplate(trace, tenantId, safeContext);
        Map<String, Object> variables = buildVariables(trace, safeContext);
        TemplatePipelineResult result = renderTemplate(trace, safeContext, template, variables);
        validateTemplate(trace, result);
        return result;
    }
}
```

- [ ] **Step 3: Add `matchTemplate`**

Add this method to `AiSolutionTemplatePipelineServiceImpl`:

```java
private Map<String, Object> matchTemplate(AiTraceContext trace, String tenantId, SolutionTemplateContext context) {
    AiTraceContext.StepTimer timer = trace == null ? null : trace.step("template_match", "模板匹配");
    try {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("templateId", safe(context.getTemplateId()))
                .addValue("templateCode", safe(context.getTemplateCode()))
                .addValue("originType", safe(context.getOriginType()))
                .addValue("objectType", safe(context.getObjectType()))
                .addValue("solutionType", safe(context.getSolutionType()));
        String sql = "select t.id,t.tenant_id,t.template_code,t.template_name,t.solution_type,t.origin_type,t.object_type,t.current_version,t.status,t.is_default,t.priority,v.content,v.variables,v.source_url " +
                "from ai_solution_template t " +
                "left join ai_solution_template_version v on v.tenant_id=t.tenant_id and v.template_id=t.id and v.version=t.current_version " +
                "where t.tenant_id=:tenantId and t.deleted=false and t.status='ENABLED' ";
        if (!safe(context.getTemplateId()).isEmpty()) {
            sql += "and t.id=:templateId ";
        } else if (!safe(context.getTemplateCode()).isEmpty()) {
            sql += "and t.template_code=:templateCode ";
        } else {
            sql += "and coalesce(t.origin_type,'')=:originType and coalesce(t.object_type,'')=:objectType and t.solution_type=:solutionType ";
        }
        sql += "order by coalesce(t.priority,0) desc, coalesce(t.is_default,false) desc, t.updated_at desc limit 1";
        List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(sql, params);
        Map<String, Object> template = rows.isEmpty() ? fallbackTemplate(context) : new LinkedHashMap<>(rows.get(0));
        if (timer != null) {
            timer.success(rows.isEmpty() ? 0 : 1, templateStepData(template, rows.isEmpty()));
        }
        return template;
    } catch (RuntimeException e) {
        if (timer != null) {
            timer.failed(e);
        }
        throw e;
    }
}
```

- [ ] **Step 4: Add variable building and rendering methods**

Add these methods:

```java
private Map<String, Object> buildVariables(AiTraceContext trace, SolutionTemplateContext context) {
    AiTraceContext.StepTimer timer = trace == null ? null : trace.step("template_variable_build", "模板变量构建");
    Map<String, Object> variables = new LinkedHashMap<>();
    putAll(variables, context.getMapObject());
    putAll(variables, context.getObjectSummary());
    putAll(variables, context.getRegionSummary());
    putAll(variables, context.getBusinessData());
    variables.put("originType", safe(context.getOriginType()));
    variables.put("objectType", safe(context.getObjectType()));
    variables.put("solutionType", safe(context.getSolutionType()));
    variables.put("routeCode", safe(context.getRouteCode()));
    variables.put("year", context.getYear());
    variables.put("title", safe(context.getTitle()));
    variables.put("fallbackMarkdown", safe(context.getFallbackMarkdown()));
    variables.put("knowledgeSources", context.getKnowledgeSources());
    variables.put("outlineSources", context.getOutlineSources());
    normalizeRegionVariables(variables, context.getRegionSummary());
    if (timer != null) {
        timer.success(variables.size());
    }
    return variables;
}

private TemplatePipelineResult renderTemplate(AiTraceContext trace, SolutionTemplateContext context, Map<String, Object> template, Map<String, Object> variables) {
    AiTraceContext.StepTimer timer = trace == null ? null : trace.step("template_render", "模板渲染");
    TemplatePipelineResult result = new TemplatePipelineResult();
    try {
        String content = safe(template.get("content"));
        MarkdownTemplateRenderer.RenderResult rendered = markdownTemplateRenderer.renderWithCheck(content, variables);
        result.setRenderedMarkdown(rendered.getRenderedMarkdown());
        result.setMarkdown(rendered.getRenderedMarkdown());
        result.setVariables(rendered.getVariables());
        result.setMissingVariables(rendered.getMissingVariables());
        result.setUnusedVariables(rendered.getUnusedVariables());
        result.setWarnings(rendered.getWarnings());
        result.setTemplateMeta(templateMeta(context, template, rendered));
        result.setSourceSummaries(sourceSummaries(template, rendered));
        if (timer != null) {
            timer.success(1, templateStepData(template, Boolean.TRUE.equals(template.get("fallback"))));
        }
        return result;
    } catch (RuntimeException e) {
        if (timer != null) {
            timer.failed(e, templateStepData(template, true));
        }
        throw e;
    }
}
```

- [ ] **Step 5: Add validation, fallback, and helpers**

Add these helper methods:

```java
private void validateTemplate(AiTraceContext trace, TemplatePipelineResult result) {
    AiTraceContext.StepTimer timer = trace == null ? null : trace.step("template_validate", "模板变量校验");
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("missingVariables", result.getMissingVariables());
    data.put("unusedVariables", result.getUnusedVariables());
    data.put("warnings", result.getWarnings());
    if (timer != null) {
        timer.success(result.getMissingVariables().size() + result.getWarnings().size(), data);
    }
}

private Map<String, Object> fallbackTemplate(SolutionTemplateContext context) {
    Map<String, Object> template = new LinkedHashMap<>();
    template.put("id", "");
    template.put("template_code", "SYSTEM_FALLBACK_" + safe(context.getSolutionType()));
    template.put("template_name", "系统兜底模板");
    template.put("current_version", "fallback");
    template.put("origin_type", safe(context.getOriginType()));
    template.put("object_type", safe(context.getObjectType()));
    template.put("solution_type", safe(context.getSolutionType()));
    template.put("content", safe(context.getFallbackMarkdown()).isEmpty() ? "# {{title}}\n\n{{businessMarkdown}}\n\n{{fallbackMarkdown}}" : safe(context.getFallbackMarkdown()));
    template.put("fallback", true);
    template.put("fallbackReason", "未找到启用的 " + safe(context.getOriginType()) + " + " + safe(context.getObjectType()) + " + " + safe(context.getSolutionType()) + " 模板，使用系统兜底模板");
    return template;
}

private Map<String, Object> templateMeta(SolutionTemplateContext context, Map<String, Object> template, MarkdownTemplateRenderer.RenderResult rendered) {
    boolean fallback = Boolean.TRUE.equals(template.get("fallback"));
    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("matched", !fallback);
    meta.put("fallback", fallback);
    meta.put("templateId", safe(template.get("id")));
    meta.put("templateCode", safe(template.get("template_code")));
    meta.put("templateName", safe(template.get("template_name")));
    meta.put("templateVersion", safe(template.get("current_version")));
    meta.put("solutionType", safe(context.getSolutionType()));
    meta.put("objectType", safe(context.getObjectType()));
    meta.put("originType", safe(context.getOriginType()));
    meta.put("matchReason", fallback ? "" : "按 originType + objectType + solutionType 匹配，priority=" + safe(template.get("priority")));
    meta.put("fallbackReason", safe(template.get("fallbackReason")));
    meta.put("missingVariables", rendered.getMissingVariables());
    meta.put("unusedVariables", rendered.getUnusedVariables());
    meta.put("warnings", rendered.getWarnings());
    return meta;
}

private List<Map<String, Object>> sourceSummaries(Map<String, Object> template, MarkdownTemplateRenderer.RenderResult rendered) {
    List<Map<String, Object>> result = new ArrayList<>();
    Map<String, Object> source = new LinkedHashMap<>();
    source.put("sourceType", "TEMPLATE");
    source.put("sourceTitle", safe(template.get("template_name")));
    source.put("sourceId", safe(template.get("id")));
    source.put("sourceUrl", safe(template.get("source_url")));
    source.put("contentExcerpt", "模板：" + safe(template.get("template_code")) + " / 版本：" + safe(template.get("current_version")));
    result.add(source);
    Map<String, Object> variableSource = new LinkedHashMap<>();
    variableSource.put("sourceType", "TEMPLATE_VARIABLE");
    variableSource.put("sourceTitle", "模板变量");
    variableSource.put("sourceId", safe(template.get("id")));
    variableSource.put("sourceUrl", "");
    variableSource.put("contentExcerpt", "已填充变量 " + rendered.getVariables().size() + " 个，缺失 " + rendered.getMissingVariables().size() + " 个");
    result.add(variableSource);
    return result;
}
```

Add these helpers:

```java
private void putAll(Map<String, Object> target, Map<String, Object> source) {
    if (target == null || source == null) {
        return;
    }
    for (Map.Entry<String, Object> entry : source.entrySet()) {
        if (entry.getKey() != null && entry.getValue() != null && !String.valueOf(entry.getValue()).trim().isEmpty()) {
            target.put(entry.getKey(), entry.getValue());
        }
    }
}

private void normalizeRegionVariables(Map<String, Object> variables, Map<String, Object> regionSummary) {
    if (variables == null || regionSummary == null) {
        return;
    }
    Object diseaseRaw = regionSummary.get("diseaseSummary");
    if (!(diseaseRaw instanceof Map)) {
        diseaseRaw = regionSummary.get("disease_summary");
    }
    Object assessmentRaw = regionSummary.get("assessmentSummary");
    if (!(assessmentRaw instanceof Map)) {
        assessmentRaw = regionSummary.get("assessment_summary");
    }
    Map disease = diseaseRaw instanceof Map ? (Map) diseaseRaw : new LinkedHashMap();
    Map assessment = assessmentRaw instanceof Map ? (Map) assessmentRaw : new LinkedHashMap();
    putIfPresent(variables, "diseaseCount", first(disease, "disease_count", "diseaseCount"));
    putIfPresent(variables, "heavyDiseaseCount", first(disease, "heavy_count", "heavyCount", "heavyDiseaseCount"));
    putIfPresent(variables, "mediumDiseaseCount", first(disease, "medium_count", "mediumCount", "mediumDiseaseCount"));
    putIfPresent(variables, "avgMqi", first(assessment, "avg_mqi", "avgMqi"));
    putIfPresent(variables, "avgPqi", first(assessment, "avg_pqi", "avgPqi"));
    putIfPresent(variables, "avgPci", first(assessment, "avg_pci", "avgPci"));
}

private Object first(Map source, String... keys) {
    if (source == null) {
        return null;
    }
    for (String key : keys) {
        Object value = source.get(key);
        if (value != null && !String.valueOf(value).trim().isEmpty()) {
            return value;
        }
    }
    return null;
}

private void putIfPresent(Map<String, Object> target, String key, Object value) {
    if (value != null && !String.valueOf(value).trim().isEmpty()) {
        target.put(key, value);
    }
}

private Map<String, Object> templateStepData(Map<String, Object> template, boolean fallback) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("templateCode", safe(template.get("template_code")));
    data.put("templateVersion", safe(template.get("current_version")));
    data.put("matched", !fallback);
    data.put("fallback", fallback);
    data.put("fallbackReason", safe(template.get("fallbackReason")));
    return data;
}

private String safe(Object value) {
    return value == null ? "" : String.valueOf(value).trim();
}
```

- [ ] **Step 6: Run backend compile**

Run:

```bash
/tmp/codex-maven/apache-maven-3.9.6/bin/mvn -pl srmp-agent -am package -DskipTests
```

Expected: SUCCESS.

- [ ] **Step 7: Run the check script**

Run:

```bash
bash scripts/check-phase35-template-effectiveness.sh
```

Expected: FAIL at preview endpoints or frontend components.

- [ ] **Step 8: Commit**

```bash
git add srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/impl/AiSolutionTemplatePipelineServiceImpl.java
git commit -m "feat: implement solution template pipeline"
```

---

## Task 5: Template Management API Enhancements

**Files:**
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/dto/AiSolutionTemplateRequest.java`
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/dto/AiSolutionTemplateQuery.java`
- Create: `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/dto/AiSolutionTemplateMatchPreviewRequest.java`
- Create: `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/dto/AiSolutionTemplateRenderPreviewRequest.java`
- Create: `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/dto/AiSolutionTemplateStatusRequest.java`
- Create: `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/dto/AiSolutionTemplateVersionRequest.java`
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/AiSolutionTemplateService.java`
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/impl/AiSolutionTemplateServiceImpl.java`
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/controller/AiSolutionTemplateController.java`

- [ ] **Step 1: Write the failing endpoint check**

Run:

```bash
grep -R "match-preview" -n srmp-agent/src/main/java/com/smartroad/srmp/agent/solution
```

Expected: FAIL because preview endpoints do not exist yet.

- [ ] **Step 2: Extend template request/query DTOs**

Add these fields to `AiSolutionTemplateRequest`:

```java
private String originType;
private String objectType;
private Boolean isDefault;
private Integer priority;
private String changeNote;
```

Add these fields to `AiSolutionTemplateQuery`:

```java
private String originType;
private String objectType;
private Boolean isDefault;
```

- [ ] **Step 3: Add new DTOs**

Create `AiSolutionTemplateMatchPreviewRequest`:

```java
package com.smartroad.srmp.agent.solution.dto;

import lombok.Data;

@Data
public class AiSolutionTemplateMatchPreviewRequest {
    private String originType;
    private String objectType;
    private String solutionType;
    private String templateId;
    private String templateCode;
}
```

Create `AiSolutionTemplateRenderPreviewRequest`:

```java
package com.smartroad.srmp.agent.solution.dto;

import lombok.Data;

import java.util.Map;

@Data
public class AiSolutionTemplateRenderPreviewRequest {
    private String originType;
    private String objectType;
    private String solutionType;
    private Map<String, Object> variables;
}
```

Create `AiSolutionTemplateStatusRequest`:

```java
package com.smartroad.srmp.agent.solution.dto;

import lombok.Data;

@Data
public class AiSolutionTemplateStatusRequest {
    private String status;
}
```

Create `AiSolutionTemplateVersionRequest`:

```java
package com.smartroad.srmp.agent.solution.dto;

import lombok.Data;

@Data
public class AiSolutionTemplateVersionRequest {
    private String version;
    private String content;
    private String changeNote;
}
```

- [ ] **Step 4: Extend service interface**

Add to `AiSolutionTemplateService`:

```java
Map<String, Object> matchPreview(AiSolutionTemplateMatchPreviewRequest request);

Map<String, Object> renderPreview(String id, AiSolutionTemplateRenderPreviewRequest request);

Map<String, Object> updateStatus(String id, AiSolutionTemplateStatusRequest request);

Map<String, Object> setDefault(String id);

Map<String, Object> createVersion(String id, AiSolutionTemplateVersionRequest request);
```

- [ ] **Step 5: Update template create/list/detail SQL**

In `AiSolutionTemplateServiceImpl.create`, include these parameters:

```java
.addValue("originType", safe(request.getOriginType(), "ROUTE_REPORT"))
.addValue("objectType", safe(request.getObjectType(), "ROAD_ROUTE"))
.addValue("isDefault", Boolean.TRUE.equals(request.getIsDefault()))
.addValue("priority", request.getPriority() == null ? 0 : request.getPriority())
```

Update the insert columns to include:

```sql
origin_type, object_type, is_default, priority
```

Update `list` and `detail` selects to include:

```sql
t.origin_type, t.object_type, t.is_default, t.priority
```

Add query filters:

```sql
and (:originType='' or origin_type=:originType)
and (:objectType='' or object_type=:objectType)
and (:isDefault=false or is_default=true)
```

- [ ] **Step 6: Implement status/default/version/preview methods**

Implement `updateStatus`:

```java
String status = safe(request == null ? null : request.getStatus(), "").toUpperCase(Locale.ROOT);
if (!Arrays.asList("ENABLED", "DISABLED").contains(status)) {
    throw new IllegalArgumentException("status 只支持 ENABLED / DISABLED");
}
namedParameterJdbcTemplate.update(
        "update ai_solution_template set status=:status, updated_at=now() where tenant_id=:tenantId and id=:id",
        new MapSqlParameterSource().addValue("tenantId", TenantContextHolder.getTenantId()).addValue("id", id).addValue("status", status)
);
return detail(id);
```

Implement `setDefault` by loading the selected template, clearing `is_default` for the same `tenant_id + origin_type + object_type + solution_type`, then setting the selected row to true.

Implement `createVersion` by inserting into `ai_solution_template_version`, updating `current_version`, and returning `detail(id)`.

Implement `matchPreview` by building a `SolutionTemplateContext`, calling `AiSolutionTemplatePipelineService.generate`, and returning `result.getTemplateMeta()`.

Implement `renderPreview` by loading template detail, building a context with `templateId`, passing request variables in `businessData`, and returning `renderedMarkdown`, `templateMeta`, `variables`, `missingVariables`, `unusedVariables`, and `warnings`.

- [ ] **Step 7: Add controller endpoints**

Add to `AiSolutionTemplateController`:

```java
@PostMapping("/match-preview")
public R<Map<String, Object>> matchPreview(@RequestBody AiSolutionTemplateMatchPreviewRequest request) {
    return R.ok(aiSolutionTemplateService.matchPreview(request));
}

@PostMapping("/{id}/render-preview")
public R<Map<String, Object>> renderPreview(@PathVariable String id,
                                            @RequestBody AiSolutionTemplateRenderPreviewRequest request) {
    return R.ok(aiSolutionTemplateService.renderPreview(id, request));
}

@PostMapping("/{id}/status")
public R<Map<String, Object>> updateStatus(@PathVariable String id,
                                           @RequestBody AiSolutionTemplateStatusRequest request) {
    return R.ok(aiSolutionTemplateService.updateStatus(id, request));
}

@PostMapping("/{id}/default")
public R<Map<String, Object>> setDefault(@PathVariable String id) {
    return R.ok(aiSolutionTemplateService.setDefault(id));
}

@PostMapping("/{id}/versions")
public R<Map<String, Object>> createVersion(@PathVariable String id,
                                            @RequestBody AiSolutionTemplateVersionRequest request) {
    return R.ok(aiSolutionTemplateService.createVersion(id, request));
}
```

- [ ] **Step 8: Run backend compile**

Run:

```bash
/tmp/codex-maven/apache-maven-3.9.6/bin/mvn -pl srmp-agent -am package -DskipTests
```

Expected: SUCCESS.

- [ ] **Step 9: Commit**

```bash
git add srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/dto srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/AiSolutionTemplateService.java srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/impl/AiSolutionTemplateServiceImpl.java srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/controller/AiSolutionTemplateController.java
git commit -m "feat: enhance solution template management APIs"
```

---

## Task 6: Route, Map Object, Region, and Draft Integration

**Files:**
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/impl/AiSolutionGenerateServiceImpl.java`
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/map/solution/dto/MapObjectSolutionResponse.java`
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/map/solution/service/impl/MapObjectSolutionServiceImpl.java`
- Modify: `srmp-gis/src/main/java/com/smartroad/srmp/gis/dto/MapRegionSolutionResponse.java`
- Modify: `srmp-gis/src/main/java/com/smartroad/srmp/gis/service/impl/MapRegionSolutionServiceImpl.java`
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/dto/AiSolutionDraftSaveRequest.java`
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/impl/AiSolutionDraftServiceImpl.java`

- [ ] **Step 1: Write the failing source check**

Run:

```bash
grep -R "getTemplateMeta" -n srmp-agent/src/main/java srmp-gis/src/main/java
```

Expected: FAIL because response DTOs do not expose `templateMeta` yet.

- [ ] **Step 2: Add response and save DTO fields**

Add to `MapObjectSolutionResponse`:

```java
private Map<String, Object> templateMeta;
private java.util.List<Map<String, Object>> sourceSummaries;
```

Add to `MapRegionSolutionResponse`:

```java
private Map<String, Object> templateMeta;
```

Add to `AiSolutionDraftSaveRequest`:

```java
private Map<String, Object> templateMeta;
```

- [ ] **Step 3: Save `template_meta` in draft service**

In `AiSolutionDraftServiceImpl.saveDraft`, add parameter:

```java
.addValue("templateMeta", toJson(request.getTemplateMeta() == null ? new LinkedHashMap<>() : request.getTemplateMeta(), "{}"))
```

Update insert columns and values:

```sql
template_meta
cast(:templateMeta as jsonb)
```

Include `template_meta` in `loadTask` select:

```sql
template_meta
```

Add `json.put("templateMeta", request.getTemplateMeta() == null ? new LinkedHashMap<>() : request.getTemplateMeta());` in `buildRequestJson`.

- [ ] **Step 4: Integrate route report generation**

Inject `AiSolutionTemplatePipelineService` into `AiSolutionGenerateServiceImpl`. Replace direct `resolveTemplate` + `templateRenderer.render` usage with a `SolutionTemplateContext`:

```java
SolutionTemplateContext templateContext = new SolutionTemplateContext();
templateContext.setTenantId(tenantId);
templateContext.setOriginType("ROUTE_REPORT");
templateContext.setObjectType("ROAD_ROUTE");
templateContext.setSolutionType(request.getSolutionType());
templateContext.setRouteCode(request.getRouteCode());
templateContext.setYear(request.getYear());
templateContext.setTemplateId(request.getTemplateId());
templateContext.setTemplateCode(request.getTemplateCode());
templateContext.setTitle(title);
templateContext.setBusinessData(variables);
templateContext.setFallbackMarkdown(templateContent);
TemplatePipelineResult pipelineResult = aiSolutionTemplatePipelineService.generate(templateContext);
String finalContent = polishWithLlm(pipelineResult.getMarkdown(), knowledgeSources);
```

Pass `pipelineResult.getTemplateMeta()` into task persistence and merge `pipelineResult.getSourceSummaries()` into `ai_solution_source`.

- [ ] **Step 5: Integrate map-object generation**

In `MapObjectSolutionServiceImpl`, keep `buildObjectSummary`, `buildTitle`, and quality checker. Replace:

```java
String markdown = buildMarkdown(solutionType, objectType, summary);
```

with:

```java
String fallbackMarkdown = buildMarkdown(solutionType, objectType, summary);
SolutionTemplateContext templateContext = new SolutionTemplateContext();
templateContext.setOriginType("MAP_OBJECT");
templateContext.setObjectType(objectType);
templateContext.setSolutionType(solutionType.name());
templateContext.setRouteCode(stringValue(summary.get("routeCode"), ""));
templateContext.setYear(ctx.getYear());
templateContext.setTitle(title);
templateContext.setObjectSummary(summary);
templateContext.setMapObject(summary);
templateContext.setFallbackMarkdown(fallbackMarkdown);
templateContext.setTrace(trace);
TemplatePipelineResult pipelineResult = aiSolutionTemplatePipelineService.generate(templateContext);
String markdown = pipelineResult.getMarkdown();
response.setTemplateMeta(pipelineResult.getTemplateMeta());
response.setSourceSummaries(pipelineResult.getSourceSummaries());
```

- [ ] **Step 6: Integrate map-region generation**

In `MapRegionSolutionServiceImpl.generate`, after knowledge retrieval and before quality check, replace direct `runSolutionGenerate` final markdown with:

```java
TemplatePipelineResult pipelineResult = runTemplatePipeline(trace, request, regionSummary, businessMarkdown, sources);
String markdown = pipelineResult.getMarkdown();
```

Create `runTemplatePipeline` that builds:

```java
private TemplatePipelineResult runTemplatePipeline(AiTraceContext trace,
                                                   MapRegionSolutionRequest request,
                                                   Map<String, Object> regionSummary,
                                                   String businessMarkdown,
                                                   Map<String, Object> sources) {
    Map<String, Object> query = request == null || request.getQuery() == null ? new LinkedHashMap<>() : request.getQuery();
    Map<String, Object> businessData = new LinkedHashMap<>();
    businessData.put("businessMarkdown", businessMarkdown);
    businessData.put("hotspots", regionSummary.get("hotspots"));
    businessData.put("sourcePrecision", regionSummary.get("sourcePrecision"));

    SolutionTemplateContext context = new SolutionTemplateContext();
    context.setOriginType("MAP_REGION");
    context.setObjectType("MAP_REGION");
    context.setSolutionType(responseSolutionType(request));
    context.setRouteCode(safe(query.get("routeCode")));
    context.setYear(parseYear(query.get("year")));
    context.setTitle(buildTitle(request, regionSummary));
    context.setRegionSummary(regionSummary);
    context.setBusinessData(businessData);
    context.setKnowledgeSources(toSourceMaps(sources.get("knowledgeSources")));
    context.setOutlineSources(toSourceMaps(sources.get("outlineSources")));
    context.setFallbackMarkdown(businessMarkdown);
    context.setTrace(trace);
    return aiSolutionTemplatePipelineService.generate(context);
}
```

Use these helpers in the same class:

```java
private String responseSolutionType(MapRegionSolutionRequest request) {
    String value = safe(request == null ? null : request.getSolutionType());
    return value.isEmpty() ? "REGION_MAINTENANCE_SUGGESTION" : value;
}

private Integer parseYear(Object value) {
    if (value == null) {
        return null;
    }
    try {
        return Integer.parseInt(String.valueOf(value));
    } catch (Exception e) {
        return null;
    }
}

private List<Map<String, Object>> toSourceMaps(Object raw) {
    List<Map<String, Object>> result = new ArrayList<>();
    if (!(raw instanceof List)) {
        return result;
    }
    for (Object item : (List<?>) raw) {
        if (item instanceof KnowledgeSearchResult) {
            KnowledgeSearchResult source = (KnowledgeSearchResult) item;
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("sourceType", "KNOWLEDGE");
            map.put("sourceTitle", source.getTitle());
            map.put("sourceId", source.getDocumentId());
            map.put("sourceUrl", source.getSourceUrl());
            map.put("contentExcerpt", shortText(source.getContent(), 500));
            result.add(map);
        } else if (item instanceof OutlineSearchResult) {
            OutlineSearchResult source = (OutlineSearchResult) item;
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("sourceType", "OUTLINE");
            map.put("sourceTitle", source.getTitle());
            map.put("sourceId", source.getId());
            map.put("sourceUrl", source.getUrl());
            map.put("contentExcerpt", shortText(source.getText(), 500));
            result.add(map);
        }
    }
    return result;
}

private List<Map<String, Object>> mergeSourceSummaries(List<Map<String, Object>> left, List<Map<String, Object>> right) {
    List<Map<String, Object>> result = new ArrayList<>();
    if (left != null) {
        result.addAll(left);
    }
    if (right != null) {
        result.addAll(right);
    }
    return result;
}
```

Set response:

```java
response.setTemplateMeta(pipelineResult.getTemplateMeta());
response.setSourceSummaries(mergeSourceSummaries(buildSourceSummaries(regionSummary, sources, trace), pipelineResult.getSourceSummaries()));
```

- [ ] **Step 7: Run backend compile**

Run:

```bash
/tmp/codex-maven/apache-maven-3.9.6/bin/mvn -pl srmp-agent,srmp-gis -am package -DskipTests
```

Expected: SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/impl/AiSolutionGenerateServiceImpl.java srmp-agent/src/main/java/com/smartroad/srmp/agent/map/solution/dto/MapObjectSolutionResponse.java srmp-agent/src/main/java/com/smartroad/srmp/agent/map/solution/service/impl/MapObjectSolutionServiceImpl.java srmp-gis/src/main/java/com/smartroad/srmp/gis/dto/MapRegionSolutionResponse.java srmp-gis/src/main/java/com/smartroad/srmp/gis/service/impl/MapRegionSolutionServiceImpl.java srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/dto/AiSolutionDraftSaveRequest.java srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/impl/AiSolutionDraftServiceImpl.java
git commit -m "feat: route solution generation through template pipeline"
```

---

## Task 7: Frontend Shared Template Display

**Files:**
- Modify: `srmp-web-ui/src/api/solution.ts`
- Modify: `srmp-web-ui/src/api/agent.ts`
- Modify: `srmp-web-ui/src/api/gis.ts`
- Create: `srmp-web-ui/src/views/agent/components/TemplateMetaCard.vue`
- Create: `srmp-web-ui/src/views/agent/components/TemplateVariableCheckPanel.vue`
- Modify: `srmp-web-ui/src/views/gis/components/SolutionPreviewDialog.vue`
- Modify: `srmp-web-ui/src/views/gis/OneMap.vue`
- Modify: `srmp-web-ui/src/views/gis/components/AgentChatFloat.vue`
- Modify: `srmp-web-ui/src/views/agent/SolutionTasksPage.vue`

- [ ] **Step 1: Write the failing source check**

Run:

```bash
grep -R "TemplateMetaCard" -n srmp-web-ui/src
```

Expected: FAIL because the shared template card does not exist.

- [ ] **Step 2: Add frontend API fields and calls**

In `srmp-web-ui/src/api/solution.ts`, extend `AiSolutionTemplateRequest` and `AiSolutionTemplateQuery` with:

```ts
originType?: string
objectType?: string
isDefault?: boolean
priority?: number
changeNote?: string
```

Add:

```ts
export function matchSolutionTemplate(data: Record<string, any>): Promise<Record<string, any>> {
  return request.post('/api/ai/solution/templates/match-preview', data)
}

export function renderSolutionTemplatePreview(id: string, data: Record<string, any>): Promise<Record<string, any>> {
  return request.post(`/api/ai/solution/templates/${id}/render-preview`, data)
}

export function updateSolutionTemplateStatus(id: string, status: string): Promise<Record<string, any>> {
  return request.post(`/api/ai/solution/templates/${id}/status`, { status })
}

export function setDefaultSolutionTemplate(id: string): Promise<Record<string, any>> {
  return request.post(`/api/ai/solution/templates/${id}/default`)
}

export function createSolutionTemplateVersion(id: string, data: Record<string, any>): Promise<Record<string, any>> {
  return request.post(`/api/ai/solution/templates/${id}/versions`, data)
}
```

Add `templateMeta?: Record<string, any>` and `sourceSummaries?: Record<string, any>[]` to map-object and map-region response interfaces in `agent.ts` and `gis.ts`.

- [ ] **Step 3: Create `TemplateMetaCard.vue`**

Create `srmp-web-ui/src/views/agent/components/TemplateMetaCard.vue`:

```vue
<template>
  <section v-if="meta && Object.keys(meta).length" class="template-meta">
    <div class="meta-header">
      <strong>{{ matched ? '生成模板' : '模板兜底' }}</strong>
      <el-tag size="small" :type="matched ? 'success' : 'warning'">{{ matched ? '已命中' : '兜底' }}</el-tag>
    </div>
    <el-descriptions :column="2" border size="small">
      <el-descriptions-item label="名称">{{ meta.templateName || meta.template_name || '-' }}</el-descriptions-item>
      <el-descriptions-item label="编码">{{ meta.templateCode || meta.template_code || '-' }}</el-descriptions-item>
      <el-descriptions-item label="版本">{{ meta.templateVersion || meta.template_version || '-' }}</el-descriptions-item>
      <el-descriptions-item label="类型">{{ meta.solutionType || meta.solution_type || '-' }}</el-descriptions-item>
      <el-descriptions-item label="对象">{{ meta.objectType || meta.object_type || '-' }}</el-descriptions-item>
      <el-descriptions-item label="来源">{{ meta.originType || meta.origin_type || '-' }}</el-descriptions-item>
    </el-descriptions>
    <el-alert v-if="fallbackReason" class="mt" type="warning" :title="fallbackReason" show-icon />
    <el-alert v-else-if="matchReason" class="mt" type="success" :title="matchReason" show-icon />
    <div v-if="missingVariables.length" class="variable-row">
      <span>缺失变量</span>
      <el-tag v-for="item in missingVariables" :key="item" type="warning" size="small">{{ item }}</el-tag>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ meta?: Record<string, any> | null }>()
const meta = computed(() => props.meta || {})
const matched = computed(() => meta.value.matched === true || meta.value.fallback === false)
const matchReason = computed(() => meta.value.matchReason || meta.value.match_reason || '')
const fallbackReason = computed(() => meta.value.fallbackReason || meta.value.fallback_reason || '')
const missingVariables = computed(() => {
  const value = meta.value.missingVariables || meta.value.missing_variables
  return Array.isArray(value) ? value : []
})
</script>

<style scoped>
.template-meta { margin-bottom: 12px; padding: 12px; border: 1px solid #e2e8f0; border-radius: 8px; background: #f8fafc; }
.meta-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.mt { margin-top: 10px; }
.variable-row { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 10px; align-items: center; font-size: 13px; color: #475569; }
</style>
```

- [ ] **Step 4: Create `TemplateVariableCheckPanel.vue`**

Create `srmp-web-ui/src/views/agent/components/TemplateVariableCheckPanel.vue`:

```vue
<template>
  <section class="variable-panel">
    <div class="panel-title">变量检查</div>
    <div class="variable-list">
      <el-tag v-for="item in filledKeys" :key="item" size="small">{{ item }}</el-tag>
      <el-empty v-if="filledKeys.length === 0" description="暂无变量" />
    </div>
    <el-alert v-if="missing.length" class="mt" type="warning" :title="`缺失变量：${missing.join('，')}`" show-icon />
    <el-alert v-if="unused.length" class="mt" type="info" :title="`未使用变量：${unused.join('，')}`" show-icon />
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  variables?: Record<string, any> | null
  missingVariables?: string[]
  unusedVariables?: string[]
}>()
const filledKeys = computed(() => Object.keys(props.variables || {}))
const missing = computed(() => props.missingVariables || [])
const unused = computed(() => props.unusedVariables || [])
</script>

<style scoped>
.variable-panel { padding: 12px; border: 1px solid #e2e8f0; border-radius: 8px; background: #fff; }
.panel-title { margin-bottom: 8px; font-weight: 600; }
.variable-list { display: flex; flex-wrap: wrap; gap: 6px; }
.mt { margin-top: 10px; }
</style>
```

- [ ] **Step 5: Use `TemplateMetaCard` in solution preview and task detail**

In `SolutionPreviewDialog.vue`, import and render:

```vue
<TemplateMetaCard :meta="(solution as any)?.templateMeta || (solution as any)?.template_meta || null" />
```

Place it between summary and quality sections.

In `SolutionTasksPage.vue`, import `TemplateMetaCard` and render it above `SolutionQualityPanel`:

```vue
<TemplateMetaCard :meta="detail.template_meta || detail.templateMeta || null" />
```

- [ ] **Step 6: Pass template fields on draft save**

In `OneMap.vue` and `AgentChatFloat.vue`, include:

```ts
templateId: solution.templateMeta?.templateId || '',
templateVersion: solution.templateMeta?.templateVersion || '',
templateName: solution.templateMeta?.templateName || '',
templateMeta: solution.templateMeta || {},
sourceSummaries: solution.sourceSummaries || []
```

- [ ] **Step 7: Run frontend build**

Run:

```bash
npm run build
```

from `srmp-web-ui`.

Expected: SUCCESS with the existing Vite large chunk warning only.

- [ ] **Step 8: Commit**

```bash
git add srmp-web-ui/src/api/solution.ts srmp-web-ui/src/api/agent.ts srmp-web-ui/src/api/gis.ts srmp-web-ui/src/views/agent/components/TemplateMetaCard.vue srmp-web-ui/src/views/agent/components/TemplateVariableCheckPanel.vue srmp-web-ui/src/views/gis/components/SolutionPreviewDialog.vue srmp-web-ui/src/views/gis/OneMap.vue srmp-web-ui/src/views/gis/components/AgentChatFloat.vue srmp-web-ui/src/views/agent/SolutionTasksPage.vue
git commit -m "feat: display solution template metadata"
```

---

## Task 8: Template Management UI and Final Verification

**Files:**
- Create: `srmp-web-ui/src/views/agent/components/TemplateRenderPreviewDialog.vue`
- Modify: `srmp-web-ui/src/views/agent/SolutionTemplatesPage.vue`
- Modify: `scripts/check-phase35-template-effectiveness.sh`

- [ ] **Step 1: Write the failing source check**

Run:

```bash
grep -R "renderSolutionTemplatePreview" -n srmp-web-ui/src/views/agent
```

Expected: FAIL because the template management page cannot preview rendering yet.

- [ ] **Step 2: Create `TemplateRenderPreviewDialog.vue`**

Create `srmp-web-ui/src/views/agent/components/TemplateRenderPreviewDialog.vue`:

```vue
<template>
  <el-dialog :model-value="visible" title="模板生效验证" width="760px" append-to-body @update:model-value="emit('update:visible', $event)">
    <TemplateMetaCard :meta="result?.templateMeta || result?.template_meta || result || null" />
    <TemplateVariableCheckPanel
      :variables="result?.variables || {}"
      :missing-variables="result?.missingVariables || result?.missing_variables || []"
      :unused-variables="result?.unusedVariables || result?.unused_variables || []"
    />
    <pre class="markdown-preview">{{ result?.renderedMarkdown || result?.rendered_markdown || '' }}</pre>
    <template #footer>
      <el-button @click="emit('update:visible', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import TemplateMetaCard from './TemplateMetaCard.vue'
import TemplateVariableCheckPanel from './TemplateVariableCheckPanel.vue'

defineProps<{
  visible: boolean
  result?: Record<string, any> | null
}>()
const emit = defineEmits<{ (e: 'update:visible', value: boolean): void }>()
</script>

<style scoped>
.markdown-preview { margin-top: 12px; max-height: 360px; overflow: auto; white-space: pre-wrap; background: #0f172a; color: #e2e8f0; border-radius: 8px; padding: 12px; line-height: 1.6; }
</style>
```

- [ ] **Step 3: Add origin/object/default/priority controls to `SolutionTemplatesPage.vue`**

Add selects to the create form:

```vue
<el-form-item label="来源场景">
  <el-select v-model="form.originType">
    <el-option label="路线报告" value="ROUTE_REPORT" />
    <el-option label="地图对象" value="MAP_OBJECT" />
    <el-option label="框选区域" value="MAP_REGION" />
  </el-select>
</el-form-item>
<el-form-item label="对象类型">
  <el-select v-model="form.objectType">
    <el-option label="路线" value="ROAD_ROUTE" />
    <el-option label="病害" value="DISEASE" />
    <el-option label="评定结果" value="ASSESSMENT_RESULT" />
    <el-option label="路段" value="ROAD_SECTION" />
    <el-option label="框选区域" value="MAP_REGION" />
  </el-select>
</el-form-item>
<el-form-item label="优先级">
  <el-input-number v-model="form.priority" :min="0" :max="999" />
</el-form-item>
<el-form-item label="默认模板">
  <el-switch v-model="form.isDefault" />
</el-form-item>
```

Add matching fields to `form`:

```ts
originType: 'ROUTE_REPORT',
objectType: 'ROAD_ROUTE',
priority: 0,
isDefault: false,
changeNote: ''
```

- [ ] **Step 4: Add template row actions**

In each template item, add buttons:

```vue
<div class="row-actions">
  <el-button size="small" plain @click.stop="previewTemplate(item)">验证</el-button>
  <el-button size="small" plain @click.stop="setAsDefault(item)">默认</el-button>
  <el-button size="small" plain @click.stop="toggleStatus(item)">{{ item.status === 'ENABLED' ? '停用' : '启用' }}</el-button>
</div>
```

Add methods:

```ts
async function previewTemplate(item: Record<string, any>) {
  const sample = buildPreviewSample(item)
  previewResult.value = await renderSolutionTemplatePreview(item.id, sample)
  previewVisible.value = true
}

async function setAsDefault(item: Record<string, any>) {
  await setDefaultSolutionTemplate(item.id)
  ElMessage.success('已设为默认模板')
  await loadTemplates()
}

async function toggleStatus(item: Record<string, any>) {
  const next = item.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  await updateSolutionTemplateStatus(item.id, next)
  ElMessage.success(next === 'ENABLED' ? '模板已启用' : '模板已停用')
  await loadTemplates()
}
```

Add `buildPreviewSample` returning variables for `DISEASE`, `MAP_REGION`, and route report examples.

- [ ] **Step 5: Mount preview dialog**

Import and render:

```vue
<TemplateRenderPreviewDialog v-model:visible="previewVisible" :result="previewResult" />
```

Add refs:

```ts
const previewVisible = ref(false)
const previewResult = ref<Record<string, any> | null>(null)
```

- [ ] **Step 6: Run final verification**

Run:

```bash
bash scripts/check-phase35-template-effectiveness.sh
```

Expected: SUCCESS with `[OK] phase35 template effectiveness hooks exist`.

Run:

```bash
/tmp/codex-maven/apache-maven-3.9.6/bin/mvn -pl srmp-agent,srmp-gis -am package -DskipTests
```

Expected: SUCCESS.

Run:

```bash
npm run build
```

from `srmp-web-ui`.

Expected: SUCCESS with the existing Vite large chunk warning only.

Run:

```bash
git diff --check
```

Expected: no output.

- [ ] **Step 7: Commit**

```bash
git add srmp-web-ui/src/views/agent/components/TemplateRenderPreviewDialog.vue srmp-web-ui/src/views/agent/SolutionTemplatesPage.vue scripts/check-phase35-template-effectiveness.sh
git commit -m "feat: add template effectiveness management UI"
```

---

## Self-Review

**Spec coverage:** The plan covers unified backend pipeline, template type fields, template source lifecycle, DDL alignment with existing `status/current_version`, Trace step data, object/region/route generation integration, `templateMeta` persistence, frontend shared display, template management preview, and final verification.

**Scope control:** The first implementation excludes approval workflow, template gray release, tenant inheritance, and ordinary Q&A prompt-template management. Ordinary Q&A remains Trace-visible without joining the solution template pipeline.

**Type consistency:** The plan uses `originType`, `objectType`, `solutionType`, `templateMeta`, `sourceSummaries`, `missingVariables`, and `unusedVariables` consistently across Java DTOs and Vue APIs. Database names stay snake_case: `origin_type`, `object_type`, `template_meta`, `step_data`.

**Verification:** Every task has at least one failing source check, compile/build command, and commit step. The final task runs the Phase35 check script, backend package, frontend build, and whitespace check.
