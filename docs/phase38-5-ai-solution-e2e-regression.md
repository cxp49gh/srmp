# Phase38.5：AI 方案闭环工程化收口与全链路回归

## 1. 阶段目标

当前主分支已经完成一张图 AI 对象专项分析、RAG 检索、LLM Trace 诊断、方案草稿生成、方案任务保存、AI 上下文保存、版本恢复、Markdown V2 导出、兜底模板字段修复和来源去重。

Phase38.5 重点不是继续加业务功能，而是做工程化收口：

```text
1. 稳定 LlmClient 与 MapAiAgentServiceImpl 的诊断契约；
2. 保留 OpenAiCompatibleLlmClient 的 Proxy.NO_PROXY 与 curl fallback；
3. 补齐 LLM 诊断信息；
4. 清理 MapAiAgentServiceImpl 中旧 planTools 和冗余 enhancer 注入；
5. 建立 AI 分析 -> 方案任务 -> 导出 的总回归脚本。
```

## 2. 本次实现内容

### 2.1 LLM 契约收口

覆盖：

```text
srmp-agent/src/main/java/com/smartroad/srmp/agent/llm/LlmClient.java
```

统一接口：

```java
String chat(String systemPrompt, String userPrompt);
default boolean enabled();
default Map<String, Object> diagnostics();
```

### 2.2 OpenAiCompatibleLlmClient 增强

覆盖：

```text
srmp-agent/src/main/java/com/smartroad/srmp/agent/llm/OpenAiCompatibleLlmClient.java
```

保留：

```text
HttpURLConnection
Proxy.NO_PROXY
TLS/SSL 异常时 curl fallback
```

新增诊断：

```text
enabled
status
provider
model
baseUrl
transport
finishReason
responseId
rawResponsePreview
choicePreview
promptChars
answerChars
errorType
errorMessage
costMs
```

### 2.3 MapAiAgentServiceImpl 清理

新增脚本：

```text
scripts/patch-phase38-5-map-agent-cleanup.py
```

清理：

```text
1. 旧 planTools 方法；
2. 旧 useKnowledge 方法；
3. 已由 MapAiAnswerEnhancerRegistry 接管的直接 enhancer 注入。
```

### 2.4 回归脚本

新增：

```text
scripts/check-phase38-5-ai-solution-e2e-regression.sh
scripts/check-phase38-5-structure.sh
scripts/check-phase38-5-build.sh
```

覆盖：

```text
LLM health
DISEASE-坑槽
DISEASE-沉陷
ASSESSMENT_RESULT
ROAD_SECTION
ROAD_ROUTE
knowledge.retrieve
LLM Trace 诊断
ai-context 保存读取
status-timeline
Markdown V2 导出
兜底模板字段非空
系统兜底模板来源去重
```

### 2.5 方案任务详情 Tab 组件

新增：

```text
srmp-web-ui/src/views/agent/components/SolutionTaskDetailTabs.vue
```

后续页面可按以下结构整理：

```text
方案正文
AI 分析依据
引用来源
质量检查
版本历史
状态流转
```

## 3. 应用方式

```bash
unzip srmp-phase38-5-ai-solution-e2e-regression.zip -d /tmp/phase38-5
cp -r /tmp/phase38-5/srmp-phase38-5-ai-solution-e2e-regression/* /path/to/srmp/

cd /path/to/srmp

chmod +x scripts/apply-phase38-5-ai-solution-e2e-regression.sh
chmod +x scripts/check-phase38-5-structure.sh
chmod +x scripts/check-phase38-5-build.sh
chmod +x scripts/check-phase38-5-ai-solution-e2e-regression.sh

bash scripts/apply-phase38-5-ai-solution-e2e-regression.sh
```

## 4. 构建与验收

```bash
bash scripts/check-phase38-5-structure.sh
bash scripts/check-phase38-5-build.sh
```

全链路验收：

```bash
BASE_URL=http://localhost:8080 \
bash scripts/check-phase38-5-ai-solution-e2e-regression.sh
```

指定任务：

```bash
BASE_URL=http://localhost:8080 TASK_ID=<方案任务ID> \
bash scripts/check-phase38-5-ai-solution-e2e-regression.sh
```

## 5. 下一阶段建议

Phase38.5 通过后，再进入：

```text
Phase39：AI 方案任务审批流与报告导出增强
```
