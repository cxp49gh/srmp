# 智路养护平台数据库初始化指南

## 1. 文档说明

本文档用于说明智路养护平台一期、AI 知识库、Outline 同步相关数据库初始化方式。

最近在使用 Outline 同步入库时出现过：

```text
relation "outline_sync_task" does not exist
```

这是因为阶段十七 SQL 没有执行，导致后端写入同步任务表时失败。

为避免类似问题，新增统一初始化脚本：

```text
scripts/init-ai-db.sh
scripts/check-db-schema.sh
scripts/check-ai-startup-prereq.sh
```

---

## 2. AI / Outline 所需表

AI 知识库增强功能至少需要以下表：

```text
knowledge_document
knowledge_chunk
outline_sync_task
```

其中：

| 表 | 来源 SQL | 说明 |
|---|---|---|
| `knowledge_document` | `phase13_knowledge_outline.sql` | 知识库文档元数据 |
| `knowledge_chunk` | `phase13_knowledge_outline.sql` | 知识库文档切片 |
| `outline_sync_task` | `phase17_outline_sync.sql` | Outline 同步任务记录 |

一期 GIS 和业务数据还依赖：

```text
road_route
road_section
evaluation_unit
disease_record
assessment_result
```

---

## 3. 一键初始化 AI / Outline 数据库

默认连接参数：

```text
DB_HOST=127.0.0.1
DB_PORT=5432
DB_USER=srmp
DB_NAME=srmp
```

执行：

```bash
chmod +x scripts/*.sh
./scripts/init-ai-db.sh
```

该脚本会按顺序执行：

```text
1. srmp-admin/src/main/resources/db/phase13_knowledge_outline.sql
2. srmp-admin/src/main/resources/db/phase17_outline_sync.sql
3. srmp-admin/src/main/resources/db/phase18_ai_demo_knowledge.sql
```

---

## 4. 自定义数据库连接

如果你的数据库参数不同：

```bash
DB_HOST=127.0.0.1 \
DB_PORT=5432 \
DB_USER=postgres \
DB_NAME=srmp \
./scripts/init-ai-db.sh
```

如果需要密码：

```bash
export PGPASSWORD=你的密码
./scripts/init-ai-db.sh
```

---

## 5. 检查表结构

执行：

```bash
./scripts/check-db-schema.sh
```

输出示例：

```text
[OK]   knowledge_document
[OK]   knowledge_chunk
[OK]   outline_sync_task
[OK]   road_route
[OK]   road_section
[OK]   evaluation_unit
[OK]   disease_record
[OK]   assessment_result
```

如果出现：

```text
[FAIL] outline_sync_task 不存在
```

说明需要执行：

```bash
./scripts/init-ai-db.sh
```

---

## 6. 启动前置检查

如果后端已启动，可以执行：

```bash
./scripts/check-ai-startup-prereq.sh
```

该脚本会检查：

```text
1. 数据库表结构；
2. 本地知识库接口；
3. Outline 状态接口；
4. Outline 同步接口。
```

可自定义：

```bash
BASE_URL=http://localhost:8080 \
TENANT_ID=default \
./scripts/check-ai-startup-prereq.sh
```

---

## 7. 推荐启动顺序

完整本地启动建议：

```bash
# 1. 初始化数据库
./scripts/init-ai-db.sh

# 2. 检查数据库表结构
./scripts/check-db-schema.sh

# 3. 可选：启动 Outline
./scripts/start-outline.sh

# 4. 配置 Outline Token
export OUTLINE_ENABLED=true
export OUTLINE_BASE_URL=http://localhost:3000
export OUTLINE_API_TOKEN=你的真实Token

# 5. 启动后端
java -jar srmp-admin/target/srmp-admin-1.0.0.jar --spring.profiles.active=demo

# 6. 启动前端
cd srmp-web-ui
npm run dev

# 7. 启动后验收
./scripts/check-ai-startup-prereq.sh
```

---

## 8. Docker PostgreSQL 场景

如果 PostgreSQL 在 Docker 容器内，可以使用宿主机映射端口：

```bash
DB_HOST=127.0.0.1 DB_PORT=5432 DB_USER=srmp DB_NAME=srmp ./scripts/init-ai-db.sh
```

也可以直接进入容器执行：

```bash
docker exec -i 你的_postgres_容器名 psql -U srmp -d srmp \
  < srmp-admin/src/main/resources/db/phase17_outline_sync.sql
```

---

## 9. 常见问题

### 9.1 relation "outline_sync_task" does not exist

原因：

```text
phase17_outline_sync.sql 没有执行到当前后端连接的数据库。
```

处理：

```bash
./scripts/init-ai-db.sh
./scripts/check-db-schema.sh
```

### 9.2 relation "knowledge_document" does not exist

原因：

```text
phase13_knowledge_outline.sql 没有执行。
```

处理：

```bash
psql -h 127.0.0.1 -U srmp -d srmp \
  -f srmp-admin/src/main/resources/db/phase13_knowledge_outline.sql
```

### 9.3 SQL 执行成功但后端仍报缺表

通常是：

```text
1. SQL 执行到了另一个数据库；
2. 后端连接的是另一个 PostgreSQL；
3. DB_HOST / DB_PORT / DB_NAME 不一致；
4. Docker 容器和宿主机连接地址不一致。
```

检查后端配置中的：

```text
spring.datasource.url
spring.datasource.username
```

确保与脚本参数一致。

---

## 10. 总结

后续部署或换环境时，建议固定执行：

```bash
./scripts/init-ai-db.sh
./scripts/check-db-schema.sh
```

再启动后端。

这样可以避免：

```text
relation xxx does not exist
bad SQL grammar
PreparedStatementCallback
```

等由缺表导致的问题。
