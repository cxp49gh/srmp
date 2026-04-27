# 阶段二十六修补：AI分析此对象自动触发

## 1. 问题

点击“AI分析此对象”时，只是打开 `AI 养护助手` 浮窗，用户还需要手动输入“分析当前对象”。

## 2. 修复目标

```text
点击 AI分析此对象
  ↓
打开 AI 养护助手
  ↓
自动带上当前地图对象上下文
  ↓
自动发送“分析当前地图选中对象”
  ↓
直接返回分析结果
```

## 3. 修复内容

```text
1. OneMap.vue 增加 pendingAiQuestion；
2. OneMap.vue 构造 selectedMapObject；
3. OneMap.vue 传递 :map-object 和 :auto-question 给 AgentChatFloat；
4. openAiForSelected() 不再只是打开浮窗，而是设置自动问题；
5. AgentChatFloat.vue 增加 autoQuestion prop；
6. AgentChatFloat.vue 监听 autoQuestion，自动 send()；
7. AgentChatFloat.vue 使用 activeMapObject，避免 props.mapObject 为空；
8. AgentChatFloat.vue 展示“当前地图上下文”。
```

## 4. 应用方式

```bash
git apply srmp-phase26-ai-analyze-auto-run.patch
chmod +x scripts/*.sh
./scripts/apply-phase26-ai-analyze-auto-run.sh
npm --prefix srmp-web-ui run build
```

## 5. 验收

```bash
./scripts/check-phase26-ai-analyze-auto-run.sh
```

前端验收：

```text
1. 打开 /gis/one-map；
2. 点击地图上的路线、路段、评定单元、病害或评定结果；
3. 点击详情面板里的“AI分析此对象”；
4. AI 养护助手弹出；
5. 自动出现用户消息“分析当前地图选中对象...”；
6. 自动返回 AI 分析结果。
```

如果弹窗打开但没有请求，请检查浏览器 Network 是否出现 `/api/agent/chat`。
