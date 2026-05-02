# Phase39.2.1：Outline 同步明细信息缺失与展示错位修复

## 1. 问题

当前 Outline 同步明细存在两个问题：

```text
1. 信息缺失：
   - 没有 URL
   - 没有 updatedAt
   - 没有 contentHash / oldContentHash
   - 没有内容长度
   - 没有更清楚的 detailMessage

2. 展示不对：
   - 后端 JDBC 返回 snake_case
   - 前端部分字段使用 camelCase
   - 导致标题、失败原因、chunk 数、耗时等字段可能显示为空或错位
```

## 2. 修复内容

### 2.1 数据库字段增强

新增：

```text
outline_url
outline_updated_at
content_chars
content_hash
old_content_hash
detail_message
document_status
```

### 2.2 后端 details 返回增强

`OutlineSyncServiceImpl.details(...)` 现在会返回：

```text
snake_case 字段
camelCase 字段
```

例如：

```json
{
  "outline_document_id": "...",
  "outlineDocumentId": "...",
  "outline_title": "...",
  "outlineTitle": "...",
  "content_hash": "...",
  "contentHash": "..."
}
```

这样前端不管使用哪种命名都能正常展示。

### 2.3 Outline API 解析增强

兼容多种 Outline 返回结构：

```text
data: []
data.data: []
data.documents: []
documents: []
```

并兼容字段：

```text
text / markdown / content
url / appUrl / documentUrl
updatedAt / updated_at / lastModifiedAt
collectionId / collection.id
```

### 2.4 前端展示增强

`OutlineSyncPage.vue` 改为统一使用：

```ts
value(row, 'camelCase', 'snake_case')
```

并增加：

```text
1. 明细统计条；
2. 展开行；
3. URL 打开；
4. contentHash / oldContentHash；
5. contentChars；
6. detailMessage；
7. errorType / errorMessage；
8. snake_case / camelCase 双兼容。
```

---

## 3. 应用方式

```bash
unzip srmp-phase39-2-1-outline-sync-detail-display-fix.zip -d /tmp/phase39-2-1
cp -r /tmp/phase39-2-1/srmp-phase39-2-1-outline-sync-detail-display-fix/* /path/to/srmp/

cd /path/to/srmp

psql -h 127.0.0.1 -U srmp -d srmp \
  -f srmp-admin/src/main/resources/db/phase39_2_1_outline_sync_detail_display_fix.sql

chmod +x scripts/check-phase39-2-1-outline-sync-detail-display-fix.sh

mvn clean package -DskipTests
npm --prefix srmp-web-ui run build
```

## 4. 验收

```bash
BASE_URL=http://localhost:8080 LIMIT=50 \
bash scripts/check-phase39-2-1-outline-sync-detail-display-fix.sh
```

预期：

```text
1. details 不为空；
2. details 同时包含 camelCase / snake_case 兼容字段；
3. outlineTitle 不全为空；
4. contentHash/contentChars/detailMessage 能展示；
5. 前端明细抽屉不再错位。
```
