# Phase50.7 LangGraph 上下文归一化、Sources 回填与 Plan Debug

## 目标

在 Phase50.5/50.6 已经完成 Runtime、Tool Gateway、观测页和只读策略的基础上，本阶段继续完善 `srmp-ai-orchestrator` 的联调稳定性：

1. 兼容前端旧字段：`context`、顶层 `mapObject`、`mapContext.geometry` 都能被 Runtime 正确识别；
2. 保留框选/多边形 GeoJSON，避免 Java 转发时区域上下文丢失；
3. 补齐 `sources/knowledgeSources`，让前端参考资料不再为空；
4. 增加 `debug/plan`，不执行工具也能看到意图识别和工具规划；
5. 强化 Java Tool Gateway 失败诊断，对 `R.fail`、404、502、超时返回可读错误。

## 本阶段改动

### Python Runtime

核心文件：

```text
srmp-ai-orchestrator/app/schemas.py
srmp-ai-orchestrator/app/workflow.py
srmp-ai-orchestrator/app/java_tools.py
srmp-ai-orchestrator/app/main.py
srmp-ai-orchestrator/app/prompt.py
srmp-ai-orchestrator/app/observability.py
srmp-ai-orchestrator/app/config.py
```

新增/增强能力：

- 新增 `request_normalize` 节点，节点流变为：

```text
request_normalize -> context_build -> intent_recognize -> context_enrich -> tool_plan -> tool_execute -> evidence_fuse -> answer_generate -> quality_guard
```

- `MapAiContext` 新增 `geometry` 字段，并允许保留额外字段；
- `MapAiAgentRequest` 兼容：
  - `mapContext` 标准字段；
  - `context.mapContext`；
  - `context.mapObject`；
  - 顶层 `mapObject`；
  - `context.geometry`；
- `ToolResult` 增加 `reason/error` 兼容字段，修复开启 LLM 时 `item.reason` 可能不存在的问题；
- `/ready` 对 Java Tool Gateway 返回失败时明确置为 `DOWN`；
- `knowledge.retrieve` 的命中结果会规范化回填到顶层 `sources` 与 `knowledgeSources`；
- Runtime 观测摘要增加最近失败工具统计 `recentFailedTools`。

### 新增接口

```http
POST /api/srmp/langgraph/debug/plan
```

只执行归一化、上下文构建、意图识别和工具规划，不调用 Java Tool Gateway。适合排查：

- 为什么点击病害对象会调哪些工具；
- 为什么框选区域识别成 `REGION_ANALYSIS`；
- 前端传入的 `context/mapObject/geometry` 是否被 Runtime 吃到了。

示例：

```bash
curl -s -X POST http://127.0.0.1:18080/api/srmp/langgraph/debug/plan \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: default' \
  -H 'X-AI-Trace-Id: phase50-7-plan' \
  -d '{
    "message":"框选区域裂缝怎么处置？",
    "context":{
      "mode":"POLYGON",
      "geometry":{"type":"Polygon","coordinates":[]},
      "mapObject":{"routeCode":"G210","diseaseName":"裂缝","severity":"中度"}
    },
    "options":{"topK":3}
  }' | jq .
```

预期能看到：

```json
{
  "intent": "REGION_ANALYSIS",
  "contextSummary": {
    "mode": "POLYGON",
    "routeCode": "G210",
    "geometryType": "Polygon"
  },
  "toolPlan": [
    {"toolName": "gis.queryRegionSummary"},
    {"toolName": "gis.queryDiseases"},
    {"toolName": "gis.queryAssessmentResults"},
    {"toolName": "knowledge.retrieve"}
  ]
}
```

### Java DTO

```text
srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/dto/MapAiContext.java
srmp-web-ui/src/views/agent/LangGraphOpsPage.vue
```

新增：

```java
private Map<String, Object> geometry;
```

原因：如果前端通过 Java `/api/agent/map-agent/chat` 再转发到 LangGraph，Jackson 会按 Java DTO 序列化；没有该字段时，框选 GeoJSON 会在 Java -> Python 之间丢失。

### 前端观测页

```text
srmp-web-ui/src/views/agent/LangGraphOpsPage.vue
```

增强点：

- Tool Gateway 状态不再只看 Runtime debug 接口 HTTP 200，还会继续检查 `body.tools.ok`；
- 链路诊断展示 Runtime 策略版本；
- 链路诊断展示近期失败工具统计，便于快速定位是 `knowledge.retrieve`、GIS 查询还是 Tool Gateway 本身失败。

## 验证

### 1. Python 语法检查

```bash
python3 -m py_compile srmp-ai-orchestrator/app/*.py
```

### 2. Plan Debug

```bash
curl -s -X POST http://127.0.0.1:18080/api/srmp/langgraph/debug/plan \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: default' \
  -d '{"message":"框选区域裂缝处置建议","context":{"mode":"POLYGON","geometry":{"type":"Polygon","coordinates":[]},"mapObject":{"routeCode":"G210","diseaseName":"裂缝","severity":"中度"}},"options":{"topK":2}}'
```

### 3. Runtime Ready

```bash
curl -s http://127.0.0.1:18080/ready | jq .
```

当 Java Tool Gateway 不通时，`status` 会是 `DOWN`，并在 `toolGateway.raw.error` 中展示 HTTP 状态或异常信息。

### 4. 正常聊天

```bash
curl -s -X POST http://127.0.0.1:18080/api/srmp/langgraph/map-agent/chat \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: default' \
  -d '{"message":"G210 中度裂缝怎么处置？","mapContext":{"tenantId":"default","mode":"OBJECT","routeCode":"G210","year":2026,"mapObject":{"objectType":"DISEASE_RECORD","diseaseName":"裂缝","severity":"中度","routeCode":"G210"}},"options":{"topK":3}}' | jq .
```

重点检查：

- `data.strategyVersion = phase50.7-context-source-v1`；
- `data.mapObjectUsed = true`；
- `trace.steps` 包含 `request_normalize`；
- 如果知识库命中，顶层 `sources/knowledgeSources` 非空；
- `data.toolTotalCount/toolSuccessCount/toolFailedCount` 有值。

## 回滚

本阶段只增加兼容字段和 Runtime 逻辑，不涉及数据库结构。回滚时恢复上述文件即可；Java 新增的 `geometry` DTO 字段对旧接口无破坏。
