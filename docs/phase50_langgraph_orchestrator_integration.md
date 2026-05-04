# Phase50：LangGraph Orchestrator 外接集成方案

## 目标

本阶段不直接在 Java 8 / Spring Boot 2.7 主工程内引入 LangChain4j、Spring AI 或 LangGraph4j，而是新增一层稳定的 Agent Orchestrator 抽象：

- 默认 `native`，继续走现有 Java 原生一张图 AI 链路；
- 可配置 `langgraph`，通过 HTTP 调用独立 LangGraph 编排服务；
- LangGraph 编排失败时默认自动回退 native；
- 外部编排服务只通过 Tool Gateway 调用 Java 业务工具，不直接连接业务库。

## 新增配置

```yaml
srmp:
  ai:
    orchestrator:
      provider: native # native / langgraph
      langgraph-url: http://127.0.0.1:18080
      langgraph-endpoint-path: /api/srmp/langgraph/map-agent/chat
      connect-timeout-ms: 10000
      read-timeout-ms: 300000
      fallback-to-native: true
      allow-write-tools: false # Tool Gateway 是否允许写工具，默认关闭
```

切换为 LangGraph：

```bash
export SRMP_AI_ORCHESTRATOR_PROVIDER=langgraph
export SRMP_LANGGRAPH_URL=http://127.0.0.1:18080
```

## Java 主工程新增能力

### 1. 编排器抽象

- `AgentOrchestrator`
- `AgentOrchestratorRouter`
- `AgentOrchestratorProperties`
- `NativeMapAgentOrchestrator`
- `RemoteLangGraphOrchestrator`

`MapAiAgentServiceImpl` 现在只作为门面，统一委托给 `AgentOrchestratorRouter`。

### 2. Tool Gateway

新增接口：

```http
GET /api/agent/tools
POST /api/agent/tools/execute
```

工具执行请求示例：

```json
{
  "toolName": "knowledge.retrieve",
  "traceId": "ai-demo-001",
  "tenantId": "default",
  "userQuestion": "中度裂缝怎么处置？",
  "mapContext": {
    "tenantId": "default",
    "mode": "OBJECT",
    "routeCode": "G210",
    "year": 2024,
    "mapObject": {
      "objectType": "DISEASE_RECORD",
      "diseaseName": "横向裂缝",
      "severity": "中度"
    }
  },
  "options": {
    "topK": 8,
    "useKnowledge": true
  },
  "args": {
    "query": "G210 中度 横向裂缝 处置建议"
  }
}
```

## LangGraph 服务约定

Java 远程客户端默认调用：

```http
POST /api/srmp/langgraph/map-agent/chat
```

请求体直接沿用 `MapAiAgentRequest`。

返回体支持两种格式。

### 直接返回 MapAiAgentResponse

```json
{
  "answer": "...",
  "mode": "LANGGRAPH_AGENT",
  "intent": "OBJECT_ANALYSIS",
  "mapContext": {},
  "toolResults": [],
  "knowledgeSources": [],
  "sources": [],
  "trace": {},
  "data": {}
}
```

### 返回统一 R 包装

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "answer": "...",
    "mode": "LANGGRAPH_AGENT"
  }
}
```

## 推荐 LangGraph 节点

第一版建议只做只读分析链路：

1. `context_build`：标准化地图上下文；
2. `intent_recognize`：识别 OBJECT_ANALYSIS / REGION_ANALYSIS / KNOWLEDGE_QA；
3. `tool_plan`：规划要调用的 Java 工具；
4. `tool_execute`：调用 `/api/agent/tools/execute`；
5. `knowledge_retrieve`：调用 `knowledge.retrieve`；
6. `answer_generate`：组织 Prompt 并调用模型；
7. `quality_guard`：过滤空答案、think 标签、过度泛化回答；
8. `finalize`：返回兼容 `MapAiAgentResponse` 的结果。

## 安全边界

- 外部 LangGraph 服务不直接访问业务数据库；
- 第一版默认只开放只读工具，`allow-write-tools=false` 时会拦截 save/create/update/delete/archive 等写工具；
- 方案保存、工单创建、状态变更类工具必须加人工确认；
- Java 侧仍保留租户上下文、工具注册、业务 DTO 和 Trace 兼容字段。

## 验证命令

### 查看工具列表

```bash
curl http://localhost:8080/api/agent/tools
```

### 执行知识检索工具

```bash
curl -X POST http://localhost:8080/api/agent/tools/execute \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: default' \
  -d '{
    "toolName":"knowledge.retrieve",
    "tenantId":"default",
    "userQuestion":"中度裂缝怎么处置",
    "options":{"topK":8},
    "args":{"query":"中度裂缝 处置建议"}
  }'
```

### 默认 native 链路

```bash
curl -X POST http://localhost:8080/api/agent/map-agent/chat \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: default' \
  -d '{"message":"G210 中度裂缝怎么处理？","options":{"useKnowledge":true}}'
```

### 切换 LangGraph 远程链路

```bash
export SRMP_AI_ORCHESTRATOR_PROVIDER=langgraph
export SRMP_LANGGRAPH_URL=http://127.0.0.1:18080
```

若远程服务不可用，默认会回退 native，响应 `data.orchestratorFallback=true`。
