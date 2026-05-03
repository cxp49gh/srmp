# Phase44 AI / Outline 前端低冲突收口说明

本阶段不再整体替换 `OutlineAutoSyncPage.vue`、`router/index.ts`、`request.ts` 等大文件，而是通过 `scripts/apply_phase44_frontend_ops.py` 做幂等增强。

## 解决的问题

1. 避免前端实现包与最新仓库已有 Outline 自动同步页面产生大面积冲突。
2. 确认 `longRequest`、`VITE_AI_TIMEOUT`、`VITE_LONG_TIMEOUT` 生效，避免 AI/Outline 长任务前端提前超时。
3. Webhook 地址支持 `VITE_WEBHOOK_BASE_URL`，避免前端运行在 `5173` 时复制出无法被 Outline 服务访问的地址。
4. Clipboard API 不可用时增加 textarea fallback，复制按钮在非 HTTPS/部分浏览器下也可用。

## 使用方式

```bash
cd srmp
python3 scripts/apply_phase44_frontend_ops.py
cd srmp-web-ui
npm run build
```

## 推荐环境变量

```env
VITE_API_TIMEOUT=60000
VITE_AI_TIMEOUT=300000
VITE_LONG_TIMEOUT=300000
VITE_WEBHOOK_BASE_URL=http://localhost:8080
```
