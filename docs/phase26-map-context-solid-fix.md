# 阶段二十六二次修复：地图对象 AI 上下文闭环真正生效

## 定位

最新代码中：

1. `OneMap.vue` 没有把 `:map-object` 传给 `AgentChatFloat`；
2. `AgentChatFloat.vue` 发送请求时 `props.mapObject` 可能是 `undefined`；
3. `AgentChatServiceImpl` 只是回显 `mapObject`，没有注入 `MapObjectContextService` 查询真实业务对象，也没有把地图上下文拼入 RAG prompt。

## 修复

1. 新增 `MapObjectContextServiceImpl`；
2. 新增 `patch-phase26-map-context-solid-fix.py`；
3. 后端注入 `MapObjectContextService`；
4. 后端解析 `request.mapObject / context.mapObject / context.selectedMapObject / context.selected`；
5. 后端将【当前地图选中对象】拼入 `businessMarkdown`；
6. 后端 answer 添加【基于当前地图对象】前缀；
7. 前端 OneMap 显式构造并传递 `selectedMapObject`；
8. 前端 AgentChatFloat 展示“当前地图上下文”。

## 应用

```bash
git apply srmp-phase26-map-context-solid-fix.patch
chmod +x scripts/*.sh
./scripts/apply-phase26-map-context-solid-fix.sh
mvn clean package -DskipTests
```

## 验收

```bash
./scripts/check-map-context-solid-fix.sh
```

应满足：

```text
data.mapObjectUsed = true
data.mapObjectContext 包含 当前地图选中对象
answer 包含 【基于当前地图对象】
```
