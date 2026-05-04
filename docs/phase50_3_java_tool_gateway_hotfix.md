# Phase50.3：Java Tool Gateway 热修复

## 背景

当前 LangGraph orchestrator 已经在 `18080` 端口运行，但调用 Java 后端工具时返回 502。根因是 Java 主工程 `srmp-admin:8080` 下缺少：

```http
GET  /api/agent/tools
POST /api/agent/tools/execute
```

本阶段补齐 Java Tool Gateway，使外部 LangGraph Runtime 可以通过 HTTP 调用 Java 内部已有 `AiToolRegistry`，并继续复用现有多租户、业务工具和数据库访问能力。

## 设计原则

1. 不依赖 Phase50.1 的 `AgentOrchestratorProperties`，避免上一包未完整应用时接口仍无法注册。
2. 不引入 LangChain4j、Spring AI、LangGraph4j 等新依赖。
3. 默认只开放只读工具，写工具默认拦截。
4. 外部 orchestrator 不直连业务库，只调用 Java Gateway。
5. 保持 Java 8 / Spring Boot 2.7 兼容。

## 新增接口

### 工具清单

```http
GET /api/agent/tools
```

返回当前已注册的只读工具，例如：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "name": "knowledge.retrieve",
      "description": "检索向量知识库，返回专业资料片段",
      "writeTool": false
    }
  ]
}
```

默认不会返回 `solution.saveTask` 等写工具。

### 工具执行

```http
POST /api/agent/tools/execute
```

请求示例：

```json
{
  "toolName": "knowledge.retrieve",
  "tenantId": "default",
  "traceId": "phase50-3-smoke",
  "userQuestion": "G210 中度裂缝怎么处理？",
  "mapContext": {
    "tenantId": "default",
    "mode": "ROUTE",
    "routeCode": "G210",
    "year": 2026
  },
  "options": {
    "useKnowledge": true,
    "topK": 5
  },
  "args": {
    "query": "G210 中度裂缝 处置建议",
    "topK": 5
  }
}
```

## 配置

默认禁止写工具：

```yaml
srmp:
  ai:
    tool-gateway:
      allow-write-tools: false
```

兼容 Phase50.1 的配置项：

```yaml
srmp:
  ai:
    orchestrator:
      allow-write-tools: false
```

当 `srmp.ai.tool-gateway.allow-write-tools` 存在时，以它为准。

## 验证

后端重启后执行：

```bash
curl -s http://127.0.0.1:8080/api/agent/tools | jq .
```

再执行工具调用：

```bash
curl -s -X POST http://127.0.0.1:8080/api/agent/tools/execute \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: default' \
  -d '{
    "toolName":"gis.queryRegionSummary",
    "tenantId":"default",
    "traceId":"phase50-3-smoke",
    "userQuestion":"统计 G210 路况",
    "mapContext":{"tenantId":"default","mode":"ROUTE","routeCode":"G210","year":2026},
    "args":{}
  }' | jq .
```

如果返回 `code=0` 且 `data.toolName=gis.queryRegionSummary`，说明 Java Tool Gateway 已经注册成功。

之后 LangGraph orchestrator 的 502 应消失。
