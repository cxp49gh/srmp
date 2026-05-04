# Phase50.5 LangGraph 编排观测与灰度控制台

## 目标

Phase50.1~50.4 已经完成 Java 编排路由、Python LangGraph Runtime、Tool Gateway 和 E2E 检查。本阶段补齐可观测闭环，避免联调时只能靠 curl 和容器日志定位问题。

本阶段不改变默认业务行为，不开放写工具，不改数据库结构。

## 新增能力

### Java 后端

新增控制器：

```text
srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/controller/AgentOrchestratorOpsController.java
```

新增接口：

```http
GET  /api/agent/orchestrator/ops/summary
GET  /api/agent/orchestrator/ops/recent?limit=20&status=SUCCESS|FAILED
POST /api/agent/orchestrator/ops/smoke
```

`summary` 会聚合：

- Java 当前 provider；
- fallbackToNative / allowWriteTools；
- 可用编排器 provider；
- Java 本地工具数量；
- LangGraph `/ready`；
- LangGraph Runtime 调用统计；
- 最近 Runtime 调用记录；
- Tool Gateway 诊断结果。

`smoke` 会通过 Java 入口走一次 `AgentOrchestratorRouter.chat`，用于确认：

```text
Java /api/agent/map-agent/chat
  -> RemoteLangGraphOrchestrator
  -> LangGraph Runtime
  -> Java Tool Gateway
  -> AiToolRegistry
```

### Python LangGraph Runtime

新增文件：

```text
srmp-ai-orchestrator/app/observability.py
```

新增接口：

```http
GET    /api/srmp/langgraph/observability/summary
GET    /api/srmp/langgraph/observability/recent?limit=20&status=SUCCESS|FAILED
DELETE /api/srmp/langgraph/observability/recent
```

运行时只维护最多 200 条内存记录。容器重启后自动清空，不替代 Java 侧持久化 Trace。

### 前端

新增页面：

```text
/agent/langgraph-ops
```

新增文件：

```text
srmp-web-ui/src/api/orchestrator.ts
srmp-web-ui/src/views/agent/LangGraphOpsPage.vue
```

页面能力：

- 查看当前 provider；
- 查看 LangGraph Ready 状态；
- 查看 Runtime 成功/失败/平均耗时；
- 查看 Tool Gateway 诊断；
- 执行一键 smoke；
- 查看最近 Runtime 调用记录；
- 复制诊断结果。

## 应用

```bash
bash phase50_5_langgraph_observability_package/apply-phase50-5-langgraph-observability.sh /path/to/srmp
```

重启 Java 后端和 LangGraph Runtime。

Java 环境变量示例：

```bash
export SRMP_AI_ORCHESTRATOR_PROVIDER=langgraph
export SRMP_LANGGRAPH_URL=http://127.0.0.1:18080
```

LangGraph Runtime 环境变量示例：

```bash
export SRMP_JAVA_BASE_URL=http://127.0.0.1:8080
```

## 验证

```bash
scripts/check-phase50-5-langgraph-observability.sh
```

手动验证：

```bash
curl -s http://127.0.0.1:18080/api/srmp/langgraph/observability/summary
curl -s http://127.0.0.1:8080/api/agent/orchestrator/ops/summary
curl -s -X POST http://127.0.0.1:8080/api/agent/orchestrator/ops/smoke \
  -H 'Content-Type: application/json' \
  -d '{"tenantId":"default","routeCode":"G210","year":2026,"message":"请给出 G210 养护建议摘要"}'
```

前端访问：

```text
http://localhost:5173/agent/langgraph-ops
```

## 注意

1. Runtime 观测记录是内存数据，不做长期审计；长期审计继续看 Java `ai_trace_log` / `ai_trace_step`。
2. 本阶段 smoke 是只读请求，不会保存方案或创建任务。
3. 如果 Java provider 仍是 `native`，页面可以看到 Runtime 状态，但主聊天入口不会走 LangGraph。
