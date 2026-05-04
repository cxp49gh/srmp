# Phase50.8 LangGraph 工具契约诊断与 Plan Debug 控制台闭环

## 目标

在 Phase50.7 的上下文归一化、sources 回填、`debug/plan` 基础上，本阶段继续把 `srmp-ai-orchestrator` 的联调排障能力收口到 Java 后端和前端控制台，解决下面几类常见问题：

1. Runtime 计划调用的工具，Java Tool Gateway 没注册；
2. Java 已注册工具，但 Runtime 白名单未放行；
3. 只读模式下写工具是否被正确屏蔽；
4. 前端传入对象/框选区域后，不知道 Runtime 会识别成什么 intent、规划哪些工具；
5. 排障时需要反复登录 Python 容器看接口，不方便。

## 本阶段改动

### 1. Python Runtime

核心文件：

```text
srmp-ai-orchestrator/app/config.py
srmp-ai-orchestrator/app/java_tools.py
srmp-ai-orchestrator/app/main.py
srmp-ai-orchestrator/app/workflow.py
srmp-ai-orchestrator/app/schemas.py
srmp-ai-orchestrator/app/prompt.py
srmp-ai-orchestrator/app/observability.py
```

新增接口：

```http
GET /api/srmp/langgraph/debug/contract
```

返回内容包括：

- `javaTools`：Java Tool Gateway 实际暴露工具；
- `runtimeAllowedTools`：Runtime 当前白名单；
- `missingInJava`：Runtime 允许但 Java 未注册；
- `blockedByRuntimeWhitelist`：Java 有但 Runtime 没放行；
- `writeBlocked`：疑似写工具且当前只读模式屏蔽；
- `readOnly`、`strategyVersion`、`javaBaseUrl` 等联调信息。

策略版本更新为：

```text
phase50.8-contract-debug-v1
```

> 说明：本包是累计包，包含 Phase50.7 的 `request_normalize`、`debug/plan`、sources 回填和 Java `MapAiContext.geometry`。

### 2. Java 后端

核心文件：

```text
srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/controller/AgentOrchestratorOpsController.java
srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/dto/MapAiContext.java
```

新增代理接口：

```http
GET  /api/agent/orchestrator/ops/contract
POST /api/agent/orchestrator/ops/plan
```

`/ops/summary` 也会额外回填：

```json
{
  "contractDebug": "..."
}
```

这样前端只访问 Java 后端即可完成 Runtime、Tool Gateway、契约和 Plan Debug 排障。

### 3. 前端控制台

核心文件：

```text
srmp-web-ui/src/api/orchestrator.ts
srmp-web-ui/src/views/agent/LangGraphOpsPage.vue
```

页面增强：

- 增加“工具契约”指标卡；
- 增加“契约诊断”按钮；
- 增加“工具契约详情”面板，直接展示 `missingInJava`、`blockedByRuntimeWhitelist`、`writeBlocked`；
- 增加“Plan Debug”表单，可以模拟对象/框选区域上下文，只规划不执行；
- 保留原 Smoke、最近调用和链路诊断能力。

## 应用方式

```bash
unzip phase50_8_langgraph_contract_plan_ops_package.zip
bash phase50_8_langgraph_contract_plan_ops_package/apply-phase50-8-langgraph-contract-plan-ops.sh /path/to/srmp
```

重启：

```bash
# Java 后端
srmp-admin

# Python Runtime
cd srmp-ai-orchestrator
uvicorn app.main:app --host 0.0.0.0 --port 18080
```

## 验证

静态 + 运行时验证：

```bash
python3 -m py_compile srmp-ai-orchestrator/app/*.py
scripts/check-phase50-8-langgraph-contract-plan-ops.sh
```

如果 Runtime 暂未启动，只做静态检查：

```bash
SKIP_LIVE=1 scripts/check-phase50-8-langgraph-contract-plan-ops.sh
```

## 典型 curl

### Runtime 契约诊断

```bash
curl -s http://127.0.0.1:18080/api/srmp/langgraph/debug/contract \
  -H 'X-Tenant-Id: default' | jq .
```

### Java 代理契约诊断

```bash
curl -s http://127.0.0.1:8080/api/agent/orchestrator/ops/contract \
  -H 'X-Tenant-Id: default' | jq .
```

### Plan Debug

```bash
curl -s -X POST http://127.0.0.1:8080/api/agent/orchestrator/ops/plan \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: default' \
  -d '{
    "message":"框选区域裂缝怎么处置？",
    "context":{
      "mode":"POLYGON",
      "routeCode":"G210",
      "year":2026,
      "geometry":{"type":"Polygon","coordinates":[]},
      "mapObject":{"routeCode":"G210","diseaseName":"裂缝","severity":"中度"}
    },
    "options":{"topK":3}
  }' | jq .
```

预期：

- `intent = REGION_ANALYSIS`；
- `toolPlan` 包含 `gis.queryRegionSummary`、`gis.queryDiseases`、`knowledge.retrieve`；
- `contextSummary.geometryType = Polygon`。

## 注意事项

- 当前默认仍是只读编排，写工具不会执行；
- `debug/plan` 不调用 Java Tool Gateway，适合排查规划逻辑；
- `debug/contract` 会访问 Java `/api/agent/tools`，如果 Java 未启动或路径未注册，会返回清晰错误；
- 如果前端页面显示“工具契约异常”，优先看 `missingInJava` 和 `blockedByRuntimeWhitelist`。
