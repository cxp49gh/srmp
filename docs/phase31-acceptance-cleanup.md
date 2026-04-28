# 阶段三十一：一期收口验收与代码清理

## 1. 目标

阶段三十一不新增业务功能，目标是把一期连续迭代后的 GIS、AI、Demo、知识库、地图对象上下文能力进行一次统一验收和清理，避免后续继续叠加功能时出现：

```text
1. 补丁残留目录污染仓库；
2. 前后端地图对象上下文语义不一致；
3. AI 回答仍暴露 <think>；
4. 单个病害误答成路线级统计；
5. 一张图页面能打开但接口数据为空；
6. README 标注完成，但源码或接口没有真正可验收。
```

## 2. 当前基线

本阶段按 GitHub `main` 最新提交检查：

```text
3d946e0 docs: update phase 28 status in README
```

仓库根目录当前可看到正式模块，也能看到疑似补丁残留，例如 `srmp-map-ai-context-final-fix/...`、`patch-phase26-ai-analyze-auto-run.py`、`phase9-ai-statistics-filter-fix.md`。这些应在确认无用后归档或删除。

## 3. 新增脚本

```text
scripts/phase31-acceptance.sh
scripts/check-phase31-repo-clean.sh
scripts/check-phase31-source-consistency.sh
scripts/check-phase31-demo-data.sh
scripts/check-phase31-gis-api.sh
scripts/check-phase31-ai-map-object.sh
scripts/check-phase31-web-source.sh
```

## 4. 使用方式

### 4.1 仅做静态检查

```bash
chmod +x scripts/*.sh
RUN_BUILD=0 RUN_API=0 ./scripts/phase31-acceptance.sh
```

### 4.2 做构建检查

```bash
RUN_BUILD=1 RUN_API=0 ./scripts/phase31-acceptance.sh
```

### 4.3 后端服务启动后做接口验收

```bash
BASE_URL=http://localhost:8080 TENANT_ID=default YEAR=2026 ROUTE_CODE=G210 \
RUN_BUILD=0 RUN_API=1 ./scripts/phase31-acceptance.sh
```

### 4.4 仓库补丁残留清理

先查看：

```bash
./scripts/check-phase31-repo-clean.sh
```

确认后移动到归档目录：

```bash
./scripts/check-phase31-repo-clean.sh --apply
```

归档目录：

```text
docs/patch-history/phase31-archive
```

## 5. 验收标准

### 5.1 构建

```text
mvn clean package -DskipTests 通过
npm --prefix srmp-web-ui run build 通过
```

### 5.2 一张图

```text
1. /gis/one-map 能打开；
2. 图层控制可切换路线、路段、评定、病害；
3. 点击对象后详情变化；
4. AI 浮窗显示当前地图上下文；
5. 点击 AI 分析此对象会自动带 mapObject 请求 /api/agent/chat。
```

### 5.3 AI 地图对象

```text
1. data.mapObjectUsed=true；
2. answer 不包含 <think>；
3. 单个 DISEASE 不输出“病害总数 / TOP10 热点”等路线级统计；
4. answer 能体现当前地图对象；
5. answerSource/fallback 展示不误导。
```

### 5.4 Demo 数据

```text
/api/demo/status 有数据状态
/api/demo/dashboard summary 有路线、评定、病害计数
/api/gis/road-routes 有 GeoJSON
/api/gis/diseases 有病害图层
/api/gis/assessment-results 有评定结果图层
```

## 6. 下一阶段建议

阶段三十一通过后，再进入阶段三十二：

```text
地图对象一键生成处置建议 / 方案草稿
```

包括：

```text
点击病害 → 生成病害复核意见
点击评定结果 → 生成处置建议
点击路线 → 生成路线技术状况报告
点击路段 → 生成养护计划草稿
```
