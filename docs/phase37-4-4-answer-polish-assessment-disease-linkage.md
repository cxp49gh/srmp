# Phase37.4.4：AI 回答展示收口与评定单元病害联动

## 1. 背景

Phase37.4.3 后，`ASSESSMENT_RESULT` 对象已经能输出专项分析，包含 MQI/PQI/PCI、主要短板、成因判断、复核重点和养护建议。

但从用户体验看，还有几个收口点：

```text
1. 前置泛化建议仍然存在；
2. 正文标题显示为 ####，聊天框不够友好；
3. 正文参考依据和前端“回答依据”面板重复；
4. 评定结果对象只查询了相邻评定结果，没有联动该单元内病害；
5. 回答仍偏“建议排查”，没有充分利用单元内病害明细。
```

本阶段目标是让回答更像产品化输出，而不是调试日志式输出。

---

## 2. 本次修复内容

### 2.1 新增 MapAiAnswerPolisher

新增：

```text
srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/service/impl/MapAiAnswerPolisher.java
```

能力：

```text
1. 当回答包含“当前评定单元专项分析”或“当前对象专项处置建议”时，移除前置泛化建议；
2. 将 #### 1. 主要问题 等标题转成 一、主要问题；
3. 移除正文末尾“参考依据/参考资料”，完整 sources 交由“回答依据”面板展示；
4. 清理多余空行。
```

### 2.2 新增 gis.queryDiseasesByStakeRange 工具

新增：

```text
srmp-agent/src/main/java/com/smartroad/srmp/agent/tool/impl/MapDiseaseStakeRangeTool.java
```

能力：

```text
1. 对 ASSESSMENT_RESULT 对象读取 startStake / endStake；
2. 查询 disease_record 中与该桩号范围重叠的病害；
3. 汇总病害类型、严重程度、数量；
4. 工具名：gis.queryDiseasesByStakeRange。
```

### 2.3 AssessmentResultAdviceEnhancer 增强

替换增强版：

```text
srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/service/impl/AssessmentResultAdviceEnhancer.java
```

增强点：

```text
1. 使用中文序号标题；
2. 不再在正文重复输出参考依据；
3. 读取 gis.queryDiseasesByStakeRange 的结果；
4. 如果单元内存在病害，回答会说明：
   - 单元内病害数量；
   - 主要病害类型；
   - 严重程度分布；
   - 是否建议从单点处置升级为小区间综合处置。
```

### 2.4 MapAiAgentServiceImpl 接入

通过脚本自动 patch：

```text
scripts/patch-phase37-4-4-answer-polish-assessment-disease-linkage.py
```

接入点：

```text
1. 注入 MapAiAnswerPolisher；
2. answer 生成后调用 mapAiAnswerPolisher.polish(answer)；
3. ASSESSMENT_RESULT 工具规划增加 gis.queryDiseasesByStakeRange。
```

---

## 3. 应用方式

```bash
unzip srmp-phase37-4-4-answer-polish-assessment-disease-linkage.zip -d /tmp/phase37-4-4
cp -r /tmp/phase37-4-4/srmp-phase37-4-4-answer-polish-assessment-disease-linkage/* /path/to/srmp/

cd /path/to/srmp
chmod +x scripts/apply-phase37-4-4-answer-polish-assessment-disease-linkage.sh
chmod +x scripts/check-phase37-4-4-answer-polish-assessment-disease-linkage.sh

bash scripts/apply-phase37-4-4-answer-polish-assessment-disease-linkage.sh

mvn clean package -DskipTests
npm --prefix srmp-web-ui run build
```

---

## 4. 验收

```bash
BASE_URL=http://localhost:8080 \
bash scripts/check-phase37-4-4-answer-polish-assessment-disease-linkage.sh
```

预期：

```text
1. answer 包含“当前评定单元专项分析”；
2. answer 不再包含前置泛化建议；
3. answer 不再包含 ####；
4. answer 正文不再重复输出“参考依据”；
5. 工具调用包含 gis.queryDiseasesByStakeRange；
6. 工具调用包含 knowledge.retrieve；
7. 回答能结合单元内病害情况进行判断。
```

---

## 5. 用户体验变化

修复前：

```text
建议结合当前对象或区域的病害、评定结果和知识库资料开展现场复核...
#### 1. 主要问题
...
#### 参考依据
...
```

修复后：

```text
当前评定单元专项分析

一、主要问题
...
二、成因判断
...
三、现场复核重点
...
四、养护处置建议
...
```

完整参考资料、工具调用、vectorUsed、rewrittenQuery 等信息统一通过“回答依据”面板展示。
