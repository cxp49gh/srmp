# 阶段二十一：AI 方案生成说明

## 1. 阶段目标

阶段二十一将阶段二十的“方案模板”真正用于方案生成，实现：

```text
方案模板
  ↓
业务数据摘要
  ↓
知识库检索
  ↓
模板变量填充
  ↓
大模型润色
  ↓
保存生成任务
  ↓
保存引用来源
```

## 2. 新增数据库表

```text
ai_solution_task
ai_solution_source
```

初始化：

```bash
./scripts/init-ai-solution-generate-db.sh
```

或：

```bash
psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/phase21_ai_solution_generate.sql
```

前置 SQL：

```bash
./scripts/init-ai-solution-template-db.sh
```

## 3. 新增接口

```text
POST /api/ai/solution/generate
POST /api/ai/solution/tasks/list
GET  /api/ai/solution/tasks/{id}
GET  /api/ai/solution/tasks/{id}/sources
```

## 4. 请求示例

```json
{
  "solutionType": "ROAD_ASSESSMENT_REPORT",
  "routeCode": "G210",
  "year": 2026,
  "templateCode": "road_assessment_report_default",
  "options": {
    "useBusinessData": true,
    "useKnowledge": true,
    "useOutline": false,
    "topK": 5
  }
}
```

## 5. 前端页面

```text
http://localhost:5173/agent/solution-generate
http://localhost:5173/agent/solution-tasks
```

## 6. 当前实现范围

本阶段为最小闭环：

```text
1. 按 solutionType / templateId / templateCode 选择模板；
2. 调用现有路线、评定、病害分析服务生成业务摘要；
3. 检索知识库作为专业依据；
4. 填充模板变量；
5. 调用大模型润色，失败时返回本地草稿；
6. 保存任务；
7. 保存来源。
```

## 7. 支持的模板变量

```text
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

## 8. 验收

1. 初始化阶段二十和阶段二十一 SQL；
2. 打开 `/agent/solution-templates`，确认有默认模板；
3. 打开 `/agent/solution-generate`；
4. 输入 G210 / 2026；
5. 点击生成；
6. 查看生成草稿和引用来源；
7. 打开 `/agent/solution-tasks` 查看历史任务。
