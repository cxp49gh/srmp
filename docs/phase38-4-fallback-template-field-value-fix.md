# Phase38.4：方案结果兜底模板字段为空修复

## 1. 问题

方案任务页面中，“方案结果”的系统兜底模板字段为空：

```text
路线编号：-
年度：-
对象类型：-
桩号范围：-
病害类型：-
严重程度：-
数量：-
```

但实际任务里有这些值。

## 2. 根因

兜底模板生成时，只从单一 `mapObject` 取值。

而真实值可能分散在：

```text
1. ai_solution_task.route_code / year / object_type / solution_type
2. ai_solution_task.map_object
3. ai_solution_task.object_summary
4. ai_solution_task.ai_context
5. ai_context.raw.mapContext
6. ai_context.raw.mapObject
7. mapObject.raw
8. camelCase / snake_case 两种字段名
```

所以模板取不到值，最终显示为空。

## 3. 修复内容

### 3.1 AiSolutionFallbackTemplateSupport 增强

新增能力：

```text
mergeContext(...)
ensureContentFromTask(...)
repairFallbackContentIfNeeded(...)
```

字段取值会自动合并：

```text
主表字段
map_object
object_summary
ai_context
ai_context.raw
ai_context.raw.mapContext
ai_context.raw.mapObject
mapObject.raw
```

并兼容：

```text
routeCode / route_code
objectType / object_type
startStake / start_stake / startMileage / start_mileage
endStake / end_stake / endMileage / end_mileage
diseaseName / disease_name
diseaseType / disease_type
measureUnit / measure_unit
```

### 3.2 导出修复

`export/markdown-v2` 在导出时，如果发现历史兜底模板字段为空，会基于任务真实数据动态重建兜底模板正文。

### 3.3 前端页面展示修复

`SolutionTasksPage.vue` 增加 `displayResultContent(detail)`。

如果页面加载到历史空字段兜底模板，会在前端展示时根据 `detail + aiContext` 动态补齐字段，不需要用户重新生成方案。

---

## 4. 应用

```bash
unzip srmp-phase38-4-fallback-template-field-value-fix.zip -d /tmp/phase38-4
cp -r /tmp/phase38-4/srmp-phase38-4-fallback-template-field-value-fix/* /path/to/srmp/

cd /path/to/srmp
chmod +x scripts/apply-phase38-4-fallback-template-field-value-fix.sh
chmod +x scripts/check-phase38-4-fallback-template-field-value-fix.sh

bash scripts/apply-phase38-4-fallback-template-field-value-fix.sh

mvn clean package -DskipTests
npm --prefix srmp-web-ui run build
```

## 5. 验收

```bash
BASE_URL=http://localhost:8080 TASK_ID=<方案任务ID> \
bash scripts/check-phase38-4-fallback-template-field-value-fix.sh
```

预期：

```text
1. 兜底模板不再显示路线编号/年度/对象类型为空；
2. 若任务中有病害字段，病害类型、严重程度、数量也会显示；
3. Markdown V2 导出同步修复；
4. 系统兜底模板引用来源最多一条。
```
