# Phase38.3：方案模板兜底为空与系统兜底模板来源重复修复

## 1. 问题

当前出现两个问题：

```text
1. 模板兜底为空；
2. 引用来源里“系统兜底模板”出现两个。
```

## 2. 根因

### 2.1 模板兜底为空

通常是以下流程之一出现了空值：

```text
模板匹配失败
  -> 进入 fallback 分支
  -> 只记录了“系统兜底模板”来源
  -> 但没有生成 fallback markdown
  -> resultContent 为空
```

或者：

```text
模板匹配到了
  -> FreeMarker / 模板渲染失败
  -> renderedContent = ""
  -> 没有二次兜底
  -> resultContent 为空
```

### 2.2 系统兜底模板来源重复

通常是因为两个地方都加了兜底来源：

```text
1. 生成 sourceSnapshot 时加了一次“系统兜底模板”；
2. 保存 ai_solution_source 时又补了一次“系统兜底模板”。
```

所以同一个 task 下会出现两个兜底模板来源。

---

## 3. 修复策略

新增支持类：

```text
AiSolutionFallbackTemplateSupport
```

提供两个核心方法：

```java
String resultContent = AiSolutionFallbackTemplateSupport.ensureContent(
    renderedContent,
    solutionType,
    routeCode,
    year,
    mapObject,
    aiAnswer
);

List<Map<String, Object>> sources = AiSolutionFallbackTemplateSupport.normalizeSources(
    sourceSnapshot,
    solutionType,
    AiSolutionFallbackTemplateSupport.isFallbackContent(resultContent)
);
```

修复原则：

```text
1. resultContent 为空时，必须生成非空 markdown；
2. 兜底 markdown 要包含 AI 方案草稿、基础信息、AI 分析摘要、主要问题、处置建议、复核要求；
3. 引用来源按 sourceType + sourceId + title 去重；
4. “系统兜底模板”无论从哪里来，同一任务只保留一次；
5. 历史脏数据用 SQL 清理。
```

---

## 4. 建议接入点

在方案生成服务中，找到类似逻辑：

```java
String resultContent = renderTemplate(...);
List<Map<String, Object>> sourceSnapshot = buildSources(...);
```

改为：

```java
String resultContent = AiSolutionFallbackTemplateSupport.ensureContent(
    renderedContent,
    solutionType,
    routeCode,
    year,
    mapObject,
    aiAnswer
);

List<Map<String, Object>> sourceSnapshot = AiSolutionFallbackTemplateSupport.normalizeSources(
    sourceSnapshot,
    solutionType,
    AiSolutionFallbackTemplateSupport.isFallbackContent(resultContent)
);
```

在保存 `ai_solution_source` 前，也再执行一次：

```java
sources = AiSolutionFallbackTemplateSupport.normalizeSources(
    sources,
    solutionType,
    false
);
```

这样即使上游重复加了来源，最终入库也只会保留一条。

---

## 5. 历史数据清理

执行：

```bash
psql -h 127.0.0.1 -U srmp -d srmp \
  -f srmp-admin/src/main/resources/db/phase38_3_cleanup_duplicate_system_fallback_sources.sql
```

---

## 6. 验收

```bash
BASE_URL=http://localhost:8080 TASK_ID=<方案任务ID> \
bash scripts/check-phase38-3-template-fallback-source-dedupe.sh
```

预期：

```text
1. resultContent 非空；
2. 如果使用兜底模板，正文包含“AI 方案草稿（系统兜底模板）”；
3. 引用来源中“系统兜底模板”最多一条。
```
