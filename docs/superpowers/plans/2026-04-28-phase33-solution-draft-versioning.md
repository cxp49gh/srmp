# Phase 33 Solution Draft Versioning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist Phase 32 map-object solution previews as versioned AI solution tasks with draft lifecycle state.

**Architecture:** Reuse the existing `ai_solution_task`, `ai_solution_source`, quality-check, export, and task-list stack. Add map-object metadata, a task version table, a focused draft service, and a small frontend save/history/status surface. Keep `ai_solution_task.status` as generation execution status and use `draft_status` for business lifecycle.

**Tech Stack:** Spring Boot 2.7, Java 8, NamedParameterJdbcTemplate, PostgreSQL JSONB, Vue 3, Element Plus, Vite.

---

## File Structure

- Create `srmp-admin/src/main/resources/db/phase33_ai_solution_draft_version.sql`: migration for task metadata and versions.
- Create `scripts/check-phase33-solution-draft-versioning.sh`: source-level acceptance check.
- Create DTOs under `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/dto/`:
  - `AiSolutionDraftSaveRequest.java`
  - `AiSolutionDraftUpdateRequest.java`
  - `AiSolutionDraftStatusUpdateRequest.java`
  - `AiSolutionSourceSummaryRequest.java`
- Modify `AiSolutionTaskQuery.java`: add `draftStatus`.
- Create `AiSolutionDraftService.java` and `AiSolutionDraftServiceImpl.java`: save/update/status/version logic.
- Create `AiSolutionDraftController.java`: task-management endpoints under `/api/ai/solution/tasks`.
- Modify `AiSolutionGenerateServiceImpl.java`: include Phase 33 columns in task list/detail.
- Modify `AiSolutionQualityServiceImpl.java`: branch quality checks and export metadata for `origin_type = MAP_OBJECT`.
- Modify `srmp-web-ui/src/api/solution.ts`: add draft save/update/status/version APIs.
- Modify `SolutionPreviewDialog.vue`: enable save action and display save result.
- Modify `AgentChatFloat.vue`: call save API with the generated map-object solution response.
- Modify `SolutionTasksPage.vue`: show draft status/object metadata, status actions, and version history drawer.

---

### Task 1: Add Migration And Red Source Check

**Files:**
- Create: `srmp-admin/src/main/resources/db/phase33_ai_solution_draft_version.sql`
- Create: `scripts/check-phase33-solution-draft-versioning.sh`

- [ ] **Step 1: Write the failing source check**

Create `scripts/check-phase33-solution-draft-versioning.sh`:

```bash
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
```

- [ ] **Step 2: Run the red check**

Run:

```bash
bash scripts/check-phase33-solution-draft-versioning.sh
```

Expected: FAIL before implementation because the migration, service, endpoints, and frontend save API do not exist.

- [ ] **Step 3: Add the migration**

Create `srmp-admin/src/main/resources/db/phase33_ai_solution_draft_version.sql`:

```sql
-- Phase 33: AI solution draft versioning
-- psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/phase33_ai_solution_draft_version.sql

ALTER TABLE ai_solution_task
ADD COLUMN IF NOT EXISTS origin_type VARCHAR(50),
ADD COLUMN IF NOT EXISTS object_type VARCHAR(100),
ADD COLUMN IF NOT EXISTS object_id VARCHAR(100),
ADD COLUMN IF NOT EXISTS map_object JSONB,
ADD COLUMN IF NOT EXISTS object_summary JSONB,
ADD COLUMN IF NOT EXISTS draft_status VARCHAR(30),
ADD COLUMN IF NOT EXISTS current_version_no INTEGER DEFAULT 1;

CREATE TABLE IF NOT EXISTS ai_solution_task_version (
    id              VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    task_id         VARCHAR(64) NOT NULL,
    version_no      INTEGER NOT NULL,
    title           VARCHAR(300),
    result_content  TEXT,
    quality_result  JSONB,
    map_object      JSONB,
    object_summary  JSONB,
    source_snapshot JSONB,
    change_note     VARCHAR(500),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_solution_task_origin
ON ai_solution_task(tenant_id, origin_type, object_type, object_id);

CREATE INDEX IF NOT EXISTS idx_ai_solution_task_draft_status
ON ai_solution_task(tenant_id, draft_status, updated_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_solution_task_version_no
ON ai_solution_task_version(tenant_id, task_id, version_no);
```

- [ ] **Step 4: Commit Task 1**

```bash
git add scripts/check-phase33-solution-draft-versioning.sh srmp-admin/src/main/resources/db/phase33_ai_solution_draft_version.sql
git commit -m "feat: add phase33 draft version migration check"
```

---

### Task 2: Add Backend DTOs And Service Contract

**Files:**
- Create: `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/dto/AiSolutionDraftSaveRequest.java`
- Create: `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/dto/AiSolutionDraftUpdateRequest.java`
- Create: `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/dto/AiSolutionDraftStatusUpdateRequest.java`
- Create: `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/dto/AiSolutionSourceSummaryRequest.java`
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/dto/AiSolutionTaskQuery.java`
- Create: `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/AiSolutionDraftService.java`

- [ ] **Step 1: Add DTO files**

Create `AiSolutionSourceSummaryRequest.java`:

```java
package com.smartroad.srmp.agent.solution.dto;

import lombok.Data;

@Data
public class AiSolutionSourceSummaryRequest {
    private String sourceType;
    private String sourceTitle;
    private String sourceId;
    private String sourceUrl;
    private String contentExcerpt;
}
```

Create `AiSolutionDraftSaveRequest.java`:

```java
package com.smartroad.srmp.agent.solution.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AiSolutionDraftSaveRequest {
    private String solutionType;
    private String title;
    private String markdown;
    private String routeCode;
    private Integer year;
    private Map<String, Object> mapObject;
    private Map<String, Object> objectSummary;
    private Map<String, Object> qualityCheck;
    private List<AiSolutionSourceSummaryRequest> sourceSummaries;
    private String templateId;
    private String templateVersion;
    private String templateName;
    private Map<String, Object> options;
    private Map<String, Object> requestContext;
}
```

Create `AiSolutionDraftUpdateRequest.java`:

```java
package com.smartroad.srmp.agent.solution.dto;

import lombok.Data;

@Data
public class AiSolutionDraftUpdateRequest {
    private String title;
    private String markdown;
    private String changeNote;
}
```

Create `AiSolutionDraftStatusUpdateRequest.java`:

```java
package com.smartroad.srmp.agent.solution.dto;

import lombok.Data;

@Data
public class AiSolutionDraftStatusUpdateRequest {
    private String draftStatus;
}
```

- [ ] **Step 2: Extend task query**

Modify `AiSolutionTaskQuery.java`:

```java
package com.smartroad.srmp.agent.solution.dto;

import lombok.Data;

@Data
public class AiSolutionTaskQuery {
    private String solutionType;
    private String routeCode;
    private Integer year;
    private String status;
    private String draftStatus;
    private Integer limit;
}
```

- [ ] **Step 3: Add service contract**

Create `AiSolutionDraftService.java`:

```java
package com.smartroad.srmp.agent.solution.service;

import com.smartroad.srmp.agent.solution.dto.AiSolutionDraftSaveRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionDraftStatusUpdateRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionDraftUpdateRequest;

import java.util.List;
import java.util.Map;

public interface AiSolutionDraftService {
    Map<String, Object> saveMapObjectDraft(AiSolutionDraftSaveRequest request);

    Map<String, Object> updateDraft(String taskId, AiSolutionDraftUpdateRequest request);

    Map<String, Object> updateDraftStatus(String taskId, AiSolutionDraftStatusUpdateRequest request);

    List<Map<String, Object>> versions(String taskId);
}
```

- [ ] **Step 4: Commit Task 2**

```bash
git add srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/dto srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/AiSolutionDraftService.java
git commit -m "feat: add solution draft DTO contracts"
```

---

### Task 3: Implement Draft Persistence Service And Controller

**Files:**
- Create: `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/impl/AiSolutionDraftServiceImpl.java`
- Create: `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/controller/AiSolutionDraftController.java`

- [ ] **Step 1: Implement controller**

Create `AiSolutionDraftController.java`:

```java
package com.smartroad.srmp.agent.solution.controller;

import com.smartroad.srmp.agent.solution.dto.AiSolutionDraftSaveRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionDraftStatusUpdateRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionDraftUpdateRequest;
import com.smartroad.srmp.agent.solution.service.AiSolutionDraftService;
import com.smartroad.srmp.common.core.R;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/solution/tasks")
public class AiSolutionDraftController {

    @Resource
    private AiSolutionDraftService aiSolutionDraftService;

    @PostMapping("/map-object-drafts")
    public R<Map<String, Object>> saveMapObjectDraft(@RequestBody AiSolutionDraftSaveRequest request) {
        return R.ok(aiSolutionDraftService.saveMapObjectDraft(request));
    }

    @PutMapping("/{id}")
    public R<Map<String, Object>> updateDraft(@PathVariable String id,
                                              @RequestBody AiSolutionDraftUpdateRequest request) {
        return R.ok(aiSolutionDraftService.updateDraft(id, request));
    }

    @PostMapping("/{id}/draft-status")
    public R<Map<String, Object>> updateDraftStatus(@PathVariable String id,
                                                    @RequestBody AiSolutionDraftStatusUpdateRequest request) {
        return R.ok(aiSolutionDraftService.updateDraftStatus(id, request));
    }

    @GetMapping("/{id}/versions")
    public R<List<Map<String, Object>>> versions(@PathVariable String id) {
        return R.ok(aiSolutionDraftService.versions(id));
    }
}
```

- [ ] **Step 2: Implement service with transactions**

Create `AiSolutionDraftServiceImpl.java` with these required behaviors:

```java
package com.smartroad.srmp.agent.solution.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroad.srmp.agent.solution.dto.AiSolutionDraftSaveRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionDraftStatusUpdateRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionDraftUpdateRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionSourceSummaryRequest;
import com.smartroad.srmp.agent.solution.service.AiSolutionDraftService;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;

@Service
public class AiSolutionDraftServiceImpl implements AiSolutionDraftService {

    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public Map<String, Object> saveMapObjectDraft(AiSolutionDraftSaveRequest request) {
        validateSave(request);
        String tenantId = TenantContextHolder.getTenantId();
        String taskId = uuid();
        Map<String, Object> objectSummary = request.getObjectSummary() == null ? new LinkedHashMap<>() : request.getObjectSummary();
        Map<String, Object> quality = normalizeMapObjectQuality(request.getQualityCheck());
        String sourceSnapshot = toJson(buildSourceSnapshot(request));

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
                .addValue("requestJson", toJson(buildRequestJson(request)))
                .addValue("resultContent", request.getMarkdown())
                .addValue("qualityResult", toJson(quality))
                .addValue("originType", "MAP_OBJECT")
                .addValue("objectType", firstString(objectSummary, request.getMapObject(), "objectType", "object_type", "type"))
                .addValue("objectId", firstString(objectSummary, request.getMapObject(), "objectId", "object_id", "id"))
                .addValue("mapObject", toJson(request.getMapObject()))
                .addValue("objectSummary", toJson(objectSummary))
                .addValue("draftStatus", "DRAFT")
                .addValue("currentVersionNo", 1);

        namedParameterJdbcTemplate.update(
                "insert into ai_solution_task(" +
                        "id, tenant_id, solution_type, title, route_code, year, template_id, template_version, status, request_json, result_content, quality_result, created_at, updated_at, origin_type, object_type, object_id, map_object, object_summary, draft_status, current_version_no" +
                        ") values (" +
                        ":id, :tenantId, :solutionType, :title, :routeCode, :year, :templateId, :templateVersion, :status, cast(:requestJson as jsonb), :resultContent, cast(:qualityResult as jsonb), now(), now(), :originType, :objectType, :objectId, cast(:mapObject as jsonb), cast(:objectSummary as jsonb), :draftStatus, :currentVersionNo" +
                        ")",
                params);

        insertSources(tenantId, taskId, request);
        insertVersion(tenantId, taskId, 1, request.getTitle(), request.getMarkdown(), quality, request.getMapObject(), objectSummary, sourceSnapshot, "创建草稿");
        return loadTask(tenantId, taskId);
    }
}
```

Add helper methods in the same class: `validateSave`, `normalizeMapObjectQuality`, `buildSourceSnapshot`, `buildRequestJson`, `insertSources`, `insertVersion`, `loadTask`, `safe`, `firstString`, `toJson`, and `uuid`. Use the SQL names shown in Task 1 and Task 3.

- [ ] **Step 3: Implement update/status/version methods**

In `AiSolutionDraftServiceImpl`, add:

```java
@Override
@Transactional
public Map<String, Object> updateDraft(String taskId, AiSolutionDraftUpdateRequest request) {
    if (request == null || safe(request.getMarkdown()).isEmpty()) {
        throw new IllegalArgumentException("markdown 不能为空");
    }
    String tenantId = TenantContextHolder.getTenantId();
    Map<String, Object> task = lockTask(tenantId, taskId);
    if (task.isEmpty()) {
        throw new IllegalArgumentException("方案任务不存在：" + taskId);
    }
    if (!"DRAFT".equals(safe(task.get("draft_status")))) {
        throw new IllegalArgumentException("只有草稿状态可编辑");
    }
    int nextVersion = intValue(task.get("current_version_no"), 1) + 1;
    String title = safe(request.getTitle()).isEmpty() ? safe(task.get("title")) : safe(request.getTitle());

    MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("id", taskId)
            .addValue("title", title)
            .addValue("resultContent", request.getMarkdown())
            .addValue("currentVersionNo", nextVersion);
    namedParameterJdbcTemplate.update(
            "update ai_solution_task set title=:title, result_content=:resultContent, current_version_no=:currentVersionNo, updated_at=now() where tenant_id=:tenantId and id=:id",
            params);

    insertVersion(tenantId, taskId, nextVersion, title, request.getMarkdown(), task.get("quality_result"), task.get("map_object"), task.get("object_summary"), "[]", safe(request.getChangeNote()));
    return loadTask(tenantId, taskId);
}

@Override
@Transactional
public Map<String, Object> updateDraftStatus(String taskId, AiSolutionDraftStatusUpdateRequest request) {
    String next = request == null ? "" : safe(request.getDraftStatus()).toUpperCase(Locale.ROOT);
    if (!Arrays.asList("DRAFT", "CONFIRMED", "ARCHIVED").contains(next)) {
        throw new IllegalArgumentException("draftStatus 只支持 DRAFT / CONFIRMED / ARCHIVED");
    }
    String tenantId = TenantContextHolder.getTenantId();
    Map<String, Object> task = lockTask(tenantId, taskId);
    String current = safe(task.get("draft_status"));
    if (!canTransition(current, next)) {
        throw new IllegalArgumentException("不允许从 " + current + " 流转到 " + next);
    }
    namedParameterJdbcTemplate.update(
            "update ai_solution_task set draft_status=:draftStatus, updated_at=now() where tenant_id=:tenantId and id=:id",
            new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", taskId).addValue("draftStatus", next));
    return loadTask(tenantId, taskId);
}

private Map<String, Object> lockTask(String tenantId, String taskId) {
    List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(
            "select id, tenant_id, title, result_content, quality_result, map_object, object_summary, draft_status, current_version_no from ai_solution_task where tenant_id=:tenantId and id=:id for update",
            new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", taskId));
    return rows.isEmpty() ? new LinkedHashMap<>() : rows.get(0);
}

private boolean canTransition(String current, String next) {
    String from = current == null || current.isEmpty() ? "DRAFT" : current;
    if (from.equals(next)) return true;
    if ("DRAFT".equals(from)) return "CONFIRMED".equals(next) || "ARCHIVED".equals(next);
    if ("CONFIRMED".equals(from)) return "ARCHIVED".equals(next);
    return false;
}
```

- [ ] **Step 4: Run backend compile**

Run:

```bash
/tmp/codex-maven/apache-maven-3.9.6/bin/mvn -pl srmp-agent -am package -DskipTests
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit Task 3**

```bash
git add srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/controller/AiSolutionDraftController.java srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/impl/AiSolutionDraftServiceImpl.java
git commit -m "feat: persist map object solution drafts"
```

---

### Task 4: Extend Existing Task And Quality Services

**Files:**
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/impl/AiSolutionGenerateServiceImpl.java`
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/impl/AiSolutionQualityServiceImpl.java`

- [ ] **Step 1: Include Phase 33 columns in task detail/list**

In `AiSolutionGenerateServiceImpl.task`, include:

```sql
origin_type, object_type, object_id, map_object, object_summary, draft_status, current_version_no
```

In `AiSolutionGenerateServiceImpl.tasks`, add query param:

```java
.addValue("draftStatus", safe(query == null ? null : query.getDraftStatus()))
```

and add SQL filter:

```sql
and (:draftStatus='' or draft_status=:draftStatus)
```

The list select must also return:

```sql
origin_type, object_type, object_id, draft_status, current_version_no
```

- [ ] **Step 2: Branch quality checks for MAP_OBJECT**

In `AiSolutionQualityServiceImpl.loadTask`, select:

```sql
origin_type, object_type, object_id, object_summary, draft_status
```

At the top of `check(String taskId)`, after loading sources/content:

```java
if ("MAP_OBJECT".equals(asString(task.get("origin_type")))) {
    Map<String, Object> result = checkMapObjectTask(task, sources, content);
    saveQualityResult(taskId, result);
    return result;
}
```

Add `checkMapObjectTask`:

```java
private Map<String, Object> checkMapObjectTask(Map<String, Object> task, List<Map<String, Object>> sources, String content) {
    List<Map<String, Object>> items = new ArrayList<>();
    int score = 100;
    score -= addQualityItem(items, hasAny(task, "route_code", "object_id") || containsAny(content, "路线", "桩号"), "MAP_OBJECT_POSITION", "地图对象位置", 20);
    score -= addQualityItem(items, containsAny(content, "成因", "原因"), "MAP_OBJECT_CAUSE", "成因判断", 15);
    score -= addQualityItem(items, containsAny(content, "处置", "养护建议", "推荐"), "MAP_OBJECT_TREATMENT", "处置建议", 20);
    score -= addQualityItem(items, containsAny(content, "优先级", "P1", "P2", "P3"), "MAP_OBJECT_PRIORITY", "优先级", 15);
    score -= addQualityItem(items, sources.stream().anyMatch(s -> "MAP_OBJECT".equals(asString(s.get("source_type")))), "MAP_OBJECT_SOURCE", "地图对象来源", 10);
    if (score < 0) score = 0;
    boolean passed = score >= 80 && items.stream().noneMatch(i -> "ERROR".equals(i.get("level")));
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("passed", passed);
    result.put("score", score);
    result.put("level", score >= 90 ? "A" : score >= 80 ? "B" : score >= 60 ? "C" : "D");
    result.put("items", items);
    result.put("summary", "地图对象方案质量校验" + (passed ? "通过" : "未通过") + "，评分 " + score + "。");
    result.put("originType", "MAP_OBJECT");
    result.put("checkedAt", new Date());
    return result;
}
```

- [ ] **Step 3: Run backend compile**

Run:

```bash
/tmp/codex-maven/apache-maven-3.9.6/bin/mvn -pl srmp-agent -am package -DskipTests
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit Task 4**

```bash
git add srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/impl/AiSolutionGenerateServiceImpl.java srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/impl/AiSolutionQualityServiceImpl.java
git commit -m "feat: support map object task quality"
```

---

### Task 5: Add Frontend APIs And Save From Preview

**Files:**
- Modify: `srmp-web-ui/src/api/solution.ts`
- Modify: `srmp-web-ui/src/views/gis/components/SolutionPreviewDialog.vue`
- Modify: `srmp-web-ui/src/views/gis/components/AgentChatFloat.vue`

- [ ] **Step 1: Add solution draft APIs**

In `srmp-web-ui/src/api/solution.ts`, add:

```ts
export interface AiSolutionSourceSummaryRequest {
  sourceType?: string
  sourceTitle?: string
  sourceId?: string
  sourceUrl?: string
  contentExcerpt?: string
}

export interface AiSolutionDraftSaveRequest {
  solutionType: string
  title: string
  markdown: string
  routeCode?: string
  year?: number
  mapObject: Record<string, any>
  objectSummary?: Record<string, any>
  qualityCheck?: Record<string, any>
  sourceSummaries?: AiSolutionSourceSummaryRequest[]
  templateId?: string
  templateVersion?: string
  templateName?: string
  options?: Record<string, any>
  requestContext?: Record<string, any>
}

export interface AiSolutionDraftUpdateRequest {
  title?: string
  markdown: string
  changeNote?: string
}

export function saveMapObjectSolutionDraft(data: AiSolutionDraftSaveRequest): Promise<Record<string, any>> {
  return request.post('/api/ai/solution/tasks/map-object-drafts', data)
}

export function updateSolutionTask(id: string, data: AiSolutionDraftUpdateRequest): Promise<Record<string, any>> {
  return request.put(`/api/ai/solution/tasks/${id}`, data)
}

export function updateSolutionTaskDraftStatus(id: string, draftStatus: string): Promise<Record<string, any>> {
  return request.post(`/api/ai/solution/tasks/${id}/draft-status`, { draftStatus })
}

export function getSolutionTaskVersions(id: string): Promise<Record<string, any>[]> {
  return request.get(`/api/ai/solution/tasks/${id}/versions`)
}
```

Also add `draftStatus?: string` to `AiSolutionTaskQuery`.

- [ ] **Step 2: Enable save in preview dialog**

In `SolutionPreviewDialog.vue`:

```ts
const props = defineProps<{
  visible: boolean
  solution: MapObjectSolutionResponse | null
  saveLoading?: boolean
  savedTask?: Record<string, any> | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'save'): void
}>()
```

Replace the disabled save button:

```vue
<el-button
  type="primary"
  plain
  :loading="saveLoading"
  :disabled="!solution?.markdown"
  @click="emit('save')"
>
  保存草稿
</el-button>
```

Add a saved task hint above the markdown:

```vue
<el-alert
  v-if="savedTask?.id"
  class="saved-task"
  type="success"
  show-icon
  :closable="false"
  :title="`已保存：${savedTask.id} / ${savedTask.draft_status || 'DRAFT'}`"
/>
```

- [ ] **Step 3: Save from AgentChatFloat**

In `AgentChatFloat.vue`, import:

```ts
import { saveMapObjectSolutionDraft } from '../../../api/solution'
```

Add state:

```ts
const solutionSaveLoading = ref(false)
const savedSolutionTask = ref<Record<string, any> | null>(null)
```

Pass props/events:

```vue
<SolutionPreviewDialog
  v-model:visible="solutionDialogVisible"
  :solution="solutionResult"
  :save-loading="solutionSaveLoading"
  :saved-task="savedSolutionTask"
  @save="saveSolutionDraft"
/>
```

Reset after generating:

```ts
savedSolutionTask.value = null
```

Add save method:

```ts
async function saveSolutionDraft() {
  const solution = solutionResult.value
  const obj: any = activeMapObject.value
  if (!solution?.markdown || !obj) {
    ElMessage.warning('暂无可保存的方案草稿')
    return
  }
  solutionSaveLoading.value = true
  try {
    savedSolutionTask.value = await saveMapObjectSolutionDraft({
      solutionType: solution.solutionType,
      title: solution.title,
      markdown: solution.markdown,
      routeCode: String(obj.routeCode || obj.route_code || props.context?.query?.routeCode || ''),
      year: normalizeYear(obj.year || props.context?.query?.year),
      mapObject: obj,
      objectSummary: solution.objectSummary || {},
      qualityCheck: solution.qualityCheck || {},
      sourceSummaries: [{
        sourceType: 'MAP_OBJECT',
        sourceTitle: mapContextLabel.value,
        sourceId: String(obj.objectId || obj.object_id || obj.id || ''),
        contentExcerpt: mapContextLabel.value
      }],
      options: Object.assign({}, options),
      requestContext: props.context || {}
    })
    ElMessage.success('方案草稿已保存')
  } finally {
    solutionSaveLoading.value = false
  }
}
```

- [ ] **Step 4: Run frontend build**

Run:

```bash
npm run build
```

Expected: build passes. Existing chunk-size warning is acceptable.

- [ ] **Step 5: Commit Task 5**

```bash
git add srmp-web-ui/src/api/solution.ts srmp-web-ui/src/views/gis/components/SolutionPreviewDialog.vue srmp-web-ui/src/views/gis/components/AgentChatFloat.vue
git commit -m "feat: save map object solution drafts"
```

---

### Task 6: Add Task Page Draft Status And Version History

**Files:**
- Modify: `srmp-web-ui/src/views/agent/SolutionTasksPage.vue`

- [ ] **Step 1: Add imports and state**

Extend imports:

```ts
import {
  checkSolutionQuality,
  getSolutionMarkdownExportUrl,
  getSolutionQualityResult,
  getSolutionTask,
  getSolutionTaskSources,
  getSolutionTaskVersions,
  listSolutionTasks,
  updateSolutionTaskDraftStatus
} from '../../api/solution'
```

Add state:

```ts
const versions = ref<Record<string, any>[]>([])
const versionDrawerVisible = ref(false)
const statusUpdating = ref(false)
```

- [ ] **Step 2: Display draft metadata**

In the task list item, show `draft_status` when present:

```vue
<el-tag v-if="item.draft_status" size="small" type="warning">{{ item.draft_status }}</el-tag>
<el-tag v-else size="small">{{ item.status }}</el-tag>
```

In descriptions, add:

```vue
<el-descriptions-item label="草稿状态">{{ detail.draft_status || '-' }}</el-descriptions-item>
<el-descriptions-item label="对象类型">{{ detail.object_type || '-' }}</el-descriptions-item>
<el-descriptions-item label="对象ID">{{ detail.object_id || '-' }}</el-descriptions-item>
<el-descriptions-item label="当前版本">{{ detail.current_version_no || '-' }}</el-descriptions-item>
```

- [ ] **Step 3: Add status actions and version drawer**

Add buttons near existing quality/export buttons:

```vue
<el-button v-if="detail?.id" size="small" @click="openVersions">版本历史</el-button>
<el-button v-if="detail?.draft_status === 'DRAFT'" size="small" :loading="statusUpdating" @click="changeDraftStatus('CONFIRMED')">确认</el-button>
<el-button v-if="detail?.draft_status === 'DRAFT' || detail?.draft_status === 'CONFIRMED'" size="small" :loading="statusUpdating" @click="changeDraftStatus('ARCHIVED')">归档</el-button>
```

Add drawer:

```vue
<el-drawer v-model="versionDrawerVisible" title="版本历史" size="520px">
  <el-empty v-if="versions.length === 0" description="暂无版本" />
  <div v-for="item in versions" :key="item.id" class="version-item">
    <strong>v{{ item.version_no }} {{ item.title }}</strong>
    <p>{{ item.change_note || '版本快照' }}</p>
    <div class="meta">{{ item.created_at }}</div>
  </div>
</el-drawer>
```

Add methods:

```ts
async function openVersions() {
  if (!detail.value?.id) return
  versions.value = await getSolutionTaskVersions(detail.value.id)
  versionDrawerVisible.value = true
}

async function changeDraftStatus(next: string) {
  if (!detail.value?.id) return
  statusUpdating.value = true
  try {
    detail.value = await updateSolutionTaskDraftStatus(detail.value.id, next)
    await loadTasks()
    ElMessage.success('草稿状态已更新')
  } finally {
    statusUpdating.value = false
  }
}
```

- [ ] **Step 4: Add version item styles**

```css
.version-item {
  padding: 12px;
  margin-bottom: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}
```

- [ ] **Step 5: Run frontend build**

Run:

```bash
npm run build
```

Expected: build passes. Existing chunk-size warning is acceptable.

- [ ] **Step 6: Commit Task 6**

```bash
git add srmp-web-ui/src/views/agent/SolutionTasksPage.vue
git commit -m "feat: show solution draft versions"
```

---

### Task 7: Final Verification

**Files:**
- Verify all changed Phase 33 files.

- [ ] **Step 1: Run source acceptance check**

```bash
bash scripts/check-phase33-solution-draft-versioning.sh
```

Expected:

```text
[OK] phase33 solution draft versioning hooks exist
```

- [ ] **Step 2: Run backend compile**

```bash
/tmp/codex-maven/apache-maven-3.9.6/bin/mvn -pl srmp-agent,srmp-gis -am package -DskipTests
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Run frontend build**

```bash
npm run build
```

Expected: build passes. Existing Vite large-chunk warning is acceptable.

- [ ] **Step 4: Run whitespace check**

```bash
git diff --check
```

Expected: no output.

- [ ] **Step 5: Verify local config changes are not staged**

```bash
git status --short
```

Expected: only intentional Phase 33 changes are staged/committed. These user-kept local files remain uncommitted and should not be staged:

```text
.claude/settings.local.json
srmp-admin/src/main/resources/application-demo.yml
srmp-admin/src/main/resources/application-dev.yml
```

- [ ] **Step 6: Push and PR**

```bash
git push -u origin codex/phase33-solution-draft-versioning
```

Open a PR with summary:

```text
Phase 33 persists map-object solution drafts into the existing AI solution task stack, adds task version history, separates draft_status from execution status, and supports task page draft lifecycle controls.
```

---

## Self-Review

- Spec coverage: migration, save API, version table, draft status, status/edit rules, quality normalization, source summaries, frontend save, versions UI, and verification are covered.
- Specificity scan: the plan avoids deferred wording and generic edge-case instructions.
- Type consistency: API path is `/api/ai/solution/tasks/map-object-drafts`; lifecycle field is `draft_status` in SQL and `draftStatus` in DTO/API query; execution status remains `status`.
