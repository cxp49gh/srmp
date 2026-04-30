# Phase36 补强实现说明：知识导入与向量检索验收闭环

## 新增/修改内容

### 后端

```text
GET  /api/ai/knowledge/stats
POST /api/ai/knowledge/search 返回 searchMode / vectorUsed / fallback / embeddingProvider
MapAiAgent Trace 增加 knowledge_retrieve step
KnowledgeRetrieveTool summary 增加 searchMode 与向量检索提示
```

### 前端

```text
新增 /agent/knowledge-vector 页面
新增 getAiKnowledgeStats API
AgentPageShell 增加“向量知识库验证”菜单
```

### 数据与脚本

```text
docs/knowledge/road-maintenance/*.md
scripts/import-phase36-demo-knowledge.sh
scripts/check-phase36-vector-rag-e2e.sh
```

## 使用步骤

```bash
# 1. 应用代码
unzip srmp-phase36-knowledge-vector-e2e-implementation.zip -d /tmp/phase36-e2e
cp -r /tmp/phase36-e2e/srmp-phase36-knowledge-vector-e2e-implementation/* /path/to/srmp/

# 2. 构建
cd /path/to/srmp
mvn clean package -DskipTests
npm --prefix srmp-web-ui run build

# 3. 导入知识
BASE_URL=http://localhost:8080 TENANT_ID=default \
bash scripts/import-phase36-demo-knowledge.sh

# 4. 验收
BASE_URL=http://localhost:8080 TENANT_ID=default \
bash scripts/check-phase36-vector-rag-e2e.sh
```

## 前端页面

```text
/agent/knowledge-vector
```
