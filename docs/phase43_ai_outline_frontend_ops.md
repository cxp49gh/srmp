# Phase43：AI 超时 + Outline 自动同步/Webhook 前端闭环

## 目标

基于最新前后端代码，完成三条闭环：

1. AI 请求超时配置真正生效，并在前端健康检查页展示。
2. Outline 自动同步配置、定时扫描、手动运行、运行记录可视化。
3. Outline Webhook 可以在前端复制接入说明并做精准同步测试。

## 主要改动

### 后端

- `LlmProperties` 增加 `connectTimeoutMs/readTimeoutMs/curlMaxTimeSeconds/maxTokens/temperature`。
- `OpenAiCompatibleLlmClient` 使用上述配置，不再写死 15s/60s/60s。
- `/api/ai/llm/health` 诊断返回超时、maxTokens、temperature。
- `OutlineProperties` 增加 Outline API 连接/读取超时。
- `OutlineSyncServiceImpl` 使用带超时的 `RestTemplate`。
- `OutlineSyncServiceImpl.sync()` 支持 `documentIds`，Webhook 触发时只同步指定文档。
- `OutlineAutoSyncController.webhook()` 支持 `X-Outline-Webhook-Secret`、`X-Webhook-Secret`、`Authorization: Bearer`。
- `OutlineAutoSyncServiceImpl` 增加运行锁，避免定时、手动、Webhook 同时重复执行同一配置。

### 前端

- `request.ts` 增加长任务请求实例，默认 300s。
- `AiHealthPage.vue` 增加 LLM 健康、超时、maxTokens、temperature、探测按钮。
- `OutlineAutoSyncPage.vue` 重做为运维闭环页：统计卡片、配置表单、配置列表、运行记录、Webhook 接入说明、Webhook 测试。
- 侧边栏增加 `Outline 自动同步` 入口。

## 推荐配置

```yaml
srmp:
  llm:
    connect-timeout-ms: 15000
    read-timeout-ms: 300000
    curl-max-time-seconds: 300
    max-tokens: 2400
    temperature: 0.2
  outline:
    connect-timeout-ms: 10000
    read-timeout-ms: 60000
```

```env
VITE_AI_TIMEOUT=300000
VITE_LONG_TIMEOUT=300000
```

## 验证

```bash
curl "http://localhost:8080/api/ai/llm/health?probe=true"
curl "http://localhost:8080/api/outline/knowledge-stats"
curl "http://localhost:8080/api/outline/auto-sync/configs"
```

前端访问：

- `/agent/ai-health`
- `/agent/outline-auto-sync`
