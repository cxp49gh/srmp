# Phase38.1：大模型回答 SKIPPED 诊断与修复

## 1. 问题

AI Trace 中出现：

```text
大模型回答 SKIPPED
```

但用户已经配置了 RAG、Embedding、知识库，容易误以为“大模型没有被调用”。

## 2. 根因

原逻辑：

```java
String answer = llmClient.chat(systemPrompt(), prompt);
if (answer == null || answer.trim().isEmpty()) {
    answer = localAnswer(...);
    llmTimer.skipped(...);
}
```

而 `OpenAiCompatibleLlmClient` 中：

```java
catch (Exception e) {
    return null;
}
```

这导致以下情况都会被统一显示为 `SKIPPED`：

```text
1. srmp.llm.api-key 未配置；
2. srmp.llm.base-url 未配置；
3. 模型名错误；
4. API Key 无效；
5. 网络/代理/TLS 错误；
6. 服务端返回格式异常；
7. LLM 真实返回为空。
```

其中只有“未启用”应该是 `SKIPPED`；其他已启用但调用失败的情况应该是 `FAILED`，并显示错误原因。

## 3. 修复

### 3.1 LlmClient 增加诊断能力

```java
boolean enabled();
Map<String, Object> diagnostics();
```

### 3.2 OpenAiCompatibleLlmClient 不再吞掉错误

保留兼容的 `String chat(...)` 返回值，但会记录诊断信息：

```text
status
enabled
provider
model
baseUrl
httpStatus
errorType
errorMessage
costMs
answerChars
```

### 3.3 MapAiAgentServiceImpl 修复 trace 状态

规则：

```text
LLM 未启用 -> SKIPPED
LLM 已启用但调用失败/返回空 -> FAILED
LLM 成功返回 -> SUCCESS
```

即使 LLM 失败，仍然会走本地 Agent 回答兜底，不影响页面输出。

### 3.4 新增健康检查接口

```http
GET /api/ai/llm/health?probe=true
```

用于验证 LLM 配置和真实连通性。

## 4. 推荐配置

```yaml
srmp:
  llm:
    provider: openai-compatible
    base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    api-key: ${DASHSCOPE_API_KEY:}
    model: qwen-plus
```

注意：Embedding 模型是 `text-embedding-v4`，LLM 聊天模型应使用 `qwen-plus`、`qwen-turbo` 等对话模型，不能使用 embedding 模型。

## 5. 应用

```bash
unzip srmp-phase38-1-llm-skipped-diagnostics-fix.zip -d /tmp/phase38-1
cp -r /tmp/phase38-1/srmp-phase38-1-llm-skipped-diagnostics-fix/* /path/to/srmp/

cd /path/to/srmp
chmod +x scripts/apply-phase38-1-llm-skipped-diagnostics-fix.sh
chmod +x scripts/check-phase38-1-llm-skipped-diagnostics-fix.sh

bash scripts/apply-phase38-1-llm-skipped-diagnostics-fix.sh

mvn clean package -DskipTests
npm --prefix srmp-web-ui run build
```

## 6. 验收

```bash
BASE_URL=http://localhost:8080 \
bash scripts/check-phase38-1-llm-skipped-diagnostics-fix.sh
```

预期：

```text
1. /api/ai/llm/health?probe=true 能看到 enabled/status/errorMessage；
2. 如果 LLM 配置正确，llm_answer 为 SUCCESS；
3. 如果 LLM 已启用但调用失败，llm_answer 为 FAILED，并显示 errorType/errorMessage；
4. 只有 LLM 未启用时才显示 SKIPPED。
```
