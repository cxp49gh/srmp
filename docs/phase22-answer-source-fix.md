# AI 问答 answer 来源标识修补说明

## 1. 问题

前端看到 `answer` 有内容，但调用链显示"大模型调用失败"，容易误解：

```
answer 到底是不是大模型返回？
```

实际情况可能是：

```
1. 大模型成功：answer 来自 LLM；
2. 大模型失败/返回空：answer 来自本地业务分析或知识库降级结果。
```

## 2. 新增字段

`/api/agent/chat` 的 `data` 中新增：

```json
{
  "answerSource": "LLM",
  "answerSourceLabel": "大模型返回",
  "llmSuccess": true,
  "fallback": false,
  "fallbackReason": null,
  "answerNotice": "本次 answer 由大模型成功生成。",
  "answerMeta": {
    "answerSource": "LLM",
    "answerSourceLabel": "大模型返回",
    "llmSuccess": true,
    "fallback": false,
    "fallbackReason": null,
    "mode": "HYBRID_RAG_LLM",
    "notice": "本次 answer 由大模型成功生成。"
  }
}
```

## 3. 判断规则

只有同时满足：

```
answerSource = LLM
llmSuccess = true
fallback = false
```

才说明 `answer` 是大模型成功返回。

## 4. 前端展示建议

新增组件：

```
srmp-web-ui/src/views/agent/components/AnswerSourceAlert.vue
```

在 AI 问答结果上方使用：

```vue
<AnswerSourceAlert :meta="result.data?.answerMeta || result.data" />
```