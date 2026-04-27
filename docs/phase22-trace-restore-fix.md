# AI Trace 不写入修复说明

## 1. 问题

现象：

```text
新发起 AI 问答后，ai_trace_log 没有新增记录。
```

常见原因：

```text
1. 表名写错：正确表名是 ai_trace_log，不是 ai_track_log；
2. phase22_ai_trace_monitor.sql 没有执行；
3. 后续 answer-source-fix 覆盖了 AgentChatServiceImpl，导致 AiTraceService.save(trace) 丢失；
4. aiTraceService.save(trace) 写入失败，但只在日志中 warn。
```

## 2. 本补丁修复

合并：

```text
answerSource / answerMeta
AI trace 保存
data.trace 返回
[AI-CHAT] 日志
```

核心修复点：

```text
AgentChatServiceImpl 重新注入 AiTraceService
finally 中执行 aiTraceService.save(trace)
response.data.trace 始终返回
```

## 3. 检查 SQL

执行：

```bash
./scripts/init-ai-trace-db.sh
```

或：

```bash
psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/phase22_ai_trace_monitor.sql
```

确认表：

```sql
SELECT COUNT(*) FROM ai_trace_log;
SELECT COUNT(*) FROM ai_trace_step;
```

## 4. 验证

```bash
chmod +x scripts/*.sh
./scripts/check-ai-trace-write.sh
```

或者手动：

```bash
curl -s -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: default" \
  -d '{
    "message": "分析 G210 2026 年路况",
    "context": {"routeCode":"G210","year":2026},
    "options": {"useBusinessData":true,"useKnowledge":true,"useOutline":false,"topK":5}
  }'
```

响应中应包含：

```text
data.trace.traceId
data.answerMeta
```

然后查询：

```sql
SELECT trace_id, status, mode, total_cost_ms, fallback, created_at
FROM ai_trace_log
ORDER BY created_at DESC
LIMIT 10;
```

## 5. 日志

后端应看到：

```text
[AI-CHAT] start traceId=...
[AI-CHAT] finish traceId=... status=... mode=... fallback=... totalCostMs=...
```

如果写库失败，会看到：

```text
[AI-CHAT] save trace failed traceId=... error=...
```