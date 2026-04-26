# 阶段十一：GIS 一张图页面布局优化说明

## 1. 优化目标

根据使用反馈，本次只调整前端 GIS 页面布局和全图按钮行为，不改动后端接口。

## 2. 主要变化

```text
1. 左侧统一整合：
   - 图层控制
   - 对象详情
   - 路况等级图例

2. 右侧只保留：
   - AI 问答面板

3. 地图中间区域释放更多可视空间。

4. 修复"全图"按钮无效：
   - 调用 map.invalidateSize()
   - 汇总所有可见 GeoJSON 图层 bounds
   - bounds 有效时 fitBounds
   - 无图层时给出提示
```

## 3. 修改文件

```text
srmp-web-ui/src/views/gis/OneMap.vue
```

## 4. 验证方式

```bash
cd srmp-web-ui
npm run build
npm run dev
```

打开：

```text
http://localhost:5173
```

验证：

```text
1. 左侧能看到图层控制、对象详情、路况等级；
2. 右侧只显示 AI 问答；
3. 点击"全图"按钮后地图自动定位到 G210 可见图层；
4. 取消所有图层后点击"全图"，出现提示；
5. 点击地图对象后，左侧对象详情更新。
```