# Phase39.2.2：Outline 同步控制台契约收口与向量化闭环

## 1. 目标

修复前端已有能力与后端契约不一致的问题，并把 Outline 同步链路明确收口为：

```text
Outline 文档
  -> /api/outline/sync 同步到 ai_knowledge_document / ai_knowledge_chunk
  -> /api/outline/knowledge-stats 查看待向量化数量
  -> /api/outline/vectorize 补齐 OUTLINE chunk embedding
  -> /api/ai/knowledge/search 参与 RAG 检索
```

## 2. 修复内容

- 补齐 `POST /api/outline/sync-tasks/{id}/retry-failed`。
- `GET /api/outline/sync-tasks/{id}/details` 支持 `status` 过滤。
- `dryRun=true` 只预演，不写 `ai_knowledge_*`。
- `cleanupMissing=true` 将本地过期 OUTLINE 文档置 `INACTIVE`。
- `documentIds` 支持指定文档同步，用于失败重试。
- 同步支持分页，不再只同步第一页。
- 明细返回 camelCase / snake_case 双字段。
- 前端增加 Outline 知识库统计、补向量、强制重建向量按钮。

## 3. 应用

```bash
unzip srmp-phase39-2-2-outline-sync-contract-vector-closure.zip -d /tmp/phase39-2-2
cp -r /tmp/phase39-2-2/srmp-phase39-2-2-outline-sync-contract-vector-closure/* /path/to/srmp/

cd /path/to/srmp

psql -h 127.0.0.1 -U srmp -d srmp   -f srmp-admin/src/main/resources/db/phase39_2_2_outline_sync_contract_vector_closure.sql

chmod +x scripts/check-phase39-2-2-outline-sync-contract-vector-closure.sh
chmod +x scripts/check-phase39-2-2-outline-no-legacy-write.sh

mvn clean package -DskipTests
npm --prefix srmp-web-ui run build
```

## 4. 验收

```bash
BASE_URL=http://localhost:8080 LIMIT=50 VECTOR_LIMIT=200 bash scripts/check-phase39-2-2-outline-sync-contract-vector-closure.sh
```

检查不再写旧知识库：

```bash
bash scripts/check-phase39-2-2-outline-no-legacy-write.sh
```

前端验证：

```text
/agent/outline-sync
```
