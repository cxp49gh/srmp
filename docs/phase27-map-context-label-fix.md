# 阶段二十七修补：当前地图上下文标签始终显示 G210

## 1. 问题

点击病害、评定结果等对象后，AI 浮窗里的“当前地图上下文”一直显示 `G210`。

## 2. 根因

最新前端代码中：

```text
1. OneMap.vue 的 agentContext 只有 query 和 selected；
2. query.routeCode 默认就是 G210；
3. AgentChatFloat.vue 的 contextText 优先显示 query.routeCode；
4. 所有对象 properties 也都会带 routeCode=G210；
5. 点击病害后，前端没有把 diseaseName / diseaseType / severity / 桩号等对象特征作为上下文标签优先展示；
6. getObjectDetail 返回后可能覆盖 selectedDetail，导致原始 feature.properties 中的对象特征丢失。
```

所以标签一直显示 G210，并不代表选中了路线，而是前端标签逻辑太粗。

## 3. 修复内容

```text
1. OneMap.vue 新增 selectedFeatureProperties，保存点击时的原始 feature.properties；
2. selectedMapObject 合并 selectedFeatureProperties + selectedDetail；
3. loadObjectDetail 成功/失败后保留 objectType/objectId/id 和原始属性；
4. AgentChatFloat.vue 新增 mapContextLabel；
5. mapContextLabel 按对象类型优先展示：
   - 病害：病害｜病害名称｜严重程度｜路线｜桩号
   - 评定结果：评定结果｜路线｜桩号｜MQI/PCI
   - 评定单元：评定单元｜路线｜桩号｜单元编码
   - 路段：路段｜路段名称｜路线｜桩号
   - 路线：路线｜路线名称｜路线编码
6. 请求体 mapObject 使用 activeMapObject.value。
```

## 4. 应用方式

```bash
git apply srmp-phase27-map-context-label-fix.patch
chmod +x scripts/*.sh
./scripts/apply-phase27-map-context-label-fix.sh
npm --prefix srmp-web-ui run build
```

## 5. 验收

```bash
./scripts/check-phase27-map-context-label-fix.sh
```

手动验收：

```text
1. 打开 /gis/one-map；
2. 点击路线：当前地图上下文应显示“路线｜...”；
3. 点击病害：应显示“病害｜裂缝/坑槽/...｜严重程度｜G210｜K...”；
4. 点击评定结果：应显示“评定结果｜G210｜K...｜MQI ...”；
5. Network 中 /api/agent/chat 请求体的 mapObject 应包含 objectType/objectId/diseaseName 或 mqi 等对象字段。
```
