# Phase50.27 运行时自适应验收脚本设计

## 背景

Phase50.26 已经补齐自适应回放对比、预算护栏和 Ops 页面入口，并完成一次真实运行时验收。验收过程中暴露出一个静态测试没有覆盖的问题：Runtime `MapAgentRunResponse` 顶层返回 `planExecution`，Java DTO 不接收该字段，导致 Java 后端把成功 Runtime 响应转成失败。

这说明 Phase50.26 的静态验收不足以覆盖 Java Backend、Python Runtime、Java Tool Gateway 和前端产物之间的真实契约。Phase50.27 不新增编排能力，而是把刚才手工完成的 live 验收固化成可重复脚本。

## 目标

1. 新增一个 live acceptance 脚本，验证已启动的 `backend`、`srmp-ai-orchestrator` 和 `frontend`。
2. 自动产生一条可回放的 Java `map-agent/run` 记录，并确认 Runtime 审计返回 `runtimeAuditId`。
3. 通过 Java ops 代理执行 `adaptiveMode=compare` 回放，确认 baseline/adaptive/compare 结构和关键状态。
4. 验证 Runtime 与 Java 代理配置都暴露 `maxAdaptiveAddedTools=1`。
5. 验证前端生产产物中包含“自适应对比”入口。
6. 输出清晰失败原因和临时诊断文件路径，方便排查 live 环境问题。

## 非目标

- 不启动、停止、重建 Docker 容器。
- 不初始化或重置数据库。
- 不要求知识库一定命中，因此不把 `evidenceImproved=true` 作为硬门槛。
- 不替代 Phase50.26 静态验收脚本。
- 不新增前端页面交互或 Runtime 业务行为。
- 不执行写工具。

## 方案对比

### 方案 A：扩展 Phase50.26 静态脚本直接跑 live 验收

优点：入口少。

缺点：静态脚本当前可以在服务未启动时运行；混入 live 检查会让 CI/本地快速验收变慢，也会让失败原因不清晰。

### 方案 B：新增独立 live acceptance 脚本

脚本只在服务已启动时运行，专门验证真实 HTTP 链路。

优点：边界清楚；失败时能直接定位 live 链路；不影响现有静态验收速度。

缺点：需要额外维护一个脚本和说明文档。

### 方案 C：用端到端浏览器测试覆盖 Ops 页面点击

优点：最接近人工操作。

缺点：需要浏览器自动化环境、登录态和稳定页面数据；当前阶段成本偏高，且主要风险在 HTTP 契约，不在 DOM 交互。

### 选型

选择方案 B。Phase50.27 先做轻量 HTTP live gate，后续如果 Ops 页面交互复杂化，再单独补浏览器级验收。

## 脚本设计

新增脚本：

```bash
scripts/check-phase50-27-runtime-adaptive-acceptance.sh
```

默认地址：

```bash
JAVA_URL=http://127.0.0.1:8080
RUNTIME_URL=http://127.0.0.1:18080
FRONTEND_URL=http://127.0.0.1:5173
TENANT_ID=default
```

脚本只读取服务状态并发起只读 AI 分析请求。所有响应落到 `/tmp`，文件名前缀为 `srmp-phase50-27-`。

### 步骤 1：服务健康

检查：

- `GET $RUNTIME_URL/health`
- `GET $JAVA_URL/api/agent/orchestrator/health`
- `GET $FRONTEND_URL/`

要求：

- Runtime `status=UP`
- Java 返回 `code=0` 且 `data.status=UP`
- Java health 中 `langgraphHealth.ok=true`
- 前端首页可访问

### 步骤 2：配置契约

检查：

- `GET $RUNTIME_URL/api/srmp/langgraph/runtime/config`
- `GET $JAVA_URL/api/agent/orchestrator/ops/config`

要求：

- Runtime `safeConfig.adaptivePlanningEnabled=true`
- Runtime `safeConfig.maxAdaptiveAddedTools=1`
- Java ops wrapper `data.ok=true`
- Java 代理到的 body 中 `safeConfig.maxAdaptiveAddedTools=1`

### 步骤 3：产生可回放记录

调用：

```http
POST $JAVA_URL/api/agent/map-agent/run
```

请求使用稳定的“无业务数据路线”场景：

```json
{
  "action": "ANALYZE_REGION",
  "message": "请分析一段没有示例数据的路线区域，并补充养护处置依据。",
  "mapContext": {
    "tenantId": "default",
    "mode": "REGION",
    "routeCode": "NO_DATA_PHASE50_27",
    "year": 2026,
    "geometry": {
      "type": "Polygon",
      "coordinates": [[[106.0, 30.0], [106.01, 30.0], [106.01, 30.01], [106.0, 30.01], [106.0, 30.0]]]
    }
  },
  "options": {
    "traceId": "phase50-27-runtime-<timestamp>",
    "useKnowledge": false,
    "topK": 3,
    "maxAdaptiveAddedTools": 1
  }
}
```

`useKnowledge=false` 让第一轮只规划 `gis.queryRegionSummary`。由于该路线不存在业务数据，第一轮证据不足，自适应规划应追加 `knowledge.retrieve`。

要求：

- Java 返回 `code=0`
- response `action=ANALYZE_REGION`
- response `intent=REGION_ANALYSIS`
- response `data.runtimeAuditId` 非空
- response `data.adaptivePlanning.status=EXECUTED`
- response `data.adaptivePlanning.addedToolNames` 包含 `knowledge.retrieve`
- response `data.toolTotalCount >= 2`

不要求：

- 不要求 `evidence.sufficient=true`
- 不要求 `knowledgeHitCount > 0`

原因：不同本地知识库状态可能导致命中数不同，但不会影响自适应追加链路是否生效。

### 步骤 4：Java ops compare 回放

调用：

```http
POST $JAVA_URL/api/agent/orchestrator/ops/replay/{runtimeAuditId}?execute=true&adaptiveMode=compare
```

要求：

- Java wrapper `code=0`
- Java wrapper `data.ok=true`
- Runtime body `execute=true`
- Runtime body `adaptiveMode=compare`
- Runtime body 包含 `baseline.response`、`adaptive.response`、`compare`
- `compare.baselineAdaptiveStatus=DISABLED`
- `compare.adaptiveStatus=EXECUTED`
- `compare.toolDelta >= 1`
- adaptive 工具总数大于 baseline 工具总数

记录但不作为硬门槛：

- `compare.evidenceImproved`
- `compare.costDeltaMs`
- baseline/adaptive knowledge hit count

### 步骤 5：前端产物入口

脚本从 `$FRONTEND_URL/` 解析 `index-*.js` 资源并下载。

要求：

- JS 产物中包含 `自适应对比`
- JS 产物中包含 `adaptiveMode`

该检查不证明用户点击流程完整，但能证明当前前端容器已部署 Phase50.26 的入口和 API 参数。

## 错误处理

- 任一 HTTP 请求失败：输出 URL、HTTP 阶段和对应 `/tmp` 响应文件。
- JSON 字段缺失：输出缺失字段名和当前响应摘要。
- 前端资源解析失败：输出首页前 20 行和下载路径。
- compare 返回 `evidenceImproved=false`：只打印提示，不失败。
- 服务未启动：失败并提示使用当前 Docker compose 栈启动。

## 实现细节

- 脚本使用 Bash 编排，使用 Python 解析 JSON，避免依赖 `jq`。
- `curl` 使用 `--max-time`，默认请求超时：
  - health/config：10 秒
  - map-agent run：180 秒
  - compare replay：240 秒
- 每个检查使用统一 `fail()` 和 `pass()` 输出。
- 脚本不写仓库文件，只写 `/tmp/srmp-phase50-27-*`。
- 支持通过环境变量覆盖 URL、租户和超时。

## 文档

新增文档：

```bash
docs/phase50_27_runtime_adaptive_acceptance.md
```

内容包括：

- 使用前提：服务已启动并运行当前代码。
- 推荐启动方式：沿用现有 Docker compose 栈。
- live 验收命令。
- 成功输出关键字段。
- 常见失败原因：
  - 运行中容器未更新到当前 main。
  - Runtime config 没有 `maxAdaptiveAddedTools`。
  - Java DTO 契约不兼容 Runtime response。
  - 本地知识库 0 命中但 compare 结构正常。

## 测试策略

静态验证：

- `bash -n scripts/check-phase50-27-runtime-adaptive-acceptance.sh`
- `git diff --check`

live 验证：

```bash
bash scripts/check-phase50-27-runtime-adaptive-acceptance.sh
```

回归验证：

```bash
bash scripts/check-phase50-26-adaptive-replay-guard.sh
```

## 验收标准

1. 已启动服务环境下，Phase50.27 live 脚本可以自动完成 health、config、run、compare 和前端产物检查。
2. 脚本能产生新的 `runtimeAuditId`，并通过 Java ops replay compare 成功回放。
3. compare 结果证明自适应链路相对 baseline 至少多执行一个工具。
4. 脚本不依赖知识库必须命中，不因 `evidenceImproved=false` 误失败。
5. 文档清楚说明脚本的前提、命令和失败排查路径。
