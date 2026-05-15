# Phase50.26 自适应回放对比与预算护栏

## 目标

Phase50.26 用运维回放回答一个问题：自适应规划是否真的带来了更多证据。

本阶段补齐三件事：

- 自适应规划每轮追加工具有预算护栏，默认最多追加 1 个只读工具。
- Runtime replay 支持关闭自适应和 baseline/adaptive 对比。
- Ops 页面可以一键触发“自适应对比”，展示工具、耗时和证据命中差异。

## 配置

Runtime 新增配置：

```bash
SRMP_MAX_ADAPTIVE_ADDED_TOOLS=1
```

语义：

- 默认值为 `1`。
- 最小值为 `0`，表示本轮不追加工具。
- 最大值不会超过 `SRMP_LANGGRAPH_MAX_TOOL_CALLS`。
- 请求级 `options.maxAdaptiveAddedTools` 可以覆盖本次请求预算。

Runtime 配置接口会在 `safeConfig` 和 strategy metadata 中暴露：

```json
{
  "adaptivePlanningEnabled": true,
  "maxAdaptiveIterations": 1,
  "maxAdaptiveAddedTools": 1
}
```

## 回放接口

Runtime replay 新增 query：

```http
POST /api/srmp/langgraph/debug/replay/{recordId}?execute=true&adaptiveMode=default
POST /api/srmp/langgraph/debug/replay/{recordId}?execute=true&adaptiveMode=off
POST /api/srmp/langgraph/debug/replay/{recordId}?execute=true&adaptiveMode=compare
```

模式：

- `default`：保持默认回放行为。
- `off`：执行回放时写入 `options.disableAdaptivePlanning=true`。
- `compare`：同一条审计 request 执行两次，baseline 关闭自适应，adaptive 使用默认自适应。

`adaptiveMode=compare` 只允许 `execute=true`。如果 `execute=false`，Runtime 返回 400。

Java 代理透传同名参数：

```http
POST /api/agent/orchestrator/ops/replay/{recordId}?execute=true&adaptiveMode=compare
```

## Response 字段

自适应摘要新增精简证据：

```json
{
  "adaptivePlanning": {
    "status": "EXECUTED",
    "maxAddedTools": 1,
    "evidenceBefore": {
      "sufficient": false,
      "businessHitCount": 0,
      "knowledgeHitCount": 0,
      "toolSuccessCount": 1,
      "toolFailedCount": 0
    },
    "evidenceAfter": {
      "sufficient": true,
      "businessHitCount": 0,
      "knowledgeHitCount": 2,
      "toolSuccessCount": 2,
      "toolFailedCount": 0
    }
  }
}
```

`adaptiveMode=compare` 返回：

```json
{
  "execute": true,
  "adaptiveMode": "compare",
  "baseline": {"response": {}},
  "adaptive": {"response": {}},
  "response": {},
  "compare": {
    "toolDelta": 1,
    "costDeltaMs": 32,
    "baselineAdaptiveStatus": "DISABLED",
    "adaptiveStatus": "EXECUTED",
    "evidenceImproved": true,
    "baselineEvidenceSufficient": false,
    "adaptiveEvidenceSufficient": true,
    "baselineBusinessHitCount": 0,
    "adaptiveBusinessHitCount": 0,
    "baselineKnowledgeHitCount": 0,
    "adaptiveKnowledgeHitCount": 2
  }
}
```

顶层 `response` 指向 adaptive 结果，便于现有执行过程抽屉继续展示回放链路。

## Ops 页面

`/agent/langgraph-ops` 的最近调用表格新增“自适应对比”按钮。

点击后：

- 调用 Java ops replay，参数为 `execute=true&adaptiveMode=compare`。
- 复用执行过程抽屉展示 adaptive 回放链路。
- 回放对比卡展示 baseline/adaptive 状态、证据是否改善、工具差值、耗时差值、knowledge/business 命中差异。

## 静态验收

不启动服务时，运行：

```bash
bash scripts/check-phase50-26-adaptive-replay-guard.sh
```

该脚本会检查关键源码字段，并执行：

```bash
PYTHONPATH=srmp-ai-orchestrator python -m unittest \
  tests.test_adaptive_planner \
  tests.test_adaptive_workflow \
  tests.test_runtime_config_adaptive \
  tests.test_replay_adaptive_compare

node --no-warnings --test srmp-web-ui/tests/langGraphOpsAdaptive.test.mjs

JAVA_HOME=$(/usr/libexec/java_home -v 17) \
  mvn -pl srmp-agent \
    -Dtest=AgentOrchestratorOpsControllerLiveTraceTest,RemoteLangGraphOrchestratorContractTest \
    test
```

默认还会执行：

```bash
npm --prefix srmp-web-ui run build
```

如只想跳过前端生产构建：

```bash
SKIP_BUILD=1 bash scripts/check-phase50-26-adaptive-replay-guard.sh
```

## 运行时验收

启动 Java、Runtime 和前端后：

1. 打开 `/agent/langgraph-ops`。
2. 产生一条可回放的 LangGraph 调用记录。
3. 在最近调用表格点击“自适应对比”。
4. 确认回放对比卡显示：
   - baseline/adaptive status；
   - evidence 是否改善；
   - 工具差值；
   - 耗时差值；
   - knowledge/business hit 差异。

## 注意事项

- compare 会执行两次只读链路，只适合运维主动回放，不影响普通用户请求。
- baseline/adaptive 使用当前工具和知识库状态重新执行，因此结果可能与原始请求时刻略有差异。
- Java Maven 在较新的 JDK 上可能被 Lombok/Javac 兼容性拦截；建议使用 JDK 17 跑 Java 聚焦测试。
