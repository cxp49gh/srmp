# Phase50.13 一键启动与 LangGraph 验收闭环

## 目标

Phase50.13 把一键启动从“基础依赖 + Java 后端 + 前端”升级为完整演示栈：

- PostGIS、Redis、MinIO；
- Java 后端 `srmp-backend`；
- Python LangGraph Runtime `srmp-ai-orchestrator`；
- Vue/nginx 前端 `srmp-frontend`；
- G210/2026 业务样例数据；
- AI 方案模板、模板版本、模板有效性字段；
- AI Trace 表；
- LangGraph 运维与诊断入口。

默认启动后，Java 后端使用 `langgraph` provider，并通过容器网络访问：

```text
http://srmp-ai-orchestrator:18080
```

LangGraph Runtime 只通过 Java Tool Gateway 访问业务能力：

```text
http://backend:8080/api/agent/tools
http://backend:8080/api/agent/tools/execute
```

## 启动

```bash
./scripts/srmp-one-click-start.sh
```

默认执行顺序：

1. 启动 PostGIS、Redis、MinIO。
2. 等待 PostgreSQL ready。
3. 幂等初始化 schema、字典、管理员、AI 表、Trace 表、方案模板和 G210/2026 样例业务数据。
4. 使用组合 Compose 构建并启动：

```bash
docker compose -f docker-compose.yml -f docker-compose.app.yml -f docker-compose.langgraph.yml up -d --build backend srmp-ai-orchestrator frontend
```

5. 执行 `scripts/srmp-check-ready.sh`。

## 访问入口

- 前端：http://localhost:5173
- 后端：http://localhost:8080
- LangGraph Runtime：http://localhost:18080
- LangGraph 运维页：http://localhost:5173/agent/langgraph-ops
- MinIO 控制台：http://localhost:9001

## Native 回退启动

如果只想启动 Java 原生编排链路，不启动 LangGraph Runtime：

```bash
./scripts/srmp-one-click-start.sh --no-orchestrator
```

该模式会设置：

```bash
SRMP_AI_ORCHESTRATOR_PROVIDER=native
```

并跳过 LangGraph 容器和 Runtime ready 检查。

## Ready Check

`scripts/srmp-check-ready.sh` 默认检查：

- `srmp-postgres`、`srmp-redis`、`srmp-minio`、`srmp-ai-orchestrator` 容器状态；
- `ai_solution_template`、`ai_solution_template_version`、`ai_trace_log`、`ai_trace_step` 表是否存在；
- 默认租户启用模板数不少于 5、模板版本数不少于 5；
- G210/2026 演示数据达到路线、路段、评定单元、评定结果、病害记录阈值；
- 后端 GIS 路线接口；
- LangGraph `/health`、`/ready`；
- Java ops 代理 `/api/agent/orchestrator/ops/summary`、`/config`、`/health-detail`；
- 前端首页。

重新检查：

```bash
./scripts/srmp-one-click-start.sh --check-only
```

Native 模式检查：

```bash
./scripts/srmp-one-click-start.sh --check-only --no-orchestrator
```

## 日志

```bash
docker compose -f docker-compose.yml -f docker-compose.app.yml -f docker-compose.langgraph.yml logs -f backend
docker compose -f docker-compose.yml -f docker-compose.app.yml -f docker-compose.langgraph.yml logs -f srmp-ai-orchestrator
docker compose -f docker-compose.yml -f docker-compose.app.yml -f docker-compose.langgraph.yml logs -f frontend
```

LangGraph Runtime 审计记录默认持久化到 Docker volume：

```text
srmp_langgraph_audit
```

容器内路径：

```text
/var/lib/srmp/langgraph/runtime-audit.jsonl
```

## 验收命令

静态验收：

```bash
bash scripts/check-phase50-13-one-click-bootstrap.sh
bash scripts/check-one-click-start.sh
```

完整启动验收：

```bash
./scripts/srmp-one-click-start.sh
```

前端打开：

```text
http://localhost:5173/agent/langgraph-ops
```

确认页面显示：

- Provider 为 `langgraph`；
- LangGraph Ready 为 OK；
- Runtime 配置有 strategy version；
- 工具契约可展开；
- 最近调用、导出诊断、审计持久化和清理入口可用。
