# Phase37.4.3：评定结果对象专项回答增强

## 1. 问题背景

当用户在一张图中选中 `ASSESSMENT_RESULT` 对象时，系统能够正确传入：

```text
objectType=ASSESSMENT_RESULT
routeCode=G210
startStake=76.534
endStake=77.334
mqi=74.729
pqi=70.059
pci=70.525
grade=MEDIUM
```

知识库也能命中：

```text
09-框选区域养护分析规则
08-低分单元养护建议规则
10-养护方案编制规范示例
```

但最终回答仍然可能只有：

```text
建议结合当前对象或区域的病害、评定结果和知识库资料开展现场复核...
```

这说明问题不在 RAG 检索，而在回答合成层缺少“评定结果对象”的业务解释器。

---

## 2. 本阶段目标

让一张图 AI 能针对评定结果对象输出：

```text
1. 当前评定单元基本信息；
2. MQI / PQI / PCI 指标解读；
3. 主要短板判断；
4. 成因判断；
5. 现场复核重点；
6. 养护处置建议；
7. 优先级判断；
8. 参考依据。
```

---

## 3. 修复内容

### 3.1 新增 AssessmentResultAdviceEnhancer

新增：

```text
srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/service/impl/AssessmentResultAdviceEnhancer.java
```

能力：

```text
1. 识别 objectType=ASSESSMENT_RESULT；
2. 读取 routeCode、year、startStake、endStake；
3. 读取 mqi、pqi、pci、rqi、rdi、grade；
4. 判断 PQI/PCI 是否为主要短板；
5. 生成“当前评定单元专项分析”；
6. 将知识库来源和评定结果工具查询结果写入回答。
```

### 3.2 MapAiAgentServiceImpl 接入

通过脚本自动 patch：

```text
scripts/patch-phase37-4-3-assessment-result-answer.py
```

接入点：

```text
answer = assessmentResultAdviceEnhancer.enhance(...)
```

### 3.3 Prompt 增强

增加要求：

```text
若当前对象为评定结果，应显式分析 MQI/PQI/PCI、grade、主要短板、可能成因和养护处置建议。
```

---

## 4. 应用方式

```bash
unzip srmp-phase37-4-3-assessment-result-answer-quality.zip -d /tmp/phase37-4-3
cp -r /tmp/phase37-4-3/srmp-phase37-4-3-assessment-result-answer-quality/* /path/to/srmp/

cd /path/to/srmp
chmod +x scripts/apply-phase37-4-3-assessment-result-answer.sh
chmod +x scripts/check-phase37-4-3-assessment-result-answer.sh

bash scripts/apply-phase37-4-3-assessment-result-answer.sh

mvn clean package -DskipTests
npm --prefix srmp-web-ui run build
```

---

## 5. 验收

```bash
BASE_URL=http://localhost:8080 \
bash scripts/check-phase37-4-3-assessment-result-answer.sh
```

预期：

```text
1. answer 包含“当前评定单元专项分析”；
2. answer 包含 MQI / PQI / PCI；
3. answer 能指出 PQI/PCI 是主要短板；
4. answer 包含局部修补、封层、薄层罩面、中修、近期养护计划等建议；
5. 不会把 ASSESSMENT_RESULT 误判为 DISEASE；
6. knowledge.retrieve 正常调用。
```

---

## 6. 用户体验变化

修复后，针对：

```text
G210 K76.534-K77.334
MQI=74.729
PQI=70.059
PCI=70.525
grade=MEDIUM
```

AI 会补充类似：

```text
当前评定单元综合技术状况中等偏低。
PQI=70.059，说明路面使用性能偏弱；
PCI=70.525，说明路面损坏状况是主要短板之一。
建议重点排查裂缝、坑槽、修补损坏、沉陷、松散等破损类病害。
若病害点状分布，可局部修补；
若连续分布且 PQI/PCI 持续偏低，可考虑封层、薄层罩面、局部铣刨重铺或中修方案。
建议优先级为 P2，纳入近期养护计划。
```
