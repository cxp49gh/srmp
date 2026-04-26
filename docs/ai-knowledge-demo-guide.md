# AI 知识库与 Outline 演示指南

## 1. 演示目标

本指南用于演示智路养护平台一期 AI 增强能力：

```text
业务数据问答
本地知识库问答
Outline 文档搜索
Outline 同步入库
混合 RAG 问答
```

## 2. 演示准备

### 2.1 启动后端

```bash
mvn clean package -DskipTests
java -jar srmp-admin/target/srmp-admin-1.0.0.jar --spring.profiles.active=demo
```

### 2.2 启动前端

```bash
cd srmp-web-ui
npm install
npm run dev
```

### 2.3 初始化知识库

```bash
psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/phase13_knowledge_outline.sql
psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/phase18_ai_demo_knowledge.sql
```

### 2.4 可选：启动 Outline

```bash
./scripts/start-outline.sh
```

访问：

```text
http://localhost:3000
```

登录：

```text
admin@example.com
password
```

创建 API Token 后配置：

```bash
export OUTLINE_ENABLED=true
export OUTLINE_BASE_URL=http://localhost:3000
export OUTLINE_API_TOKEN=你的_Outline_API_Token
```

## 3. 演示页面

### 3.1 GIS 一张图

```text
http://localhost:5173/gis/one-map
```

演示内容：

```text
1. 加载 G210 图层；
2. 点击地图对象；
3. 打开右下角 AI；
4. 勾选“业务数据”和“知识库”；
5. 提问：根据知识库解释 PCI 指标，并结合 G210 2026 年情况给出建议。
```

### 3.2 AI 问答页面

```text
http://localhost:5173/agent/chat
```

推荐问题：

```text
根据知识库解释 PCI 指标，并结合 G210 2026 年情况给出建议
数据导入模板怎么使用？
病害复核流程怎么走？
生成 G210 2026 年技术状况评定报告草稿
```

### 3.3 知识库文档

```text
http://localhost:5173/agent/knowledge-documents
```

演示内容：

```text
1. 新增 Markdown 文档；
2. 保存并切片；
3. 到知识库检索页面搜索。
```

### 3.4 知识库检索

```text
http://localhost:5173/agent/knowledge-search
```

推荐搜索：

```text
PCI 指标是什么意思
病害复核流程
数据导入模板
G210 评定报告
```

### 3.5 Outline 状态

```text
http://localhost:5173/agent/outline-status
```

### 3.6 Outline 搜索

```text
http://localhost:5173/agent/outline-search
```

### 3.7 Outline 同步

```text
http://localhost:5173/agent/outline-sync
```

演示内容：

```text
1. 查看 Outline 状态；
2. 查看 Collection；
3. 预览文档；
4. 点击“同步到知识库”；
5. 查看同步任务；
6. 到知识库检索页面搜索同步后的文档。
```

## 4. 命令行验收

```bash
./scripts/check-ai-knowledge.sh
./scripts/check-outline.sh
./scripts/check-ai-e2e.sh
```

## 5. 演示注意事项

```text
1. 如果没有真实大模型 key，系统可能走本地规则兜底；
2. 如果没有 Outline token，Outline 检查会提示不可用；
3. Outline 同步前需要先在 Outline 中创建文档；
4. 本地知识库检索是关键词检索，不是向量检索；
5. AI 输出是建议或草稿，不直接生成正式业务决策。
```
