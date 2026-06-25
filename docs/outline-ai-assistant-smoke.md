# Outline 知识库到 AI 助手验收说明

本文用于验证 Outline 知识内容已经进入 SRMP AI 知识库，并能被一张图 AI 助手提问链路使用。

## 1. 初始化 Outline 知识库

在后端和 Outline 服务可访问时执行：

```bash
python3 scripts/init-outline-knowledge-base.py
```

常用参数：

```bash
python3 scripts/init-outline-knowledge-base.py \
  --base-url http://localhost:3000 \
  --token "$OUTLINE_API_TOKEN" \
  --collection-id "$OUTLINE_DEFAULT_COLLECTION_ID"
```

脚本会在 Outline 中维护以下目录：

- `00_知识库治理`
- `10_指标与评定标准`
- `20_病害识别与分级`
- `30_养护处置工艺`
- `40_方案模板蓝本`
- `50_项目资料`
- `60_案例库`
- `70_术语与问答`

其中模板蓝本和治理页不会进入 RAG；指标、病害、工艺、项目资料、案例库和 FAQ 会带 SRMP 元数据进入后续同步。

## 2. 一键验证同步与 AI 生效

推荐在目标环境部署/更新后执行：

```bash
bash scripts/check-outline-ai-assistant.sh
```

可选环境变量：

```bash
BASE_URL=http://localhost:8080 \
TENANT_ID=default \
OUTLINE_SYNC_LIMIT=50 \
OUTLINE_SYNC_FORCE=true \
MAP_AGENT_REQUIRED=true \
bash scripts/check-outline-ai-assistant.sh
```

脚本会依次验证：

1. `/api/outline/status`：Outline 连接可用；
2. `/api/outline/sync`：Outline 文档同步入库；
3. `/api/outline/knowledge-stats`：Outline 来源文档和切片已落库；
4. `/api/ai/knowledge/search`：可按 `sourceTypes=["OUTLINE"]` 命中知识；
5. `/api/agent/tools/execute`：`knowledge.retrieve` 工具可命中 Outline 来源；
6. `/api/agent/map-agent/run`：地图 AI 助手能返回知识工具或来源上下文。

默认情况下，如果 LangGraph Runtime 未启动，脚本会把地图 AI 助手入口标记为警告，但不会抹掉前面已完成的 Outline 知识检索和工具验证。目标环境要求完整闭环时，设置 `MAP_AGENT_REQUIRED=true`。

## 3. 推荐验收提问

```text
当前 PCI 偏低，裂缝和坑槽集中时应该如何处置？请说明知识来源上下文。
```

预期结果：

- 知识检索命中 `OUTLINE` 来源；
- 命中内容包含 PCI、裂缝、坑槽、灌缝、铣刨重铺、现场复核等养护关键词；
- 工具响应中包含 request summary，便于追踪 topK、filters 和 query；
- AI 助手响应中能看到 `knowledge.retrieve` 工具结果或 `OUTLINE` sources。

## 4. 常见失败原因

- `outline-status usable=false`：后端未配置 `OUTLINE_ENABLED=true`、`OUTLINE_BASE_URL` 或 `OUTLINE_API_TOKEN`。
- `outline-knowledge-stats chunkCount=0`：Outline 文档未同步、文档缺少正文，或文档元数据 `ragEnabled=false`。
- `ai-knowledge-search` 无命中：同步未完成、tenant 不一致，或查询词与知识内容不匹配。
- `knowledge.retrieve` 无 OUTLINE 来源：检查请求 filters 是否包含 `sourceType=OUTLINE`，以及 `ai_knowledge_chunk.source_type` 是否为 `OUTLINE`。
- `map-agent-run` 提示 LangGraph Runtime 不可用：启动 LangGraph Runtime，或确认后端 `srmp.ai.orchestrator.langgraph-url` 指向正确地址。
