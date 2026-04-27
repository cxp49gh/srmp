# 阶段二十二：AI 调用链路监控与超时治理

## 1. 目标

解决 AI 问答出现 `timeout of 30000ms exceeded` 但不知道卡在哪里的问题。

本阶段增加：

```
1. AI traceId
2. AI 每一步耗时
3. AI trace 日志表
4. AI trace 查询接口
5. 前端 AI 请求 120 秒超时
6. 前端 AI 调用监控页面
7. 后端日志输出 [AI-CHAT]
```

## 2. 初始化

```bash
./scripts/init-ai-trace-db.sh
```

或：

```bash
psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/phase22_ai_trace_monitor.sql
```

## 3. 新增接口

```
GET /api/ai/traces
GET /api/ai/traces/{traceId}
GET /api/ai/traces/{traceId}/steps
```

## 4. 前端页面

```
http://localhost:5173/agent/ai-traces
```

## 5. 日志查看

```bash
tail -f app.log | grep AI-CHAT
```

Docker：

```bash
docker logs -f 你的_srmp_admin_容器名 | grep AI-CHAT
```

## 6. 超时说明

普通接口仍使用 30 秒超时，AI 接口使用 `VITE_AI_TIMEOUT`，默认 120 秒。
