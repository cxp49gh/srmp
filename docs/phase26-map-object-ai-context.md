# 阶段二十六：地图选中对象作为 AI 上下文

## 1. 背景

最新 `OneMap.vue` 已经在地图点击时调用 `/api/gis/object-detail`，并把 `selectedDetail` 传给右侧 `AgentChatPanel`。但后端 AI 问答没有显式识别“当前地图对象”，用户问“这个路段为什么分低”时，AI 仍可能只按 routeCode/year 做泛化分析。

## 2. 本阶段目标

```text
1. 前端在地图点击后生成显式 mapObject；
2. 右侧 AI 面板展示当前地图上下文；
3. AI 请求携带 context.mapObject；
4. 后端按 objectType/objectId 查询真实业务详情；
5. RAG prompt 前置“当前地图选中对象”说明；
6. AI 返回 data.mapObjectUsed / data.mapObject / data.mapObjectContext；
7. 支持“这个/当前/该路段/该病害”等指代问题。
```

## 3. 支持对象类型

```text
ROAD_ROUTE
ROAD_SECTION
EVALUATION_UNIT
DISEASE
ASSESSMENT_RESULT
```

## 4. 验收

```bash
chmod +x scripts/*.sh
./scripts/check-map-ai-context.sh
```

前端验证：

```text
1. 打开 /gis/one-map；
2. 点击一个评定结果或病害；
3. 右侧 AI 面板显示“当前地图上下文”；
4. 输入“这个对象怎么处理？”；
5. AI 返回应包含【基于当前地图对象】提示；
6. 后端响应 data.mapObjectUsed=true。
```
