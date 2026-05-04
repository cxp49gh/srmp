# Phase50.2：LangGraph 编排运行时服务

## 目标

Phase50.1 已经把 Java 主工程改造成 `native / langgraph` 可切换的 Agent Orchestrator，并新增了 Java Tool Gateway。本阶段补齐外部运行时：新增 `srmp-ai-orchestrator` 独立服务，作为 LangGraph 编排服务的第一版可运行骨架。

本阶段坚持三个边界：

1. 不升级 Java 主工程，不引入 Java 17 / Spring Boot 3 依赖；
2. LangGraph 服务不直连 SRMP 业务库，只通过 Java Tool Gateway 调用工具；
3. 默认只读工具，写操作继续走前端人工确认。

## 新增目录

```text
srmp-ai-orchestrator/
├── Dockerfile
├── README.md
├── requirements.txt
└── app/
    ├── main.py             # FastAPI 入口
    ├── workflow.py         # LangGraph 状态图与顺序降级执行器
    ├── java_tools.py       # Java Tool Gateway Client
    ├── llm_client.py       # 可选 OpenAI-compatible LLM Client
    ├── prompt.py           # 回答生成 Prompt 与保底答案
    ├── schemas.py          # 与 MapAiAgentRequest/Response 兼容的 DTO
    └── config.py           # 环境变量配置
```

新增根目录文件：

```text
docker-compose.langgraph.yml
scripts/run-langgraph-orchestrator-dev.sh
scripts/check-phase50-2-langgraph-runtime.sh
docs/phase50_2_langgraph_runtime_service.md
```

## 服务接口

### 健康检查

```http
GET /health
```

返回示例：

```json
{
  "status": "UP",
  "app": "srmp-langgraph-orchestrator",
  "langgraphAvailable": true,
  "javaBaseUrl": "http://127.0.0.1:8080",
  "allowWriteTools": false,
  "useLlm": false
}
```

### 查看 Java 工具

```http
GET /api/srmp/langgraph/tools
```

内部转发到 Java 主工程：

```http
GET /api/agent/tools
```

### 一张图 Agent 编排

```http
POST /api/srmp/langgraph/map-agent/chat
```

请求体兼容 Java 的 `MapAiAgentRequest`：

```json
{
  "message": "G210 中度裂缝怎么处置？",
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
  }
}
```

返回体兼容 Java 的 `MapAiAgentResponse`：

```json
{
  "answer": "...",
  "mode": "LANGGRAPH_AGENT",
  "intent": "OBJECT_ANALYSIS",
  "mapContext": {},
  "toolResults": [],
  "knowledgeSources": [],
  "sources": [],
  "trace": {
    "traceId": "...",
    "engine": "langgraph",
    "steps": []
  },
  "data": {
    "orchestrator": "langgraph",
    "langgraphAvailable": true,
    "mapObjectUsed": true,
    "readOnly": true
  }
}
```

## 编排节点

当前图节点：

```text
context_build
  ↓
intent_recognize
  ↓
tool_plan
  ↓
tool_execute
  ↓
answer_generate
  ↓
quality_guard
```

其中 `tool_execute` 只调用 Java Tool Gateway，不访问数据库。

## 工具规划策略

### OBJECT_ANALYSIS

常见于点击病害对象、地图对象后提问。

默认工具：

```text
gis.queryDiseases
gis.queryNearbyObjects
knowledge.retrieve
```

### ASSESSMENT_ANALYSIS

常见于点击评定结果、MQI/PQI/PCI 指标后提问。

默认工具：

```text
gis.queryAssessmentResults
gis.queryDiseasesByStakeRange
knowledge.retrieve
```

### REGION_ANALYSIS

常见于框选区域、多边形范围、区域统计类问题。

默认工具：

```text
gis.queryRegionSummary
gis.queryDiseases
knowledge.retrieve
```

### KNOWLEDGE_QA

没有明确地图对象时，默认只查知识库：

```text
knowledge.retrieve
```

## 环境变量

### 基础配置

```bash
export SRMP_JAVA_BASE_URL=http://127.0.0.1:8080
export SRMP_LANGGRAPH_ALLOW_WRITE_TOOLS=false
export SRMP_LANGGRAPH_ALLOWED_TOOLS=knowledge.retrieve,gis.queryDiseases,gis.queryAssessmentResults,gis.queryDiseasesByStakeRange,gis.queryRegionSummary,gis.queryNearbyObjects,template.match
```

### 可选 LLM 配置

默认 `SRMP_LANGGRAPH_USE_LLM=false`，服务会根据工具结果生成保底答案，便于先验证编排和工具调用。

如果希望 LangGraph 服务自己调用 OpenAI-compatible Chat Completion：

```bash
export SRMP_LANGGRAPH_USE_LLM=true
export SRMP_LLM_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
export SRMP_LLM_API_KEY=你的key
export SRMP_LLM_MODEL=qwen-plus
```

建议后续再补一个 `llm.chat` Java Tool，让外部编排服务统一通过 Java 调 LLM，避免两边重复维护模型配置。

## 本地启动

```bash
./scripts/run-langgraph-orchestrator-dev.sh
```

或手动启动：

```bash
cd srmp-ai-orchestrator
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
export SRMP_JAVA_BASE_URL=http://127.0.0.1:8080
uvicorn app.main:app --host 0.0.0.0 --port 18080 --reload
```

## Docker 启动

单独启动编排服务：

```bash
docker compose -f docker-compose.langgraph.yml up -d --build srmp-ai-orchestrator
```

如果和 `docker-compose.app.yml` 一起使用，建议后端服务增加环境变量：

```yaml
environment:
  SRMP_AI_ORCHESTRATOR_PROVIDER: langgraph
  SRMP_LANGGRAPH_URL: http://srmp-ai-orchestrator:18080
```

## 验证

```bash
./scripts/check-phase50-2-langgraph-runtime.sh
```

该脚本检查三件事：

1. `/health` 可用；
2. `/api/srmp/langgraph/map-agent/chat` 可返回结果；
3. 响应中包含 `mode=LANGGRAPH_AGENT`、`answer`、`trace.steps`。

## 与 Phase50.1 的关系

- Phase50.1：Java 主工程新增 Orchestrator Router、RemoteLangGraphOrchestrator 和 Tool Gateway；
- Phase50.2：新增真正可运行的外部 LangGraph 服务。

完整联调流程：

```bash
# 1. 启动 Java 后端
./scripts/srmp-one-click-start.sh --skip-build

# 2. 启动 LangGraph 服务
./scripts/run-langgraph-orchestrator-dev.sh

# 3. 后端切换远程编排
export SRMP_AI_ORCHESTRATOR_PROVIDER=langgraph
export SRMP_LANGGRAPH_URL=http://127.0.0.1:18080

# 4. 调原有前端/后端接口
curl -X POST http://localhost:8080/api/agent/map-agent/chat \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: default' \
  -d '{"message":"G210 中度裂缝怎么处置？","options":{"useKnowledge":true}}'
```

## 下一阶段建议

Phase50.3 建议做两件事：

1. 新增 Java 侧 `llm.chat` 只读工具，让 LangGraph 统一通过 Java 调模型；
2. 新增前端 AI Trace 视图对 LangGraph `trace.steps` 的可视化展示，把每个节点耗时、工具调用结果和知识库命中展示出来。
