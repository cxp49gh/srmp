# 阶段十二：GIS 一张图方案 A 轻量化布局说明

## 1. 布局目标

采用方案 A：

```text
顶部：轻量查询工具条
左侧：图层 / 图例 / 全图悬浮按钮
中间：地图最大化
底部：统计小卡片
右下：AI 悬浮按钮
底部抽屉：对象详情
```

目标是让地图尽量干净，所有组件默认小巧、可折叠、按需展开。

## 2. 新增组件路径

```text
srmp-web-ui/src/views/gis/components/MapFloatTools.vue
srmp-web-ui/src/views/gis/components/LayerDrawer.vue
srmp-web-ui/src/views/gis/components/LegendPopover.vue
srmp-web-ui/src/views/gis/components/ObjectDetailDrawer.vue
srmp-web-ui/src/views/gis/components/MapStatisticsBar.vue
srmp-web-ui/src/views/gis/components/AgentChatFloat.vue
```

## 3. 重写页面

```text
srmp-web-ui/src/views/gis/OneMap.vue
```

## 4. 交互变化

```text
1. 图层控制默认收起，点击"层"按钮展开；
2. 图例默认收起，点击"例"按钮展开；
3. AI 默认只显示右下角圆形按钮；
4. 点击地图对象后，从底部弹出对象详情；
5. 底部统计条支持折叠；
6. 全图按钮会基于所有可见 GeoJSON 图层重新计算 bounds。
```
