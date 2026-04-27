# 阶段二十五：GIS 图层接口与真实数据适配

## 1. 背景

最新数据库结构使用 PostGIS 字段：

```text
road_route.geom
road_section.geom
road_evaluation_unit.geom
road_evaluation_unit.center_point
disease_record.geom
```

但原 GIS 图层实现主要依赖业务 VO 中的 `geomGeoJson` 字段。演示数据直接按最新 schema 导入后，如果业务服务没有正确把 `geom` 转成 GeoJSON，就会出现：

```text
1. GIS 一张图图层为空；
2. 地图统计为 0/null；
3. 评定结果无法上图；
4. 点击对象详情接口不存在或 assessment objectType 不匹配。
```

## 2. 本阶段修复

```text
1. GIS 路线、路段、评定单元直接读取 PostGIS geom 并用 ST_AsGeoJSON 输出；
2. 病害图层直接读取 disease_record.geom；
3. 评定结果图层通过 assessment_result 关联 road_evaluation_unit.geom；
4. /api/gis/map-statistics 不再返回固定 0/null；
5. 增加 /api/gis/object-detail；
6. object-detail 支持 ASSESSMENT_RESULT；
7. 图层输出统一 objectType、routeCode、startStake、endStake、color 等属性；
8. 增加 check-gis-layers.sh 验收脚本。
```

## 3. 验收

```bash
chmod +x scripts/*.sh
./scripts/check-gis-layers.sh
```

自定义参数：

```bash
BASE_URL=http://localhost:8080 TENANT_ID=default ROUTE_CODE=G210 YEAR=2026 ./scripts/check-gis-layers.sh
```

## 4. 前端验证

访问：

```text
http://localhost:5173/gis/one-map
```

确认：

```text
1. 路线图层有数据；
2. 路段图层有数据；
3. 评定单元图层有数据；
4. 病害点/线图层有数据；
5. 评定结果专题图有颜色；
6. 点击对象后能展示详情；
7. 底部统计不再是 0/null。
```

## 5. 说明

本阶段尽量不改前端布局，优先保证 GIS 后端接口返回真实 GeoJSON。
下一阶段可继续做"地图选中对象作为 AI 上下文"。