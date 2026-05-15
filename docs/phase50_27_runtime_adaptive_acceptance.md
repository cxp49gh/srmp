# Phase50.27 运行时自适应验收

Phase50.27 不新增运行时能力，而是把 Phase50.26 的真实链路验收固化为 live acceptance gate。它用于确认 Java backend、LangGraph Runtime、Java Tool Gateway 和前端生产产物之间的自适应 compare 契约仍然可用。

## 前提

服务必须已经启动，并且运行当前代码：

- Java backend: `http://127.0.0.1:8080`
- LangGraph Runtime: `http://127.0.0.1:18080`
- Frontend: `http://127.0.0.1:5173`

脚本不会启动、停止、重建容器，也不会重置数据库。需要刷新本地容器时，可沿用当前 compose 栈：

```bash
docker compose -f docker-compose.yml -f docker-compose.app.yml -f docker-compose.langgraph.yml -f docker-compose.dev.yml up -d --build backend frontend srmp-ai-orchestrator
```

## 命令

```bash
bash scripts/check-phase50-27-runtime-adaptive-acceptance.sh
```

可覆盖地址和租户：

```bash
JAVA_URL=http://127.0.0.1:8080 \
RUNTIME_URL=http://127.0.0.1:18080 \
FRONTEND_URL=http://127.0.0.1:5173 \
TENANT_ID=default \
bash scripts/check-phase50-27-runtime-adaptive-acceptance.sh
```

## 验收内容

- Runtime health 为 `UP`，且策略节点包含 `adaptive_tool_planning`。
- Java orchestrator health 为 `UP`，且能访问 Runtime。
- Runtime 和 Java ops config 都暴露 `maxAdaptiveAddedTools=1`。
- Java `map-agent/run` 能产生 `runtimeAuditId`。
- 请求保持 `ANALYZE_REGION` / `REGION_ANALYSIS` 契约。
- 该请求触发自适应追加 `knowledge.retrieve`，并执行至少两个工具。
- Java ops replay `adaptiveMode=compare` 返回 baseline/adaptive/compare。
- compare 中 baseline 为 `DISABLED`，adaptive 为 `EXECUTED`，且 `toolDelta >= 1`。
- adaptive replay 的工具总数大于 baseline replay。
- 前端产物包含“自适应对比”和 `adaptiveMode`。

`evidenceImproved=false` 不是失败条件。本地知识库 0 命中时，compare 结构和工具差值仍然能证明自适应链路生效。

## 诊断文件

脚本会把响应写到 `/tmp/srmp-phase50-27-*`。失败时优先查看对应 JSON、HTML 或 JS 文件：

- `/tmp/srmp-phase50-27-runtime-health.json`
- `/tmp/srmp-phase50-27-java-health.json`
- `/tmp/srmp-phase50-27-run-response.json`
- `/tmp/srmp-phase50-27-compare-response.json`
- `/tmp/srmp-phase50-27-frontend-index.html`
- `/tmp/srmp-phase50-27-frontend-index.js`

## 常见失败

- 运行中容器未更新到当前代码：重建并重启 `backend`、`srmp-ai-orchestrator`、`frontend`。
- Runtime config 没有 `maxAdaptiveAddedTools`：Runtime 镜像不是 Phase50.26 之后的代码。
- Java `map-agent/run` 返回 Runtime DTO 反序列化错误：检查 `MapAgentRunResponse` 是否接收 `planExecution`。
- compare 缺少 baseline/adaptive：确认 `adaptiveMode=compare` 通过 Java ops replay 代理到 Runtime。
- 前端 JS 不包含“自适应对比”：前端容器仍是旧产物，需重建 `frontend`。
