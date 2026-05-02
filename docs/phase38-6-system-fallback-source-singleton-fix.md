# Phase38.6：系统兜底模板来源单例化与举一反三防重

## 问题

病害处置建议里出现两个“系统兜底模板”。原因通常是：方案正文内嵌了一次“引用来源 - 系统兜底模板”，同时 sources 面板或 Markdown V2 导出又展示了一次。

## 修复原则

```text
方案正文只放方案正文；
引用来源只由 sources 面板 / ai_solution_source / Markdown V2 引用来源章节展示；
系统兜底模板在同一任务下最多保留一条。
```

## 修复内容

```text
1. 新增 AiSolutionFallbackSourceGuard
2. 剥离兜底正文内嵌的“引用来源 - 系统兜底模板”
3. 后端 loadSources / export/markdown-v2 做兜底来源去重
4. 前端 SolutionTasksPage.vue 展示 sources 前再防御性去重
5. 数据库脚本清理历史重复，并加唯一 partial index
```

## 应用

```bash
unzip srmp-phase38-6-system-fallback-source-singleton-fix.zip -d /tmp/phase38-6
cp -r /tmp/phase38-6/srmp-phase38-6-system-fallback-source-singleton-fix/* /path/to/srmp/

cd /path/to/srmp
chmod +x scripts/apply-phase38-6-system-fallback-source-singleton.sh
chmod +x scripts/check-phase38-6-system-fallback-source-singleton.sh
chmod +x scripts/check-phase38-6-static-source-guard.sh

bash scripts/apply-phase38-6-system-fallback-source-singleton.sh

psql -h 127.0.0.1 -U srmp -d srmp \
  -f srmp-admin/src/main/resources/db/phase38_6_system_fallback_source_singleton.sql

mvn clean package -DskipTests
npm --prefix srmp-web-ui run build
```

## 验收

```bash
BASE_URL=http://localhost:8080 TASK_ID=<方案任务ID> \
bash scripts/check-phase38-6-system-fallback-source-singleton.sh
```

```bash
bash scripts/check-phase38-6-static-source-guard.sh
```
