# Phase37.4.6：路线/路段对象专项回答增强

## 1. 问题背景

前面已经增强了：

```text
DISEASE 病害对象
ASSESSMENT_RESULT 评定结果对象
```

但路线、路段对象仍可能出现：

```text
1. AI 回答只有地图上下文和参考资料；
2. 没有“当前路线专项分析”或“当前路段专项分析”；
3. 没有结合评定结果和病害分布；
4. 没有路线/路段级养护建议；
5. 工具规划没有主动查询病害和评定结果。
```

## 2. 修复内容

### 2.1 新增 RoadAssetAdviceEnhancer

新增：

```text
srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/service/impl/RoadAssetAdviceEnhancer.java
```

支持对象：

```text
ROAD_ROUTE / ROUTE
ROAD_SECTION / SECTION / ROAD_SEGMENT / SEGMENT
```

路线对象输出：

```text
当前路线专项分析
一、主要问题
二、成因判断
三、养护处置建议
四、后续操作建议
```

路段对象输出：

```text
当前路段专项分析
一、主要问题
二、成因判断
三、现场复核重点
四、养护处置建议
```

### 2.2 MapDiseaseQueryTool 增强

`gis.queryDiseases` 增加 summary：

```text
diseaseTypes
severities
totalQuantity
```

用于路线/路段回答中展示：

```text
主要病害类型
严重程度分布
病害数量
```

### 2.3 MapAiAgentServiceImpl 工具规划增强

通过脚本 patch：

```text
scripts/patch-phase37-4-6-road-route-section-answer.py
```

对路线对象：

```text
gis.queryAssessmentResults
gis.queryDiseases
knowledge.retrieve
```

对路段对象：

```text
gis.queryAssessmentResults
gis.queryDiseases
gis.queryDiseasesByStakeRange
knowledge.retrieve
```

### 2.4 Prompt 增强

新增：

```text
若当前对象为路线或路段，应结合评定结果、病害分布、低分区间和路段属性，输出路线/路段级养护建议。
```

## 3. 应用方式

```bash
unzip srmp-phase37-4-6-road-route-section-answer-quality.zip -d /tmp/phase37-4-6
cp -r /tmp/phase37-4-6/srmp-phase37-4-6-road-route-section-answer-quality/* /path/to/srmp/

cd /path/to/srmp
chmod +x scripts/apply-phase37-4-6-road-route-section-answer.sh
chmod +x scripts/check-phase37-4-6-road-route-section-answer.sh

bash scripts/apply-phase37-4-6-road-route-section-answer.sh

mvn clean package -DskipTests
npm --prefix srmp-web-ui run build
```

## 4. 验收

```bash
BASE_URL=http://localhost:8080 \
bash scripts/check-phase37-4-6-road-route-section-answer.sh
```

预期：

```text
1. ROAD_SECTION 回答包含“当前路段专项分析”；
2. ROAD_ROUTE 回答包含“当前路线专项分析”；
3. 工具调用包含 gis.queryAssessmentResults；
4. 工具调用包含 gis.queryDiseases；
5. knowledge.retrieve 正常调用；
6. 回答能结合路线/路段属性、病害分布和评定结果给出养护建议。
```
