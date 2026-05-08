# SRMP LangGraph Orchestrator

Phase50.2 新增的独立 AI 编排服务。它不直接访问 SRMP 业务库，只通过 Java 主工程暴露的 Tool Gateway 调用现有业务工具。

## 启动

```bash
cd srmp-ai-orchestrator
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
export SRMP_JAVA_BASE_URL=http://127.0.0.1:8080
uvicorn app.main:app --host 0.0.0.0 --port 18080 --reload
```

## Docker 启动

```bash
docker compose -f docker-compose.langgraph.yml up -d --build srmp-ai-orchestrator
```

## 健康检查

```bash
curl http://127.0.0.1:18080/health
```

## 调用示例

```bash
curl -X POST http://127.0.0.1:18080/api/srmp/langgraph/map-agent/run \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: default' \
  -d '{
    "message":"G210 中度裂缝怎么处置？",
    "action":"CHAT",
    "mapContext":{"tenantId":"default","mode":"OBJECT","routeCode":"G210","year":2024,"mapObject":{"objectType":"DISEASE_RECORD","diseaseName":"横向裂缝","severity":"中度"}},
    "options":{"topK":8,"useKnowledge":true}
  }'
```

## 可选 LLM 配置

默认 `SRMP_LANGGRAPH_USE_LLM=false`，Runtime 不调用外部模型，只根据 Java Tool Gateway 的 GIS/知识库结果生成确定性兜底答案。这样一键启动和本地回归不依赖付费模型。

需要让 LangGraph Runtime 自己调用 OpenAI-compatible Chat Completion 时配置：

```bash
export SRMP_LANGGRAPH_USE_LLM=true
export SRMP_LLM_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
export SRMP_LLM_API_KEY=你的key
export SRMP_LLM_MODEL=qwen-plus
export SRMP_LANGGRAPH_LLM_CONNECT_TIMEOUT_SECONDS=10
export SRMP_LANGGRAPH_LLM_READ_TIMEOUT_SECONDS=180
export SRMP_LANGGRAPH_LLM_MAX_TOKENS=2048
export SRMP_LANGGRAPH_LLM_COMPACT_RETRY_ENABLED=true
```

验证 Python LangGraph LLM 链路：

```bash
curl -fsS 'http://localhost:8080/api/agent/orchestrator/ops/llm-probe?probe=true' | python3 -m json.tool
```

返回 `status=SUCCESS` 表示 Python Runtime 自己的 LLM 调用可用。返回 `SKIPPED` 表示未启用，返回 `FAILED` 时查看 `errorType`、`errorMessage`、`rawResponsePreview`、`probeCostMs`。

## Plan Debug

Phase50.7 新增只规划不执行接口，用来排查前端传入的 `context/mapObject/geometry` 是否被 Runtime 正确识别，以及会规划哪些工具：

```bash
curl -X POST http://127.0.0.1:18080/api/srmp/langgraph/debug/plan \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: default' \
  -d '{"message":"框选区域裂缝处置建议","context":{"mode":"POLYGON","geometry":{"type":"Polygon","coordinates":[]},"mapObject":{"routeCode":"G210","diseaseName":"裂缝","severity":"中度"}},"options":{"topK":2}}'
```

返回中重点看 `intent`、`contextSummary`、`toolPlan`、`steps`。

## 工具契约诊断

Phase50.8 新增工具契约诊断接口，用来比较 Runtime 白名单与 Java Tool Gateway 实际注册工具：

```bash
curl http://127.0.0.1:18080/api/srmp/langgraph/debug/contract \
  -H 'X-Tenant-Id: default'
```

重点看：

- `missingInJava`：Runtime 计划会调用，但 Java 未注册；
- `blockedByRuntimeWhitelist`：Java 已注册，但 Runtime 白名单未放行；
- `writeBlocked`：疑似写工具且当前只读模式屏蔽。

Java 侧也可以通过 `/api/agent/orchestrator/ops/contract` 和 `/api/agent/orchestrator/ops/plan` 代理访问，前端页面 `/agent/langgraph-ops` 已接入。

## Phase50.9 Runtime Replay / Export

本阶段在观测缓冲区中保存最近请求的 `requestPayload` 快照，用于联调时回放：

- `GET /api/srmp/langgraph/observability/record/{idOrTraceId}`：查看单条内存审计记录；
- `GET /api/srmp/langgraph/observability/export?limit=30`：导出 Runtime 诊断快照；
- `POST /api/srmp/langgraph/debug/replay/{idOrTraceId}?execute=false`：只做 Plan 回放，不调用 Java 工具；
- `POST /api/srmp/langgraph/debug/replay/{idOrTraceId}?execute=true`：重新执行完整只读链路。

默认 `execute=false`，适合排查“为什么规划了这个工具”。`execute=true` 用于修复 Tool Gateway、知识库、GIS 查询后验证同一个请求是否恢复。
