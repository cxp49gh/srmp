# Phase37.4.2：地图对象专病种回答生成增强

## 1. 问题背景

当前一张图 AI 已经能够：

```text
1. 正确传入地图对象上下文；
2. 调用 knowledge.retrieve；
3. 命中正确知识来源；
4. 展示回答依据；
5. vectorUsed=true。
```

但用户点击地图对象后，回答仍可能过于泛化。

例如点击：

```text
G210 K78.132-K78.134
病害：沉陷
严重程度：HEAVY
数量：5.74m2
```

系统虽然命中了：

```text
05-车辙与沉陷处置指南
```

但回答只给出：

```text
建议结合当前对象或区域的病害、评定结果和知识库资料开展现场复核...
```

这对业务用户来说不够可执行。

---

## 2. 本阶段目标

让 AI 从：

```text
查到资料
```

升级为：

```text
基于当前地图对象 + 知识库资料，输出具体、结构化、可执行的病害处置建议
```

---

## 3. 修复内容

### 3.1 新增 MapObjectDiseaseAdviceEnhancer

新增：

```text
srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/service/impl/MapObjectDiseaseAdviceEnhancer.java
```

能力：

```text
1. 识别当前地图对象病害类型；
2. 识别 severity / 桩号 / 数量 / 单位；
3. 按病种生成专项处置建议；
4. 支持沉陷、车辙、坑槽、修补损坏、裂缝；
5. 若回答过于泛化，自动追加“当前对象专项处置建议”。
```

### 3.2 沉陷类专项建议

当对象为：

```text
diseaseName=沉陷
diseaseType=SUBSIDENCE
severity=HEAVY
```

回答会补充：

```text
1. 重度沉陷风险判断；
2. 基层 / 路基 / 排水 / 含水软化 / 不均匀沉降成因判断；
3. 沉陷深度、范围、基层稳定性和排水条件复核；
4. 局部铣刨、找平、重新摊铺；
5. 基层处理、排水处理、局部结构修复或路基加固；
6. P1 优先处置建议。
```

### 3.3 限制 solution.generateDraft 自动调用

之前：

```text
“分析当前对象，给出处置建议”
```

会被识别为 `SOLUTION_GENERATE`，从而调用 `solution.generateDraft`。

本阶段调整为：

```text
只有用户明确说：
- 生成方案
- 方案草稿
- 保存方案
- 保存为方案任务
- 生成任务
- 生成养护方案

才调用 solution.generateDraft。
```

普通“给出处置建议”只做分析，不自动生成方案草稿。

### 3.4 Prompt 增强

新增要求：

```text
1. 必须优先吸收 Top1 参考资料中的处置工艺、成因判断和复核要点；
2. 病害对象应按：主要问题、成因判断、现场复核、处置建议、优先级、参考依据 组织回答。
```

---

## 4. 应用方式

```bash
unzip srmp-phase37-4-2-map-object-disease-answer-quality.zip -d /tmp/phase37-4-2
cp -r /tmp/phase37-4-2/srmp-phase37-4-2-map-object-disease-answer-quality/* /path/to/srmp/

cd /path/to/srmp
chmod +x scripts/apply-phase37-4-2-map-object-disease-answer.sh
chmod +x scripts/check-phase37-4-2-map-object-disease-answer.sh

bash scripts/apply-phase37-4-2-map-object-disease-answer.sh

mvn clean package -DskipTests
npm --prefix srmp-web-ui run build
```

---

## 5. 验收

```bash
BASE_URL=http://localhost:8080 \
bash scripts/check-phase37-4-2-map-object-disease-answer.sh
```

预期：

```text
1. answer 包含“当前对象专项处置建议”；
2. 沉陷对象回答包含路基 / 基层 / 排水 / 结构修复等建议；
3. 不再出现“当前对象属于裂缝类病害”；
4. 普通处置建议不自动调用 solution.generateDraft；
5. knowledge.retrieve 正常调用。
```

---

## 6. 用户体验变化

修复前：

```text
建议结合当前对象或区域的病害、评定结果和知识库资料开展现场复核...
```

修复后：

```text
当前对象为 G210 K78.132-K78.134 重度沉陷，面积约 5.74m2。
建议优先级 P1。
需复核沉陷深度、基层稳定性、路基局部变形、含水情况和排水条件。
若仅为面层局部沉陷，可局部铣刨、找平、重新摊铺并压实；
若基层松散或含水，应先处理基层和排水；
若涉及路基不均匀沉降，应进行局部结构修复或路基加固。
```
