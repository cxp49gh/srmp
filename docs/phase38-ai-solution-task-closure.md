# Phase38：AI 方案任务闭环与版本管理增强

## 1. 阶段目标

当前一张图 AI 已经完成对象专项分析、RAG 检索、回答依据面板、方案草稿生成入口和方案任务保存。

Phase38 的目标是把这些能力串成完整闭环：

```text
AI 分析结果
  -> 生成方案草稿
  -> 保存为方案任务
  -> 保存 AI Trace / answer / sources / toolResults
  -> 查看任务详情
  -> 查看版本历史
  -> 恢复历史版本
  -> 状态流转
  -> 导出 Markdown 报告
```

## 2. 本次实现内容

### 2.1 数据库增强

新增迁移：

```text
srmp-admin/src/main/resources/db/phase38_ai_solution_task_closure.sql
```

增强字段：

```text
ai_trace_id
ai_answer
ai_sources
ai_tool_results
ai_evidence
ai_context
generation_mode
confirmed_at
archived_at
```

### 2.2 后端接口

新增：

```text
POST /api/ai/solution/tasks/{id}/ai-context
GET  /api/ai/solution/tasks/{id}/ai-context
GET  /api/ai/solution/tasks/{id}/status-timeline
POST /api/ai/solution/tasks/{id}/versions/{versionNo}/restore
GET  /api/ai/solution/tasks/{id}/export/markdown-v2
```

### 2.3 前端增强

`AgentChatFloat.vue` 保存方案任务后，自动写入：

```text
aiAnswer
aiSources
aiToolResults
aiEvidence
aiTraceId
generationMode
```

`SolutionTasksPage.vue` 新增展示：

```text
AI 分析依据
状态时间线
恢复历史版本
Markdown V2 导出
```

## 3. 应用方式

```bash
unzip srmp-phase38-ai-solution-task-closure.zip -d /tmp/phase38
cp -r /tmp/phase38/srmp-phase38-ai-solution-task-closure/* /path/to/srmp/

cd /path/to/srmp

psql -h 127.0.0.1 -U srmp -d srmp \
  -f srmp-admin/src/main/resources/db/phase38_ai_solution_task_closure.sql

chmod +x scripts/apply-phase38-ai-solution-task-closure.sh
chmod +x scripts/check-phase38-ai-solution-task-closure.sh

bash scripts/apply-phase38-ai-solution-task-closure.sh

mvn clean package -DskipTests
npm --prefix srmp-web-ui run build
```

## 4. 验收

先在一张图中：

```text
1. 选择地图对象
2. AI 分析
3. 点击“基于本次分析生成方案草稿”
4. 保存为方案任务
```

再执行：

```bash
BASE_URL=http://localhost:8080 \
bash scripts/check-phase38-ai-solution-task-closure.sh
```

也可指定任务：

```bash
BASE_URL=http://localhost:8080 TASK_ID=<任务ID> \
bash scripts/check-phase38-ai-solution-task-closure.sh
```

预期：

```text
1. AI 上下文保存成功；
2. 任务详情页能看到 AI 分析依据；
3. 状态时间线可读取；
4. Markdown V2 导出包含 AI 分析摘要和 AI 依据摘要；
5. 历史版本可恢复到草稿。
```

## 5. 下一阶段建议

Phase38 完成后，建议进入：

```text
Phase39：AI 方案任务审批流与报告导出增强
```
