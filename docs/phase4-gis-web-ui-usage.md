# 阶段四：GIS 一张图前端完善使用说明

本阶段新增一个独立前端工程：

```text
srmp-web-ui
```

技术栈：

```text
Vue 3 + Vite + TypeScript + Element Plus + Leaflet + ECharts + Axios
```

## 1. 新增能力

```text
1. GIS 一张图主页面
2. 左侧图层树
3. 顶部地图工具栏
4. 路线 / 路段 / 评定单元图层加载
5. 病害图层加载
6. 评定结果专题图加载
7. 点击地图对象查看详情
8. 地图范围统计面板
9. 图例面板
10. AI 智能问答浮窗
11. 按路线编号、年度、指标筛选图层
```

## 2. 启动方式

进入前端目录：

```bash
cd srmp-web-ui
npm install
npm run dev
```

默认访问：

```text
http://localhost:5173
```

默认后端地址：

```text
http://localhost:8080
```

可在 `.env.development` 修改：

```text
VITE_API_BASE_URL=http://localhost:8080
VITE_TENANT_ID=default
```

## 3. 后端依赖接口

本前端工程依赖以下后端接口：

```text
GET  /api/gis/layers
GET  /api/gis/road-routes
GET  /api/gis/road-sections
GET  /api/gis/evaluation-units
GET  /api/gis/diseases
GET  /api/gis/assessment-results
GET  /api/gis/object-detail
POST /api/gis/map-statistics
POST /api/agent/chat
```

如果某些接口暂未实现，前端会给出错误提示，但不会影响整体页面启动。

## 4. 推荐测试流程

```text
1. 启动后端 srmp-admin
2. 启动前端 srmp-web-ui
3. 打开 GIS 一张图
4. 输入 routeCode，例如 G210
5. 勾选路线、路段、评定单元图层
6. 勾选病害图层
7. 勾选评定结果图层
8. 点击地图对象查看右侧详情
9. 在 AI 问答框输入“分析当前路线病害情况”
```

## 5. 注意事项

1. Leaflet 默认使用 OpenStreetMap 瓦片，生产环境可替换为内网瓦片或天地图。
2. 所有请求默认带 `X-Tenant-Id` 请求头。
3. 评定专题图按 `properties.color` 渲染；如果后端未返回颜色，前端会按等级兜底渲染。
4. 病害图层支持 Point、LineString、Polygon，前端会根据 geometry 自动渲染。