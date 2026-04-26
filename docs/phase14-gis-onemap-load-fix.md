# 阶段十四：/gis/one-map 地图数据加载修复说明

## 1. 问题

当前主分支中，前端多个关键文件为空或被压缩成单行，导致 `/gis/one-map` 无法正常构建或无法加载地图数据。

主要问题：

```text
1. OneMap.vue 为空；
2. MapToolbar.vue、LayerDrawer.vue 等组件为空；
3. main.ts、gis.ts、request.ts、geojson.ts、leafletStyle.ts 被压成单行，TypeScript 语法失效；
4. /gis/one-map 页面无法初始化 Leaflet 图层；
5. GIS 接口请求函数类型缺失。
```

## 2. 修复内容

```text
1. 恢复 App.vue / main.ts / router；
2. 修复 request.ts；
3. 修复 api/gis.ts；
4. 修复 api/agent.ts；
5. 修复 geojson.ts；
6. 修复 leafletStyle.ts；
7. 重写 OneMap.vue；
8. 补齐 MapToolbar、MapFloatTools、LayerDrawer、LegendPopover、ObjectDetailDrawer、MapStatisticsBar、AgentChatFloat；
9. 页面默认加载 G210 / 2026 / MQI；
10. 自动请求路线、路段、病害、评定专题图层；
11. 修复全图定位。
```

## 3. 验证

```bash
cd srmp-web-ui
npm install
npm run build
npm run dev
```

访问：

```text
http://localhost:5173/gis/one-map
```

后端接口验证：

```text
GET /api/gis/road-routes?routeCode=G210
GET /api/gis/road-sections?routeCode=G210
GET /api/gis/diseases?routeCode=G210
GET /api/gis/assessment-results?routeCode=G210&year=2026&indexCode=MQI
```
