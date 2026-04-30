# 一键启动与演示数据初始化设计

## 1. 背景

当前 SRMP 已具备 Docker 基础依赖、数据库建表 SQL、业务样例数据、AI 知识库、方案模板、Trace、方案任务和 Phase35 模板样例，但入口分散在多个脚本中：

- `docker-compose.yml`
- `scripts/init-db.sh`
- `scripts/init-ai-db.sh`
- `scripts/init-ai-solution-template-db.sh`
- `scripts/init-ai-solution-generate-db.sh`
- `scripts/init-ai-trace-db.sh`
- `scripts/init-phase35-sample-templates.sh`
- `scripts/start-backend.sh`
- `scripts/start-frontend.sh`
- 多个 `check-*.sh` 验收脚本

这会导致新环境或演示环境启动时需要人工记住执行顺序，也容易出现“后端已启动但数据库缺表 / 模板未初始化 / 样例数据不足”的问题。

## 2. 目标

提供一个默认安全的一键启动体系，让开发者或验收人员可以用一条命令完成：

1. 启动 PostGIS、Redis、MinIO。
2. 等待数据库可用。
3. 初始化基础表结构、字典、管理员。
4. 初始化业务样例数据。
5. 按需初始化业务样例数据。
6. 初始化 AI 知识库、Outline 同步表、Trace、方案任务、模板配置和 Phase35 样例模板。
7. 构建并启动后端。
8. 构建并启动前端。
9. 执行启动后健康检查。
10. 输出访问地址、日志路径、常见排查提示。

默认行为必须是安全幂等初始化：只补齐缺失内容和可重复 upsert 的样例配置，不主动清空用户已有业务数据。重置演示数据必须通过显式参数触发。

## 3. 非目标

本阶段不做以下事情：

- 不引入 Flyway/Liquibase 等迁移框架。
- 不重构现有 SQL 表结构。
- 不替换现有 Docker Compose 服务。
- 不默认删除数据库卷。
- 不默认覆盖用户本地配置文件。
- 不把 Outline 服务纳入默认一键启动，Outline 仍通过现有 `scripts/start-outline.sh` 独立启动。

## 4. 推荐入口

### 4.1 总入口

新增：

```bash
scripts/srmp-one-click-start.sh
```

默认使用：

```bash
./scripts/srmp-one-click-start.sh
```

默认动作：

- 启动基础依赖。
- 初始化数据库和样例配置。
- 构建后端、前端。
- 启动后端、前端。
- 运行 ready check。

### 4.2 数据初始化入口

新增：

```bash
scripts/srmp-init-demo.sh
```

职责：

- 统一执行数据库结构和样例 SQL。
- 自动选择 `psql` 执行方式。
- 本机有 `psql` 时优先使用本机 `psql`。
- 本机没有 `psql` 且 `srmp-postgres` 容器存在时，自动使用 `docker exec srmp-postgres psql`。
- 默认幂等执行。

### 4.3 启动检查入口

新增：

```bash
scripts/srmp-check-ready.sh
```

职责：

- 检查 Docker 依赖状态。
- 检查数据库连接。
- 检查关键表和样例数据数量。
- 检查模板配置是否存在。
- 检查后端基础接口。
- 检查 GIS 和模板匹配接口。

## 5. 命令参数

### 5.1 `srmp-one-click-start.sh`

支持参数：

```text
--reset-demo       显式重置演示业务数据，允许重刷 G210/2026 样例数据。
--skip-build       跳过后端和前端构建，直接启动已有产物。
--backend-only     只启动基础依赖、初始化数据、构建和启动后端。
--frontend-only    只启动前端，不初始化数据库，不启动后端。
--no-start         只启动依赖并初始化数据库，不启动后端/前端。
--check-only       只执行 ready check。
--help             输出帮助。
```

默认不执行 `--reset-demo`。

### 5.2 环境变量

保留现有习惯，支持：

```bash
DB_HOST=127.0.0.1
DB_PORT=5432
DB_NAME=srmp
DB_USER=srmp
DB_PASSWORD=srmp123
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
MINIO_ENDPOINT=http://127.0.0.1:9000
BACKEND_PORT=8080
FRONTEND_PORT=5173
TENANT_ID=default
YEAR=2026
ROUTE_CODE=G210
```

Maven 优先级：

1. 如果设置了 `MAVEN_HOME`，使用 `$MAVEN_HOME/bin/mvn`。
2. 否则使用 PATH 中的 `mvn`。

Java 优先级：

1. 如果设置了 `JAVA_HOME`，使用 `$JAVA_HOME/bin/java`。
2. 否则使用 PATH 中的 `java`。

## 6. 数据初始化顺序

`srmp-init-demo.sh` 按以下顺序执行：

```text
schema.sql
init_dict.sql
init_admin.sql
业务样例数据检测与按需初始化
phase13_knowledge_outline.sql
phase17_outline_sync.sql
phase18_ai_demo_knowledge.sql
phase20_ai_solution_template.sql
phase21_ai_solution_generate.sql
phase22_ai_trace_monitor.sql
phase33_ai_solution_draft_version.sql
phase35_template_effectiveness.sql
phase35_sample_solution_templates.sql
```

原因：

- `schema.sql` 提供基础业务表。
- `init_dict.sql` 和 `init_admin.sql` 提供基础字典和管理员。
- 业务样例数据检测与按需初始化负责保证 G210/2026 演示数据可用。
- Phase13/17/18 提供知识库和 Outline 相关表及演示知识。
- Phase20/21/22/33/35 提供方案模板、方案任务、Trace、草稿版本、模板效果字段和样例模板。

业务样例数据检测规则：

```text
road_route >= 1
road_section >= 1
road_evaluation_unit >= 1000
assessment_result >= 1000
disease_record >= 3000
```

如果当前租户和年份已达到以上阈值，默认跳过业务样例重刷，避免覆盖用户已调整的演示数据。

如果未达到阈值：

- 默认执行安全样例补齐逻辑。
- 若仍复用现有 `demo_data.sql`，必须在脚本输出中明确提示它只会重刷 `tenant_id='default' AND route_code='G210'` 范围内的数据。
- 后续实现可以新增更完整的 `demo_business_data.sql` 或生成脚本，把样例规模补齐到验收阈值。

## 7. 幂等和重置策略

### 7.1 默认幂等模式

默认模式下：

- 不删除 Docker volume。
- 不 drop schema。
- 不清空全库业务表。
- 执行 `CREATE TABLE IF NOT EXISTS`、`ALTER TABLE ADD COLUMN IF NOT EXISTS`、`INSERT ... ON CONFLICT` 等幂等 SQL。
- 先检查业务样例数据是否达标，达标则跳过业务样例重刷。
- 不达标时才执行业务样例补齐。
- 任何会删除并重刷 G210 的逻辑都必须在日志中明确提示范围，并且不得影响非默认租户、非 G210 路线和用户新增模板。

### 7.2 显式重置模式

`--reset-demo` 允许重置演示业务数据，但仍不删除整库：

- 重刷 G210/2026 演示路线、路段、评定单元、病害、评定结果、指标结果。
- 重刷样例 AI 知识库。
- 重刷默认模板配置。
- 不影响非演示路线、非默认租户和用户新增模板。

如果未来需要“彻底重建数据库卷”，必须使用单独参数，例如 `--reset-volume`，本阶段不实现该参数。

## 8. `psql` 执行方式

脚本需要兼容本机没有 PostgreSQL 客户端的情况。

执行策略：

1. 如果 `PSQL_BIN` 显式设置，直接使用。
2. 如果 PATH 中存在 `psql`，使用本机 `psql`。
3. 如果 Docker 可用且 `srmp-postgres` 容器存在，使用：

```bash
docker exec -i srmp-postgres psql -U "$DB_USER" -d "$DB_NAME"
```

4. 如果以上都不可用，输出清晰错误：

```text
[FAIL] 未找到 psql，且 srmp-postgres 容器不可用。
请先执行 docker compose up -d，或安装 PostgreSQL 客户端。
```

对于容器模式，SQL 文件通过 stdin 输入，不要求把 SQL 文件复制到容器内。

## 9. 服务启动方式

### 9.1 基础依赖

默认执行：

```bash
docker compose up -d postgres redis minio
```

然后等待 PostgreSQL 可用。

### 9.2 后端

默认构建：

```bash
mvn -pl srmp-admin -am package -DskipTests
```

默认启动：

```bash
java \
  -Djava.net.useSystemProxies=false \
  -Dhttp.nonProxyHosts='localhost|127.*|[::1]' \
  -Dhttps.nonProxyHosts='localhost|127.*|[::1]' \
  -Dspring.profiles.active=demo \
  -jar srmp-admin/target/srmp-admin-1.0.0.jar
```

日志输出：

```text
logs/srmp-backend.log
```

PID 文件：

```text
logs/srmp-backend.pid
```

如果 8080 已被占用：

- 如果 PID 文件存在且指向同一个 jar，先停止旧进程再启动。
- 如果端口由其他程序占用，停止启动并输出占用信息，不强杀未知进程。

### 9.3 前端

默认构建：

```bash
npm --prefix srmp-web-ui install
npm --prefix srmp-web-ui run build
```

开发启动：

```bash
npm --prefix srmp-web-ui run dev -- --host 0.0.0.0 --port 5173
```

日志输出：

```text
logs/srmp-frontend.log
```

PID 文件：

```text
logs/srmp-frontend.pid
```

## 10. Ready Check

`srmp-check-ready.sh` 检查以下内容：

### 10.1 基础依赖

- `srmp-postgres` 容器存在并可连接。
- `srmp-redis` 容器存在。
- `srmp-minio` 容器存在。

### 10.2 数据库

检查关键表存在：

- `tenant`
- `road_route`
- `road_section`
- `road_evaluation_unit`
- `disease_record`
- `assessment_result`
- `index_result`
- `knowledge_document`
- `knowledge_chunk`
- `ai_solution_template`
- `ai_solution_template_version`
- `ai_solution_task`
- `ai_trace_log`
- `ai_trace_step`

检查默认模板：

- `road_assessment_report_default`
- `map_object_disease_treatment_default`
- `map_object_assessment_low_score_default`
- `map_object_road_section_maintenance_default`
- `map_region_maintenance_advice_default`

### 10.3 后端接口

检查：

```text
GET /api/health
GET /api/demo/status?tenantId=default&year=2026
GET /api/gis/road-routes?routeCode=G210
GET /api/gis/diseases?routeCode=G210
POST /api/ai/solution/templates/match-preview
```

模板匹配请求：

```json
{
  "originType": "MAP_REGION",
  "objectType": "MAP_REGION",
  "solutionType": "REGION_MAINTENANCE_SUGGESTION"
}
```

期望：

- `code=0`
- `templateMeta` 或返回数据中包含 `templateCode=map_region_maintenance_advice_default`
- `fallback=false`

## 11. 错误处理

脚本必须使用：

```bash
set -euo pipefail
```

关键失败点要输出明确提示：

- Docker 不可用。
- 端口被占用。
- 数据库连接失败。
- SQL 执行失败。
- Maven 不存在。
- Java 不存在。
- npm 不存在。
- 后端启动超时。
- 前端启动超时。
- ready check 失败。

失败时输出：

- 当前步骤。
- 使用的配置。
- 相关日志路径。
- 推荐排查命令。

## 12. 文档

新增：

```text
docs/one-click-start-guide.md
```

内容包括：

- 一键启动命令。
- 参数说明。
- 默认端口。
- 默认账号密码。
- 数据库初始化范围。
- 样例业务数据范围。
- 样例模板配置范围。
- 日志位置。
- 常见问题。

README 中“快速开始”应更新为优先推荐：

```bash
./scripts/srmp-one-click-start.sh
```

保留原手工启动步骤作为高级用法。

## 13. 测试计划

### 13.1 静态检查

```bash
bash -n scripts/srmp-one-click-start.sh
bash -n scripts/srmp-init-demo.sh
bash -n scripts/srmp-check-ready.sh
```

### 13.2 数据初始化检查

在已有 Docker 环境下执行：

```bash
./scripts/srmp-init-demo.sh
./scripts/srmp-check-ready.sh --db-only
```

重复执行一次，确认幂等：

```bash
./scripts/srmp-init-demo.sh
./scripts/srmp-check-ready.sh --db-only
```

### 13.3 一键启动检查

```bash
./scripts/srmp-one-click-start.sh --skip-build
```

验证：

- 后端 8080 可访问。
- 前端 5173 可访问。
- ready check 通过。

### 13.4 构建检查

```bash
mvn -pl srmp-admin -am package -DskipTests
npm --prefix srmp-web-ui run build
```

### 13.5 接口检查

复用：

```bash
BASE_URL=http://localhost:8080 TENANT_ID=default YEAR=2026 ROUTE_CODE=G210 \
RUN_BUILD=0 RUN_API=1 ./scripts/phase31-acceptance.sh
```

并新增：

```bash
./scripts/srmp-check-ready.sh
```

## 14. 验收标准

完成后应满足：

- 新环境执行一条命令即可启动可用系统。
- 本机没有 `psql` 时仍可通过 Docker 容器初始化数据库。
- 重复执行初始化不会破坏非演示数据。
- 默认模板配置存在，区域方案模板匹配不再提示“未返回模板信息”。
- 后端和前端日志可定位。
- README 和独立指南能让新成员按步骤跑起来。
