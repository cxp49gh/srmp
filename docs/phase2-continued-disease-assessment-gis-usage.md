# 阶段二续：病害与评定 GIS 图层使用说明

本阶段在阶段二道路资产与 GIS 图层基础上继续补齐：

1. 病害类型 CRUD；
2. 病害记录 CRUD；
3. 病害 GIS 图层 `/api/gis/diseases`；
4. 综合评定结果 CRUD；
5. 指标结果 CRUD；
6. 评定结果 GIS 专题图 `/api/gis/assessment-results`；
7. 病害统计 `/api/diseases/statistics`；
8. 评定统计 `/api/assessment-results/statistics`。

## 1. 数据库初始化

如果你已经执行过阶段一、阶段二 SQL，请再次执行：

```bash
psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/schema.sql
psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/init_dict.sql
```

脚本使用 `CREATE TABLE IF NOT EXISTS` 和 `ON CONFLICT DO NOTHING`，可重复执行。

## 2. 编译启动

```bash
mvn clean package -DskipTests
cd srmp-admin
mvn spring-boot:run
```

所有接口请求建议带租户头：

```http
X-Tenant-Id: default
```

## 3. 新增病害类型

```http
POST /api/disease-types
Content-Type: application/json
X-Tenant-Id: default
```

```json
{
  "diseaseCode": "POTHOLE",
  "diseaseName": "坑槽",
  "diseaseCategory": "PAVEMENT",
  "measureUnit": "m2",
  "relatedIndex": "PCI",
  "severityEnabled": true,
  "enabled": true,
  "sortNo": 1
}
```

## 4. 新增病害记录

```http
POST /api/diseases
Content-Type: application/json
X-Tenant-Id: default
```

```json
{
  "routeCode": "G210",
  "direction": "BOTH",
  "laneNo": 1,
  "startStake": 0.35,
  "endStake": 0.36,
  "diseaseCategory": "PAVEMENT",
  "diseaseType": "POTHOLE",
  "diseaseName": "坑槽",
  "severity": "HEAVY",
  "quantity": 1,
  "measureUnit": "m2",
  "damageArea": 2.5,
  "source": "MANUAL",
  "geomWkt": "POINT(106.635 26.655)"
}
```

## 5. 查询病害 GIS 图层

```http
GET /api/gis/diseases?routeCode=G210
X-Tenant-Id: default
```

返回 GeoJSON FeatureCollection，properties 中包含：

```text
objectType=DISEASE
routeCode
diseaseType
diseaseName
severity
quantity
measureUnit
status
color
```

## 6. 新增评定结果

新增前建议先创建路线、路段、评定单元，并拿到 `unitId`。

```http
POST /api/assessment-results
Content-Type: application/json
X-Tenant-Id: default
```

```json
{
  "taskId": "task_demo_001",
  "objectType": "EVALUATION_UNIT",
  "objectId": "替换为评定单元ID",
  "unitId": "替换为评定单元ID",
  "routeCode": "G210",
  "direction": "BOTH",
  "startStake": 0,
  "endStake": 1,
  "year": 2026,
  "mqi": 78.5,
  "pqi": 75.2,
  "pci": 68.9,
  "rqi": 82.1,
  "grade": "MEDIUM"
}
```

## 7. 查询评定结果 GIS 专题图

```http
GET /api/gis/assessment-results?routeCode=G210&year=2026
X-Tenant-Id: default
```

返回 GeoJSON FeatureCollection，geometry 取对应 `road_evaluation_unit.geom`，properties 中包含：

```text
objectType=ASSESSMENT_RESULT
routeCode
unitId
mqi
pqi
pci
rqi
grade
color
```

颜色规则：

| 等级 | 颜色 |
|---|---|
| EXCELLENT | 绿色 |
| GOOD | 蓝色 |
| MEDIUM | 黄色 |
| POOR | 橙色 |
| BAD | 红色 |

## 8. 验收闭环

完成后应能跑通：

```text
新增病害 → /api/gis/diseases 返回 GeoJSON → 地图展示病害
新增评定结果 → /api/gis/assessment-results 返回专题 GeoJSON → 地图按等级渲染
```
