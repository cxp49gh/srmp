# Phase50.4：LangGraph E2E 联调闭环

## 背景

Phase50.2 已经启动独立 LangGraph Runtime；Phase50.3 已补齐 Java Tool Gateway：

- `GET /api/agent/tools`
- `POST /api/agent/tools/execute`

本阶段继续收口完整链路：

```text
前端 /api/agent/map-agent/chat
  -> Java AgentOrchestratorRouter
  -> RemoteLangGraphOrchestrator
  -> Python LangGraph Runtime
  -> Java Tool Gateway
  -> Java AiToolRegistry
```

## 新增能力

### 1. Java 远程编排客户端增强

`RemoteLangGraphOrchestrator` 增强点：

- 调用 LangGraph 时透传 `X-Tenant-Id`；
- 生成并透传 `X-AI-Trace-Id`；
- 支持直接响应与 R 包装响应；
- 远程返回空答案时抛异常，交给 Router fallback native；
- 标准化响应字段：
  - `data.orchestratorProvider=langgraph`
  - `data.orchestratorFallback=false`
  - `data.remoteLangGraphUrl`
  - `data.remoteCostMs`
  - `trace.orchestratorProvider=langgraph`

### 2. Java 编排健康检查

新增：

```http
GET /api/agent/orchestrator/health
```

返回内容包括：

- 当前 provider；
- 可用 provider 列表；
- 本地工具数量；
- LangGraph `/health` 探测结果；
- LangGraph `/ready` 探测结果。

### 3. LangGraph Runtime 就绪检查

新增：

```http
GET /ready
GET /api/srmp/langgraph/debug/tool-gateway
GET /api/srmp/langgraph/debug/tool-gateway?smoke=true
```

`/ready` 会实际访问 Java `/api/agent/tools`。如果 Tool Gateway 未注册、Java 地址错误、网络不通，会返回 `status=DOWN`。

### 4. 一键 E2E 检查脚本

新增：

```bash
scripts/check-phase50-4-langgraph-e2e.sh
```

检查内容：

1. Java Tool Gateway 是否注册；
2. LangGraph Runtime 是否存活；
3. LangGraph Runtime 是否能访问 Java Tool Gateway；
4. 直接调用 LangGraph chat 是否返回 `LANGGRAPH_AGENT`；
5. Java 编排健康接口是否可访问；
6. Java `/api/agent/map-agent/chat` 是否能返回答案；
7. 可选强校验 Java chat 是否已走 LangGraph。

强制校验 Java chat 必须走 LangGraph：

```bash
REQUIRE_LANGGRAPH=true scripts/check-phase50-4-langgraph-e2e.sh
```

## 启动顺序

### 1. 重启 Java 后端

```bash
export SRMP_AI_ORCHESTRATOR_PROVIDER=langgraph
export SRMP_LANGGRAPH_URL=http://127.0.0.1:18080
mvn -pl srmp-admin -am -DskipTests package
# 按你的方式重启 srmp-admin
```

### 2. 重启 LangGraph Runtime

```bash
export SRMP_JAVA_BASE_URL=http://127.0.0.1:8080
./scripts/run-langgraph-orchestrator-dev.sh
```

或：

```bash
docker compose -f docker-compose.langgraph.yml up -d --build srmp-ai-orchestrator
```

### 3. 验证

```bash
scripts/check-phase50-4-langgraph-e2e.sh
```

如果 Java 已经切到 LangGraph：

```bash
REQUIRE_LANGGRAPH=true scripts/check-phase50-4-langgraph-e2e.sh
```

## 常见问题

### `/ready` 返回 DOWN

说明 LangGraph Runtime 到 Java Tool Gateway 不通，优先检查：

1. `SRMP_JAVA_BASE_URL` 是否指向正确 Java 后端；
2. Java 是否已应用 Phase50.3；
3. Java `/api/agent/tools` 是否能返回工具清单；
4. Docker 场景下不要在容器内使用 `127.0.0.1:8080` 指向宿主机 Java，需要使用 `host.docker.internal` 或同 compose 网络服务名。

### Java chat 仍是 native

检查 Java 进程启动环境：

```bash
echo $SRMP_AI_ORCHESTRATOR_PROVIDER
```

必须是：

```bash
SRMP_AI_ORCHESTRATOR_PROVIDER=langgraph
```

修改环境变量后必须重启 `srmp-admin`。

### Java chat fallback native

查看响应字段：

```json
{
  "data": {
    "orchestratorFallback": true,
    "orchestratorFallbackReason": "..."
  }
}
```

同时访问：

```bash
curl http://127.0.0.1:8080/api/agent/orchestrator/health
curl http://127.0.0.1:18080/ready
```
