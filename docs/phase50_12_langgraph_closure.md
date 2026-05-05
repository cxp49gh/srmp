# Phase50.12 LangGraph 编排闭环收口

## 目标

Phase50.12 不新增业务生成能力，先把 `srmp-ai-orchestrator` 与前端 LangGraph 运维页收口到可持续验收状态：

- 修复真实 LangGraph 图在 `tool_planning` 提前结束的风险；
- 让 Phase50.7/50.8/50.9 旧验收脚本兼容当前 Phase50.11 策略版本；
- 把 Phase50.11 的配置快照、健康详情、审计持久化和审计清理能力接入前端；
- 同步 Docker Compose 默认镜像标签与运行时环境变量。

## 关键修正

### Runtime Graph

`srmp-ai-orchestrator/app/workflow.py` 中的 LangGraph 编译路径现在和 sequential fallback 保持一致：

```text
request_normalize
-> context_build
-> intent_recognize
-> context_enrich
-> tool_planning
-> tool_execute
-> evidence_fuse
-> answer_generate
-> quality_guard
-> END
```

此前额外存在 `tool_planning -> END`，可能导致真实 LangGraph 执行只规划工具、不执行工具、不生成回答。

Plan Debug 使用 sequential 路径，不经过 LangGraph 编译图。Phase50.12 同时补齐了节点名到处理函数的映射，确保 `tool_planning` 能正确调用 `_tool_plan`，避免 `/api/srmp/langgraph/debug/plan` 返回 500。

### 前端运维页

`/agent/langgraph-ops` 增加：

- 配置健康：显示 health status、runtime app/version、strategy version、config fingerprint、warning count；
- 审计持久化：显示持久化开关、路径、容量、写入/恢复计数、最近错误；
- Runtime 快照：通过 Java 代理 `/api/agent/orchestrator/ops/snapshot` 导出；
- 审计清理：通过 Java 代理 `/api/agent/orchestrator/ops/prune` 按状态清理，并默认保留最近 20 条。

### Docker Compose

`docker-compose.langgraph.yml` 默认镜像标签调整为：

```text
srmp-ai-orchestrator:phase50.12
```

并显式暴露以下运行时配置：

- `SRMP_LANGGRAPH_STRATEGY_VERSION`
- `SRMP_LANGGRAPH_AUDIT_MAX_RECORDS`
- `SRMP_LANGGRAPH_AUDIT_PERSIST_ENABLED`
- `SRMP_LANGGRAPH_AUDIT_PERSIST_PATH`
- `SRMP_LANGGRAPH_AUDIT_PRUNE_DEFAULT_RETAIN_LATEST`

## 验收命令

静态验收：

```bash
python3 -m py_compile srmp-ai-orchestrator/app/*.py
SKIP_LIVE=1 bash scripts/check-phase50-7-langgraph-context-source-plan.sh
SKIP_LIVE=1 bash scripts/check-phase50-8-langgraph-contract-plan-ops.sh
SKIP_LIVE=1 bash scripts/check-phase50-9-langgraph-runtime-replay-export.sh
bash scripts/check-phase50-12-langgraph-closure.sh
```

前端构建：

```bash
npm --prefix srmp-web-ui install
npm --prefix srmp-web-ui run build
```

Live 验收需要先启动 Java 后端和 Python Runtime：

```bash
bash scripts/check-phase50-4-langgraph-e2e.sh
bash scripts/check-phase50-5-langgraph-observability.sh
bash scripts/check-phase50-6-langgraph-strategy.sh
```

## 下一步

Phase50.12 通过后，再进入“普通问答、单对象、路线、区域统一接 LangGraph 编排”的业务增强阶段。那一阶段应以调用入口统一和 Trace 展示一致性为主，不再把基础运维能力和业务能力混在同一个改动中。
