# Phase37.5：一张图 AI 对象能力回归评测与策略编排收口

## 1. 阶段背景

当前一张图 AI 已完成多个对象类型增强：

```text
DISEASE：病害对象专项分析
ASSESSMENT_RESULT：评定结果对象专项分析
ROAD_SECTION：路段对象专项分析
ROAD_ROUTE：路线对象专项分析
```

并已具备：

```text
RAG 向量检索
回答依据面板
AI 健康检查
RAG 质量评测
方案草稿生成
```

但前面多轮迭代也暴露出问题：

```text
1. 修一个对象，容易影响另一个对象；
2. MapAiAgentServiceImpl 中 enhancer 和工具规划逻辑越来越多；
3. 回答清理器曾误删正文；
4. 工具调用条件需要稳定回归；
5. 前端虽然有方案按钮，但用户更希望在 AI 回答后顺手生成方案草稿。
```

本阶段目标是：

```text
建立回归测试
收口策略编排
减少 MapAiAgentServiceImpl 中的硬编码堆叠
补齐 AI 回答到方案草稿的入口
```

---

## 2. 本次实现内容

### 2.1 回答增强策略收口

新增：

```text
MapAiAnswerEnhanceContext
MapAiAnswerEnhancer
MapAiAnswerEnhancerRegistry
```

当前 registry 统一编排：

```text
1. MapObjectDiseaseAdviceEnhancer
2. AssessmentResultAdviceEnhancer
3. RoadAssetAdviceEnhancer
4. MapAiAnswerPolisher
```

`MapAiAgentServiceImpl` 从直接调用多个 enhancer，收口为：

```java
answer = mapAiAnswerEnhancerRegistry.enhance(
    answer,
    MapAiAnswerEnhanceContext.of(answer, message, context, knowledgeSources, toolResults)
);
```

后续新增对象类型时，优先扩展 enhancer/registry，而不是继续污染 Agent 主流程。

---

### 2.2 工具规划策略收口

新增：

```text
MapAiToolPlanner
MapAiToolPlannerImpl
```

将对象类型驱动的工具规划从 `MapAiAgentServiceImpl` 中拆出。

当前规则：

```text
DISEASE -> gis.queryNearbyObjects
ASSESSMENT_RESULT -> gis.queryAssessmentResults + gis.queryDiseasesByStakeRange
ROAD_SECTION -> gis.queryAssessmentResults + gis.queryDiseases + gis.queryDiseasesByStakeRange
ROAD_ROUTE -> gis.queryAssessmentResults + gis.queryDiseases
REGION -> gis.queryRegionSummary
```

普通分析不会自动调用 `solution.generateDraft`，只有明确“生成方案/方案草稿/保存方案”时才调用。

---

### 2.3 一张图对象 AI 回归测试

新增：

```text
scripts/check-phase37-5-map-object-ai-regression.sh
```

覆盖对象：

```text
DISEASE-坑槽
DISEASE-沉陷
ASSESSMENT_RESULT-中等偏低单元
ROAD_SECTION-路段对象
ROAD_ROUTE-路线对象
```

检查项：

```text
1. 专项分析标题是否存在；
2. knowledge.retrieve 是否调用；
3. sources 是否非空；
4. 是否误调用 solution.generateDraft；
5. 是否残留 #### 标题；
6. 是否包含对象关键字段和处置建议。
```

---

### 2.4 前端回答下方生成方案草稿入口

对 `AgentChatFloat.vue` 增加：

```text
基于本次分析生成方案草稿
```

位置：

```text
AI 回答下方 / 回答依据下方
```

行为：

```text
自动取当前对象的 primary solutionAction；
调用现有 generateMapObjectSolution；
打开现有 SolutionPreviewDialog；
保存逻辑复用现有 saveMapObjectSolutionDraft。
```

这样用户路径更顺：

```text
点击地图对象
AI 分析
查看依据
基于本次分析生成方案草稿
保存为方案任务
```

---

## 3. 应用方式

```bash
unzip srmp-phase37-5-map-object-ai-regression-strategy.zip -d /tmp/phase37-5
cp -r /tmp/phase37-5/srmp-phase37-5-map-object-ai-regression-strategy/* /path/to/srmp/

cd /path/to/srmp

chmod +x scripts/apply-phase37-5-map-ai-strategy-chain.sh
chmod +x scripts/apply-phase37-5-agent-chat-solution-action.sh
chmod +x scripts/check-phase37-5-strategy-structure.sh
chmod +x scripts/check-phase37-5-map-object-ai-regression.sh

bash scripts/apply-phase37-5-map-ai-strategy-chain.sh
bash scripts/apply-phase37-5-agent-chat-solution-action.sh

mvn clean package -DskipTests
npm --prefix srmp-web-ui run build
```

---

## 4. 验收

### 4.1 结构检查

```bash
bash scripts/check-phase37-5-strategy-structure.sh
```

### 4.2 回归测试

```bash
BASE_URL=http://localhost:8080 \
bash scripts/check-phase37-5-map-object-ai-regression.sh
```

预期：

```text
[OK] disease-pothole
[OK] disease-subsidence
[OK] assessment-medium
[OK] road-section
[OK] road-route
[OK] Phase37.5 一张图对象 AI 回归通过：5/5
```

### 4.3 前端验证

进入：

```text
/gis/one-map
```

选择任意地图对象，点击 AI 分析。AI 回答下方应出现：

```text
基于本次分析生成方案草稿
```

点击后应打开现有方案预览弹窗，并可保存为方案任务。

---

## 5. 后续建议

Phase37.5 验收后，建议进入：

```text
Phase38：AI 方案草稿到方案任务的完整业务闭环增强
```

目标：

```text
AI 分析结果
  -> 方案草稿
  -> 保存任务
  -> 历史版本
  -> 状态流转
  -> 导出报告
  -> 方案对比
```
