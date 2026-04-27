# 阶段二十七补丁：一张图“当前地图上下文”前端强修复

## 1. 最新代码结论

基于 GitHub 最新代码检查，当前 `/gis/one-map` 的“当前地图上下文”不符合设计：

```text
1. OneMap.vue 只传 :context，没有传 :map-object；
2. OneMap.vue 没有 selectedMapObject；
3. openAiForSelected() 只打开 AI 浮窗，不自动分析；
4. AgentChatFloat.vue 虽然声明 mapObject，但父组件没有传；
5. AgentChatFloat.vue 请求体使用 props.mapObject，容易为空；
6. AgentChatFloat.vue 没有“当前地图上下文”卡片；
7. AgentChatFloat.vue 没有“重新分析当前对象 / 生成处置建议”按钮。
```

## 2. 本补丁目标

```text
点击地图对象
  ↓
OneMap 构造 selectedMapObject
  ↓
AgentChatFloat 显示“当前地图上下文”
  ↓
点击 AI分析此对象
  ↓
自动发送“分析当前地图选中对象...”
  ↓
请求体携带 mapObject
```

## 3. 应用方式

```bash
git apply srmp-phase27-onemap-context-frontend-fix.patch
chmod +x scripts/*.sh
./scripts/apply-phase27-onemap-context-frontend.sh
npm --prefix srmp-web-ui run build
```

## 4. 验收

```bash
./scripts/check-phase27-onemap-context-frontend.sh
```

手动验收：

```text
1. 打开 /gis/one-map；
2. 点击地图对象；
3. 点击“AI分析此对象”；
4. AI 浮窗显示“当前地图上下文”；
5. AI 浮窗显示“重新分析当前对象”；
6. AI 浮窗显示“生成处置建议”；
7. Network 中 /api/agent/chat 请求体包含 mapObject。
```

## 5. 注意

本补丁只修复前端链路。要让返回内容包含【基于当前地图对象】，后端仍需确保：

```text
1. AgentChatServiceImpl 把 mapObjectContext 拼进 prompt；
2. answer 前缀补充【基于当前地图对象】；
3. data.mapObjectUsed=true。
```
