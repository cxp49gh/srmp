# Phase50.9 LangGraph Runtime Replay / Export 闭环

## 目标

在 Phase50.8 的工具契约诊断和 Plan Debug 基础上，继续补齐运行期排障闭环：

- 最近调用可以查看完整内存审计记录；
- 最近调用可以基于原始 requestPayload 进行 Plan 回放；
- 必要时可以重新执行完整只读链路，验证 Tool Gateway 修复是否生效；
- 可以一次性导出 Runtime 诊断快照，便于复制给研发排障。

> 注意：这里仍然是轻量内存审计，不替代 Java 侧 ai_trace_log / ai_trace_step 持久化。容器重启后记录会清空。

## Runtime 新增接口

```http
GET /api/srmp/langgraph/observability/record/{recordIdOrTraceId}
GET /api/srmp/langgraph/observability/export?limit=30&status=FAILED
POST /api/srmp/langgraph/debug/replay/{recordIdOrTraceId}?execute=false
POST /api/srmp/langgraph/debug/replay/{recordIdOrTraceId}?execute=true
```

### 回放语义

- `execute=false`：只执行 `request_normalize -> context_build -> intent_recognize -> context_enrich -> tool_plan`，不会调用 Java 工具；
- `execute=true`：重新执行完整 LangGraph 链路。当前策略仍默认只读，写工具会继续被阻断。

## Java 代理接口

```http
GET  /api/agent/orchestrator/ops/record/{recordIdOrTraceId}
GET  /api/agent/orchestrator/ops/export?limit=30&status=FAILED
POST /api/agent/orchestrator/ops/replay/{recordIdOrTraceId}?execute=false
```

前端页面不需要直连 Python Runtime。

## 前端变化

`/agent/langgraph-ops` 增加：

- 顶部“导出诊断”按钮；
- Runtime 调用表格中的“Plan回放”；
- Runtime 调用表格中的“执行”回放；
- “查看”会优先从 Runtime 拉取完整 record，包含 `requestPayload`、`responsePreview`、`steps`、`toolResults`。

## 建议验收

```bash
python3 -m py_compile srmp-ai-orchestrator/app/*.py
SKIP_LIVE=1 scripts/check-phase50-9-langgraph-runtime-replay-export.sh
```

启动 Java 与 Python Runtime 后：

```bash
scripts/check-phase50-9-langgraph-runtime-replay-export.sh
```

## 配置变化

默认策略版本：

```text
phase50.9-replay-export-v1
```

可继续通过环境变量覆盖：

```bash
export SRMP_LANGGRAPH_STRATEGY_VERSION=phase50.9-replay-export-v1
```
