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
curl -X POST http://127.0.0.1:18080/api/srmp/langgraph/map-agent/chat \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: default' \
  -d '{
    "message":"G210 中度裂缝怎么处置？",
    "mapContext":{"tenantId":"default","mode":"OBJECT","routeCode":"G210","year":2024,"mapObject":{"objectType":"DISEASE_RECORD","diseaseName":"横向裂缝","severity":"中度"}},
    "options":{"topK":8,"useKnowledge":true}
  }'
```

## 可选 LLM 配置

默认不直接调用外部模型，只根据 Java 工具结果生成保底答案。需要让 LangGraph 服务自己调用 OpenAI-compatible Chat Completion 时配置：

```bash
export SRMP_LANGGRAPH_USE_LLM=true
export SRMP_LLM_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
export SRMP_LLM_API_KEY=你的key
export SRMP_LLM_MODEL=qwen-plus
```

建议生产环境仍优先复用 Java 主工程已有 LLM 配置，后续再把 LLM 调用也包装成 Java Tool Gateway。
