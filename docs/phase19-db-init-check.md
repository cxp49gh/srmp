# 阶段十九：数据库初始化自动化与启动自检说明

## 1. 阶段目标

阶段十九用于解决功能 Patch 多、SQL 初始化依赖人工执行的问题。

本阶段新增：

```text
1. AI / Outline 数据库一键初始化脚本；
2. 数据库表结构检查脚本；
3. AI / Outline 启动前置检查脚本；
4. 数据库初始化说明文档；
5. 阶段十九说明文档。
```

---

## 2. 新增文件

```text
scripts/init-ai-db.sh
scripts/check-db-schema.sh
scripts/check-ai-startup-prereq.sh
docs/db-init-guide.md
docs/phase19-db-init-check.md
```

---

## 3. 一键初始化

```bash
chmod +x scripts/*.sh
./scripts/init-ai-db.sh
```

默认执行：

```text
phase13_knowledge_outline.sql
phase17_outline_sync.sql
phase18_ai_demo_knowledge.sql
```

---

## 4. 数据库结构检查

```bash
./scripts/check-db-schema.sh
```

检查表：

```text
knowledge_document
knowledge_chunk
outline_sync_task
road_route
road_section
evaluation_unit
disease_record
assessment_result
```

---

## 5. 启动前置检查

后端启动后执行：

```bash
./scripts/check-ai-startup-prereq.sh
```

默认：

```text
BASE_URL=http://localhost:8080
TENANT_ID=default
```

自定义：

```bash
BASE_URL=http://localhost:8080 TENANT_ID=default ./scripts/check-ai-startup-prereq.sh
```

---

## 6. 数据库连接参数

可通过环境变量覆盖：

```bash
DB_HOST=127.0.0.1
DB_PORT=5432
DB_USER=srmp
DB_NAME=srmp
```

示例：

```bash
DB_HOST=127.0.0.1 DB_PORT=5432 DB_USER=postgres DB_NAME=srmp ./scripts/init-ai-db.sh
```

如需密码：

```bash
export PGPASSWORD=你的密码
```

---

## 7. 解决的问题

典型错误：

```text
PreparedStatementCallback; bad SQL grammar
ERROR: relation "outline_sync_task" does not exist
```

处理方式：

```bash
./scripts/init-ai-db.sh
./scripts/check-db-schema.sh
```

---

## 8. 推荐后续方向

阶段十九完成后，一期 AI 增强链路基本可以封版。后续建议进入：

```text
阶段二期 2.1：前端平台化 Layout + 菜单 + 登录页
阶段二期 2.2：养护工单闭环
阶段二期 2.3：养护计划管理
```
