# 智路养护平台一期下一步实施计划文档

系统名称：智路养护平台  
英文名：SmartRoad Maintenance Platform  
简称：SRMP  
当前阶段：一期阶段一基础骨架已生成  
下一阶段：阶段二基础数据与道路资产落地  

---

# 1. 当前状态

目前已完成 **阶段一：基础骨架搭建**，包括：

```text
1. Maven 多模块后端工程骨架
2. Spring Boot 启动模块
3. PostgreSQL + PostGIS 配置
4. Redis 配置
5. MinIO 配置
6. 多租户上下文
7. 登录认证占位
8. 统一响应模型
9. 全局异常处理
10. GIS 接口占位
11. 大模型 Agent 接口占位
12. 数据库初始化脚本
```

阶段一工程包：

```text
srmp-phase1-skeleton.zip
```

---

# 2. 下一步总体目标

下一步建议进入：

```text
阶段二：基础数据与道路资产落地
```

阶段二核心目标是把 **GIS 一张图的数据底座** 先跑通。

重点不是先做复杂业务流程，而是优先完成：

```text
路线可维护
路段可维护
评定单元可维护
空间数据可入库
GIS 接口可返回 GeoJSON
地图可以展示道路资产
```

---

# 3. 下一步开发顺序

## 3.1 第一步：跑通基础骨架

解压工程后先验证后端服务可以正常启动。

### 启动基础依赖

```bash
docker compose up -d
```

### 编译工程

```bash
mvn clean package -DskipTests
```

### 启动后端

```bash
cd srmp-admin
mvn spring-boot:run
```

### 验证接口

```text
GET http://localhost:8080/api/health
```

期望返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "system": "SmartRoad Maintenance Platform",
    "status": "UP",
    "phase": "phase1-skeleton"
  }
}
```

---

## 3.2 第二步：初始化数据库

执行数据库初始化脚本：

```bash
psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/schema.sql
psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/init_dict.sql
psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/init_admin.sql
```

默认数据库信息：

```text
数据库：srmp
用户名：srmp
密码：srmp123
端口：5432
```

---

# 4. 阶段二建设内容

阶段二聚焦 **道路资产模块 + GIS 图层接口**。

## 4.1 核心对象

```text
road_route              路线
road_section            路段
road_evaluation_unit    评定单元
```

## 4.2 对应模块

```text
srmp-road-asset         道路资产模块
srmp-gis                GIS 一张图模块
srmp-base               基础字典模块
srmp-tenant             多租户模块
```

---

# 5. 道路资产模块开发内容

## 5.1 路线管理

### 功能

```text
1. 新增路线
2. 修改路线
3. 删除路线
4. 路线详情
5. 路线分页查询
6. 根据路线编号查询
7. 路线空间线形维护
```

### API

```text
POST   /api/road-routes/page
GET    /api/road-routes/{id}
POST   /api/road-routes
PUT    /api/road-routes/{id}
DELETE /api/road-routes/{id}
GET    /api/road-routes/by-code/{routeCode}
```

### 核心字段

| 字段 | 说明 |
|---|---|
| tenant_id | 租户 ID |
| route_code | 路线编号 |
| route_name | 路线名称 |
| route_type | 路线类型 |
| technical_grade | 技术等级 |
| start_stake | 起点桩号 |
| end_stake | 终点桩号 |
| length_km | 路线长度 |
| geom | 路线空间线形 |

---

## 5.2 路段管理

### 功能

```text
1. 新增路段
2. 修改路段
3. 删除路段
4. 路段详情
5. 路段分页查询
6. 按路线查询路段
7. 按桩号范围查询路段
```

### API

```text
POST   /api/road-sections/page
GET    /api/road-sections/{id}
POST   /api/road-sections
PUT    /api/road-sections/{id}
DELETE /api/road-sections/{id}
GET    /api/road-sections/by-route/{routeCode}
```

### 核心字段

| 字段 | 说明 |
|---|---|
| tenant_id | 租户 ID |
| route_id | 路线 ID |
| route_code | 路线编号 |
| section_code | 路段编码 |
| section_name | 路段名称 |
| direction | 方向 |
| start_stake | 起点桩号 |
| end_stake | 终点桩号 |
| pavement_type | 路面类型 |
| technical_grade | 技术等级 |
| geom | 路段空间线形 |

---

## 5.3 评定单元管理

### 功能

```text
1. 新增评定单元
2. 修改评定单元
3. 删除评定单元
4. 评定单元详情
5. 评定单元分页查询
6. 按路线查询评定单元
7. 按桩号定位评定单元
```

### API

```text
POST   /api/evaluation-units/page
GET    /api/evaluation-units/{id}
POST   /api/evaluation-units
PUT    /api/evaluation-units/{id}
DELETE /api/evaluation-units/{id}
GET    /api/evaluation-units/by-route/{routeCode}
GET    /api/road-assets/stake-location
```

### 核心字段

| 字段 | 说明 |
|---|---|
| tenant_id | 租户 ID |
| route_id | 路线 ID |
| section_id | 路段 ID |
| route_code | 路线编号 |
| unit_code | 评定单元编码 |
| direction | 方向 |
| lane_no | 车道编号 |
| start_stake | 起点桩号 |
| end_stake | 终点桩号 |
| length_m | 单元长度 |
| geom | 评定单元空间线形 |
| center_point | 中心点 |

---

# 6. GIS 图层接口开发内容

道路资产 CRUD 完成后，需要马上补齐 GIS 图层接口。

## 6.1 路线图层

```text
GET /api/gis/road-routes
```

返回路线 GeoJSON。

## 6.2 路段图层

```text
GET /api/gis/road-sections
```

返回路段 GeoJSON。

## 6.3 评定单元图层

```text
GET /api/gis/evaluation-units
```

返回评定单元 GeoJSON。

---

# 7. GIS GeoJSON 返回格式

所有 GIS 图层接口建议统一返回：

```json
{
  "type": "FeatureCollection",
  "features": [
    {
      "type": "Feature",
      "id": "xxx",
      "geometry": {
        "type": "LineString",
        "coordinates": [
          [106.630, 26.650],
          [106.720, 26.710]
        ]
      },
      "properties": {
        "objectType": "ROAD_ROUTE",
        "routeCode": "G210",
        "routeName": "G210示例路线",
        "startStake": 0.0,
        "endStake": 10.0,
        "lengthKm": 10.0
      }
    }
  ]
}
```

---

# 8. 阶段二最小闭环

阶段二建议先完成这个最小闭环：

```text
1. 手工新增路线
2. 手工新增路段
3. 手工新增评定单元
4. GIS 接口返回 GeoJSON
5. 前端地图展示路线
6. 前端地图展示路段
7. 前端地图展示评定单元
8. 点击地图对象查看属性
```

完成后，系统就具备 GIS 一张图最小雏形。

---

# 9. 阶段二后端代码清单

阶段二建议生成和补齐以下代码。

## 9.1 road_route

```text
RoadRoute.java
RoadRouteMapper.java
RoadRouteMapper.xml
RoadRouteService.java
RoadRouteServiceImpl.java
RoadRouteController.java
RoadRouteQueryDTO.java
RoadRouteSaveDTO.java
RoadRouteVO.java
```

## 9.2 road_section

```text
RoadSection.java
RoadSectionMapper.java
RoadSectionMapper.xml
RoadSectionService.java
RoadSectionServiceImpl.java
RoadSectionController.java
RoadSectionQueryDTO.java
RoadSectionSaveDTO.java
RoadSectionVO.java
```

## 9.3 road_evaluation_unit

```text
RoadEvaluationUnit.java
RoadEvaluationUnitMapper.java
RoadEvaluationUnitMapper.xml
RoadEvaluationUnitService.java
RoadEvaluationUnitServiceImpl.java
RoadEvaluationUnitController.java
EvaluationUnitQueryDTO.java
EvaluationUnitSaveDTO.java
RoadEvaluationUnitVO.java
```

## 9.4 GIS 图层

```text
GisRoadAssetLayerService.java
GisRoadAssetLayerServiceImpl.java
GisLayerQueryDTO.java
GeoJsonFeatureVO.java
GeoJsonFeatureCollectionVO.java
GisMapController.java
```

---

# 10. 阶段二前端页面清单

## 10.1 道路资产页面

```text
/views/road-asset/RouteList.vue
/views/road-asset/SectionList.vue
/views/road-asset/EvaluationUnitList.vue
```

## 10.2 GIS 一张图页面

```text
/views/gis/OneMap.vue
/views/gis/components/LayerTree.vue
/views/gis/components/ObjectDetailPanel.vue
/views/gis/components/MapToolbar.vue
```

---

# 11. 阶段二接口测试顺序

建议按以下顺序测试：

## 11.1 新增路线

```http
POST /api/road-routes
```

## 11.2 查询路线分页

```http
POST /api/road-routes/page
```

## 11.3 查询路线图层

```http
GET /api/gis/road-routes
```

## 11.4 新增路段

```http
POST /api/road-sections
```

## 11.5 查询路段图层

```http
GET /api/gis/road-sections
```

## 11.6 新增评定单元

```http
POST /api/evaluation-units
```

## 11.7 查询评定单元图层

```http
GET /api/gis/evaluation-units
```

---

# 12. 阶段二验收标准

| 验收项 | 标准 |
|---|---|
| 后端服务启动 | `/api/health` 正常返回 |
| 数据库初始化 | 基础表成功创建 |
| 路线管理 | 可新增、修改、删除、分页查询 |
| 路段管理 | 可新增、修改、删除、分页查询 |
| 评定单元管理 | 可新增、修改、删除、分页查询 |
| 空间字段 | geom 可存储 LineString |
| 路线图层 | `/api/gis/road-routes` 返回 GeoJSON |
| 路段图层 | `/api/gis/road-sections` 返回 GeoJSON |
| 评定单元图层 | `/api/gis/evaluation-units` 返回 GeoJSON |
| 多租户 | 查询自动按 tenant_id 隔离 |
| 地图展示 | 前端可以展示路线、路段、评定单元 |

---

# 13. 阶段二完成后的效果

阶段二完成后，系统将具备：

```text
1. 道路资产基础管理能力
2. 路线、路段、评定单元空间入库能力
3. 基础 GIS 图层服务能力
4. 地图展示道路资产能力
5. 后续病害、评定结果、巡检轨迹上图的数据基础
```

---

# 14. 阶段三预告：数据导入

阶段二完成后，下一阶段建议进入：

```text
阶段三：数据导入模块
```

阶段三重点：

```text
1. 路线 Excel 导入
2. 路段 Excel 导入
3. 评定单元 Excel 导入
4. GeoJSON 导入
5. WKT 空间字段解析
6. 导入错误日志
7. 导入结果预览
```

---

# 15. 当前建议

当前最建议立即执行：

```text
1. 验证阶段一基础骨架可启动
2. 初始化数据库
3. 开发道路资产 CRUD
4. 开发 GIS 路线、路段、评定单元图层接口
5. 形成 GIS 一张图最小闭环
```

完成后，再进入：

```text
数据导入 → 病害管理 → 评定结果 → GIS 专题图 → 大模型分析
```
