# 阶段二十八：地图对象 AI 回答质量修复

## 1. 问题现象

点击单个病害对象后，AI 回答已经带上：

```text
【基于当前地图对象】
【当前地图选中对象】
- 对象类型：DISEASE
- 路线：G210
- 起点桩号：69.007
- 终点桩号：69.034
- 病害：修补损坏
- 严重程度：MEDIUM
- 数量：11.02
- 单位：m2
```

说明地图上下文传入成功。

但后续回答出现两个问题：

```text
1. 页面暴露 <think>...</think>
2. 当前选中的是单个病害，但 AI 回答成了整条 G210 路线病害统计和 TOP10 热点
```

## 2. 根因

最新 `AgentChatServiceImpl` 会解析地图对象，但仍继续走：

```text
agentAnalysisService.analyzeDisease(analysisRequest)
```

而该方法内部做的是路线级病害聚合分析，所以单个 DISEASE 对象会被回答成整条路线病害统计。

同时后端没有统一清洗 `<think>...</think>`。

## 3. 修复内容

```text
1. 新增 sanitizeAssistantAnswer，统一过滤 <think>...</think> 和 <thinking>...</thinking>
2. 对 objectType=DISEASE / DISEASE_RECORD 的当前地图对象，优先使用单对象回答
3. 单对象回答包含：
   - 当前对象摘要
   - 主要问题
   - 成因判断
   - 养护处置建议
4. 避免单个病害误答成整条路线 TOP10 统计
5. 新增 MAP_OBJECT_LOCAL answerSource 标识
6. 补充 MapObjectContext.empty()/of()，避免接口实现使用静态方法但实体未定义的问题
```

## 4. 应用方式

```bash
git pull origin main
git apply srmp-phase28-map-object-answer-quality-fix.patch
chmod +x scripts/*.sh
./scripts/apply-phase28-map-object-answer-quality.sh
mvn clean package -DskipTests
```

## 5. 验收

```bash
./scripts/check-phase28-map-object-answer-quality.sh
```

手动验收：

```text
1. 打开 /gis/one-map
2. 点击一个病害对象
3. 点击 AI 分析此对象
4. 回答不应出现 <think>
5. 回答不应再展开为整条路线病害总数 / TOP10 热点
6. 回答应围绕当前单个病害：
   - 病害类型
   - 桩号
   - 严重程度
   - 成因判断
   - 处置建议
```

## 6. 预期回答结构

```text
【基于当前地图对象】

【当前地图选中对象】
- 对象类型：DISEASE
- 路线：G210
- 桩号：K69.007—K69.034
- 病害：修补损坏
- 严重程度：MEDIUM
- 数量：11.02m2

## 当前对象判断
...

## 主要问题
...

## 成因判断
...

## 养护处置建议
...
```
