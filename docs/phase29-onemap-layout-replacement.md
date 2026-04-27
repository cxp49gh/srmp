# Phase 29 OneMap 布局优化完整替换文件

基于 GitHub main（3d946e0）的一张图页面现状，当前问题主要是：

1. `OneMap.vue` 已有 `selectedMapObject`，但 `AgentChatFloat` 实际传入的是 `selectedDetail`。
2. `selectedMapObject` 没有合并 `selectedFeatureProperties`，详情接口覆盖后可能丢病害名、桩号、评分等原始属性。
3. 图层控制与路况等级图例分离，展开后容易相互遮挡。
4. AI 浮窗偏大、位置靠上，和地图主视图区冲突。
5. 统计条在 AI 浮窗打开时仍占用中下方空间。
6. AI 消息不支持基本 Markdown 显示，报告类输出可读性差。

## 替换文件

```text
srmp-web-ui/src/views/gis/OneMap.vue
srmp-web-ui/src/views/gis/components/AgentChatFloat.vue
srmp-web-ui/src/views/gis/components/LayerDrawer.vue
srmp-web-ui/src/views/gis/components/LegendPopover.vue
srmp-web-ui/src/views/gis/components/MapStatisticsBar.vue
srmp-web-ui/src/api/agent.ts
```

## 主要调整

```text
1. 左侧图层控制中内嵌路况等级，避免图例覆盖图层面板。
2. 单独点击“例”时，只有图层面板关闭时才显示独立图例。
3. AI 面板右侧固定，宽度控制在 430px，最大高度随视口自适应。
4. 统计条在 AI 打开时自动收缩可用宽度，小屏隐藏。
5. OneMap 使用 selectedMapObject 传给 AgentChatFloat。
6. selectedMapObject 合并原始 feature.properties 与详情接口返回数据。
7. AI 浮窗顶部上下文优先显示当前对象，而不是固定显示 G210。
8. AI 消息增加基础 Markdown 渲染能力。
```

## 使用方式

```bash
unzip srmp-phase29-onemap-layout-replacement.zip -d /tmp/srmp-phase29-layout
cp -r /tmp/srmp-phase29-layout/srmp-phase29-onemap-layout-replacement/* /path/to/srmp/

cd /path/to/srmp
npm --prefix srmp-web-ui run build
```

建议替换前新建分支：

```bash
git checkout -b phase29-onemap-layout
```
