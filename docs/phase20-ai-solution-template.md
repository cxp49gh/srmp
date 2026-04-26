# 阶段二十：AI 方案模板管理说明

## 1. 阶段目标

阶段二十用于建立 AI 方案生成的模板基础能力。

本阶段不直接做完整方案生成，而是先完成：

```
1. 方案模板表；
2. 方案模板版本表；
3. 模板变量解析；
4. 模板 CRUD 接口；
5. 从知识库文档登记为模板；
6. 前端方案模板管理页面。
```

## 2. 新增接口

```
POST /api/ai/solution/templates
POST /api/ai/solution/templates/list
GET  /api/ai/solution/templates/{id}
GET  /api/ai/solution/templates/{id}/versions
POST /api/ai/solution/templates/import-from-knowledge
POST /api/ai/solution/templates/{id}/disable
```

## 3. 新增前端页面

```
http://localhost:5173/agent/solution-templates
```

页面能力：

```
1. 模板列表；
2. 新增模板；
3. 自动解析 {{变量}}；
4. 查看模板详情；
5. 查看模板版本；
6. 从知识库文档 ID 登记为模板。
```

## 4. 初始化 SQL

```bash
psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/phase20_ai_solution_template.sql
```

或：

```bash
./scripts/init-ai-solution-template-db.sh
```

## 5. 模板变量格式

```
{{routeCode}}
{{year}}
{{routeSummary}}
{{assessmentSummary}}
{{diseaseSummary}}
{{lowScoreSections}}
{{problemAnalysis}}
{{maintenanceSuggestion}}
{{riskNotice}}
```

## 6. Outline 模板应用流程

```
Outline 维护方案模板
  ↓
Outline 同步入库
  ↓
knowledge_document / knowledge_chunk
  ↓
在方案模板页面输入 knowledge_document.id
  ↓
登记为 ai_solution_template
  ↓
解析模板变量
  ↓
后续阶段二十一用于方案生成
```

## 7. 验收

```bash
./scripts/init-ai-solution-template-db.sh
```

访问：

```
http://localhost:5173/agent/solution-templates
```

新增一个模板，内容包含：

```markdown
# {{routeCode}} {{year}} 年技术状况评定报告

## 评定结果
{{assessmentSummary}}

## 养护建议
{{maintenanceSuggestion}}
```

保存后，右侧应能看到变量：

```
routeCode
year
assessmentSummary
maintenanceSuggestion
```

## 8. 下一阶段

阶段二十一建议做：

```
AI 方案生成：按模板调用业务数据工具、检索知识库、生成方案草稿并保存。
```
