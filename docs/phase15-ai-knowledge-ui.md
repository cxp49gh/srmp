# 阶段十五：AI 知识库前端管理与 Outline 接入页面

## 1. 新增内容

```text
1. 新增知识库 API 封装
2. 新增 Outline API 封装
3. 新增 AI 问答页面
4. 新增知识库文档录入页面
5. 新增知识库检索测试页面
6. 新增 Outline 状态页面
7. 新增 Outline 搜索页面
8. 增强 GIS AI 浮窗：
   - 使用业务数据
   - 使用知识库
   - 使用 Outline
   - 展示引用来源数量
```

## 2. 新增路由

```text
/agent/chat
/agent/knowledge-documents
/agent/knowledge-search
/agent/outline-search
/agent/outline-status
```

## 3. 新增路径

```text
srmp-web-ui/src/api/knowledge.ts
srmp-web-ui/src/api/outline.ts
srmp-web-ui/src/views/agent/**
srmp-web-ui/src/views/gis/components/AgentChatFloat.vue
```

## 4. 验证

```bash
cd srmp-web-ui
npm install
npm run build
npm run dev
```

访问：

```text
http://localhost:5173/agent/chat
http://localhost:5173/agent/knowledge-documents
http://localhost:5173/agent/knowledge-search
http://localhost:5173/agent/outline-status
http://localhost:5173/agent/outline-search
```

## 5. 后端前置条件

请先执行阶段十三 SQL：

```bash
psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/phase13_knowledge_outline.sql
```

如果要测试 Outline，请配置：

```bash
export OUTLINE_ENABLED=true
export OUTLINE_BASE_URL=https://outline.example.com
export OUTLINE_API_TOKEN=your-outline-token
```