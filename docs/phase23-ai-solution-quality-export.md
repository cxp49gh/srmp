# 阶段二十三：AI 方案质量校验与 Markdown 导出

## 1. 阶段目标

阶段二十三将 AI 生成的方案从"文本草稿"升级为"可检查、可追溯、可导出"的交付物。

新增能力：

```text
1. 方案质量校验；
2. 必填章节检查；
3. 空值 / null / NaN 检查；
4. 引用来源检查；
5. AI 草稿风险提示检查；
6. 质量评分和等级；
7. Markdown 导出；
8. 前端质量校验面板。
```

## 2. 新增接口

```text
POST /api/ai/solution/tasks/{id}/quality-check
GET  /api/ai/solution/tasks/{id}/quality-result
GET  /api/ai/solution/tasks/{id}/export/markdown
```

## 3. 校验规则

### 3.1 必填章节

```text
路线概况
评定结果
主要病害
问题分析
养护建议
风险提示
```

### 3.2 空值检查

```text
null
undefined
NaN
平均 MQI null
平均 PCI null
平均 PQI null
总数 0
```

### 3.3 来源检查

```text
BUSINESS_DATA
KNOWLEDGE / OUTLINE
TEMPLATE
```

### 3.4 风险提示检查

检查正文是否包含：

```text
草稿
人工审核
审核确认
```

## 4. 前端页面

在方案任务页面增强：

```text
http://localhost:5173/agent/solution-tasks
```

新增：

```text
1. 质量校验按钮；
2. 质量评分面板；
3. 校验明细；
4. Markdown 导出按钮。
```

## 5. 命令行验收

先生成一个方案任务，得到 taskId。

然后执行：

```bash
TASK_ID=你的任务ID ./scripts/check-ai-solution-quality.sh
```

或手动：

```bash
curl -s -X POST http://localhost:8080/api/ai/solution/tasks/{id}/quality-check \
  -H "X-Tenant-Id: default"

curl -OJ http://localhost:8080/api/ai/solution/tasks/{id}/export/markdown \
  -H "X-Tenant-Id: default"
```

## 6. 数据库说明

本阶段不新增表。

质量校验结果写回已有字段：

```text
ai_solution_task.quality_result
```

引用来源继续使用：

```text
ai_solution_source
```

## 7. 下一阶段建议

阶段二十四可继续做：

```text
1. Word / PDF 导出；
2. 人工审核意见；
3. AI 方案修改；
4. 方案转养护计划；
5. LangGraph / Agent 编排评估。
```