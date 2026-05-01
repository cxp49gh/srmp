# Phase38.2：MapAgent LLM 空响应诊断与精简重试修复

## 1. 问题现象

健康检查正常：

```http
GET /api/ai/llm/health?probe=true
```

返回：

```json
{
  "enabled": true,
  "available": true,
  "probeAnswerPreview": "OK"
}
```

但一张图 AI 的 Trace 中仍出现：

```text
大模型回答 FAILED / LLM 返回为空
```

这说明：

```text
LLM 基础配置可用；
短 prompt 可正常返回；
MapAgent 的大 prompt 调用返回空。
```

所以问题不是“LLM 不通”，而是“MapAgent 这次请求返回空，需要诊断并重试”。

## 2. 常见原因

```text
1. MapAgent prompt 太长；
2. 服务端返回 choices 但 content 为空；
3. finish_reason 是 length/content_filter/stop 等；
4. 返回结构不完全是 choices[0].message.content；
5. 大 prompt 触发模型安全策略或服务端策略；
6. 未记录 raw response，导致无法定位。
```

## 3. 本次修复

### 3.1 OpenAiCompatibleLlmClient 诊断增强

新增诊断字段：

```text
promptChars
systemPromptChars
userPromptChars
httpStatus
responseId
finishReason
rawResponsePreview
choicePreview
answerChars
```

并支持兼容解析：

```text
message.content 字符串
message.content 数组
message.text
message.reasoning_content
```

### 3.2 MapAgent 空响应自动精简重试

第一次大 prompt 返回空时：

```text
1. 保留 firstAttempt 诊断；
2. 构造 compactPrompt；
3. 只保留地图对象、Top3 知识、工具摘要；
4. 重新调用 LLM；
5. 若成功，llm_answer = SUCCESS，data.retriedWithCompactPrompt=true；
6. 若仍失败，llm_answer = FAILED，并带 firstAttempt / rawResponsePreview / finishReason。
```

### 3.3 兜底不影响用户回答

即使 LLM 失败，仍会走本地 Agent 回答兜底，页面不会空白。

## 4. 应用

```bash
unzip srmp-phase38-2-mapagent-llm-empty-retry-fix.zip -d /tmp/phase38-2
cp -r /tmp/phase38-2/srmp-phase38-2-mapagent-llm-empty-retry-fix/* /path/to/srmp/

cd /path/to/srmp
chmod +x scripts/apply-phase38-2-mapagent-llm-empty-retry-fix.sh
chmod +x scripts/check-phase38-2-mapagent-llm-empty-retry-fix.sh

bash scripts/apply-phase38-2-mapagent-llm-empty-retry-fix.sh

mvn clean package -DskipTests
npm --prefix srmp-web-ui run build
```

## 5. 验收

```bash
BASE_URL=http://localhost:8080 \
bash scripts/check-phase38-2-mapagent-llm-empty-retry-fix.sh
```

预期：

```text
1. health probe 仍 available=true；
2. MapAgent llm_answer 如果成功，显示 SUCCESS；
3. 若第一次失败但精简重试成功，显示 SUCCESS 且 retriedWithCompactPrompt=true；
4. 若仍失败，显示 FAILED，并包含 firstAttempt、finishReason、rawResponsePreview 等诊断；
5. 不再只有一句“LLM 返回为空”。
```
