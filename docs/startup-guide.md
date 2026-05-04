# SRMP 工程各模块启动方式

> 本文档描述 SRMP 各模块的正确启动顺序和命令。

## 前提条件

- Docker Desktop 已启动（用于数据库、中间件）
- JDK 17+
- Maven 3.8+
- Node.js 18+
- Python 3.9+（通过 .venv 隔离）

---

## 1. 数据库 / 中间件（必须先启动）

```bash
cd /Users/cxp/.codex/worktrees/d1e1/srmp

# 启动所有依赖服务：Postgres + Redis + MinIO 等
docker compose -f docker-compose.yml up -d

# 验证服务状态
docker compose ps
```

---

## 2. Java 后端（srmp-admin）

### 编译打包

> **注意**：每次修改 `srmp-agent` 后，必须重新 install agent，再打包 admin。

```bash
cd /Users/cxp/.codex/worktrees/d1e1/srmp

# step 1: 安装 srmp-agent 到本地 Maven 仓库
mvn -pl srmp-agent install -DskipTests

# step 2: 打包 srmp-admin
mvn -pl srmp-admin clean package -DskipTests
```

### 启动

```bash
cd /Users/cxp/.codex/worktrees/d1e1/srmp

export OUTLINE_ENABLED=true
export OUTLINE_BASE_URL=http://localhost:3000
export OUTLINE_API_TOKEN=ol_api_pdJapK4ghpURyGmGTtV70igoCYuEW527iaGG7n
export DASHSCOPE_API_KEY="sk-f39cf265c5ad4c378dc052d01db084e3"

java -DsocksProxyHost= -jar srmp-admin/target/srmp-admin-1.0.0.jar \
     --spring.profiles.active=dev &
```

**启动验证**
```bash
curl http://localhost:8080/api/agent/tools         # 查看工具列表
curl http://localhost:8080/api/agent/orchestrator/health  # 查看编排健康状态
```

---

## 3. Python LangGraph 编排服务（srmp-ai-orchestrator）

### 方式 A：本地直接运行（推荐开发调试用）

```bash
cd /Users/cxp/.codex/worktrees/d1e1/srmp/srmp-ai-orchestrator

# 关键：SRMP_JAVA_BASE_URL 必须指向本机 8080（不是 host.docker.internal）
SRMP_JAVA_BASE_URL=http://127.0.0.1:8080 \
.venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 18080 > /tmp/uvicorn.log 2>&1 &
```

**验证**
```bash
curl http://localhost:18080/health          # 应返回 UP
curl http://localhost:18080/ready          # 应返回 status: UP, toolGateway.ok: true
```

### 方式 B：Docker Compose（生产环境推荐）

```bash
cd /Users/cxp/.codex/worktrees/d1e1/srmp

# Docker 容器内用 host.docker.internal 访问宿主机 Java
SRMP_JAVA_BASE_URL=http://host.docker.internal:8080 \
docker compose -f docker-compose.langgraph.yml up -d --build srmp-ai-orchestrator
```

---

## 4. 前端（srmp-web-ui）

### 安装依赖

```bash
cd /Users/cxp/.codex/worktrees/d1e1/srmp/srmp-web-ui

npm install
```

### 开发模式

```bash
npm run dev
# 访问 http://localhost:5173
```

### 预览生产构建

```bash
npx vite preview --port 5173
```

---

## 5. 切换编排 Provider

Java 默认 `provider=native`（走原生 Java 链路）。

**切换到 LangGraph 远程编排**：
```bash
cd /Users/cxp/.codex/worktrees/d1e1/srmp

SRMP_AI_ORCHESTRATOR_PROVIDER=langgraph \
SRMP_LANGGRAPH_URL=http://127.0.0.1:18080 \
java -DsocksProxyHost= -jar srmp-admin/target/srmp-admin-1.0.0.jar \
     --spring.profiles.active=dev
```

**切换回 Native**：
```bash
SRMP_AI_ORCHESTRATOR_PROVIDER=native \
java -DsocksProxyHost= -jar srmp-admin/target/srmp-admin-1.0.0.jar \
     --spring.profiles.active=dev
```

---

## 6. 各服务端口一览

| 服务 | 端口 | 说明 |
|------|------|------|
| srmp-admin (Java) | 8080 | 后端 API 主入口 |
| srmp-ai-orchestrator (Python) | 18080 | LangGraph 编排服务 |
| srmp-web-ui (Vite) | 5173 | 前端开发服务器 |
| Postgres | 5432 | 数据库 |
| Redis | 6379 | 缓存 |
| MinIO | 9000 | 对象存储 |

---

## 7. 常见问题

**Q: Java Tool Gateway 返回 502？**
- 检查 Java 是否在 8080 端口正常运行
- 检查 Python 端的 `SRMP_JAVA_BASE_URL` 是否正确（本地开发用 `http://127.0.0.1:8080`，Docker 内用 `http://host.docker.internal:8080`）

**Q: LangGraph /ready 返回 DOWN？**
- 说明 Python 无法访问 Java 的 `/api/agent/tools`
- 本地运行时确保 `SRMP_JAVA_BASE_URL=http://127.0.0.1:8080`
- Docker 运行时确保 `SRMP_JAVA_BASE_URL=http://host.docker.internal:8080`

**Q: srmp-agent 修改后 Java 看不到新 Controller？**
- 需重新 `mvn -pl srmp-agent install` 再 `mvn -pl srmp-admin clean package`

**Q: 如何确认 Phase50 各阶段验证通过？**
```bash
# Phase50.3 Tool Gateway
bash scripts/check-phase50-3-tool-gateway.sh

# Phase50.4 LangGraph E2E
bash scripts/check-phase50-4-langgraph-e2e.sh

# Phase50.5 Observability
bash scripts/check-phase50-5-langgraph-observability.sh
```