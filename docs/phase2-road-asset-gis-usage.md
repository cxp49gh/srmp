# 阶段二道路资产与 GIS 图层接口使用说明

## 1. 本阶段新增能力

1. 路线 CRUD：`/api/road-routes`
2. 路段 CRUD：`/api/road-sections`
3. 评定单元 CRUD：`/api/evaluation-units`
4. 桩号定位：`/api/road-assets/stake-location`
5. GIS 路线图层：`/api/gis/road-routes`
6. GIS 路段图层：`/api/gis/road-sections`
7. GIS 评定单元图层：`/api/gis/evaluation-units`

## 2. 请求头

```http
X-Tenant-Id: default
```

## 3. 新增路线示例

```http
POST /api/road-routes
Content-Type: application/json
X-Tenant-Id: default
```

```json
{
  "routeCode": "G210",
  "routeName": "G210示例路线",
  "routeType": "NATIONAL_HIGHWAY",
  "technicalGrade": "FIRST_CLASS",
  "startStake": 0,
  "endStake": 10,
  "lengthKm": 10,
  "geomWkt": "LINESTRING(106.630 26.650,106.720 26.710)"
}
```

## 4. 查询路线图层

```http
GET /api/gis/road-routes?routeCode=G210
X-Tenant-Id: default
```

## 5. 新增路段示例

```json
{
  "routeId": "路线ID",
  "routeCode": "G210",
  "sectionCode": "G210_K0_K10",
  "sectionName": "G210 K0-K10",
  "direction": "BOTH",
  "startStake": 0,
  "endStake": 10,
  "lengthKm": 10,
  "pavementType": "ASPHALT",
  "technicalGrade": "FIRST_CLASS",
  "geomWkt": "LINESTRING(106.630 26.650,106.720 26.710)"
}
```

## 6. 新增评定单元示例

```json
{
  "routeId": "路线ID",
  "sectionId": "路段ID",
  "routeCode": "G210",
  "unitCode": "G210_BOTH_K0_K1",
  "direction": "BOTH",
  "startStake": 0,
  "endStake": 1,
  "lengthM": 1000,
  "geomWkt": "LINESTRING(106.630 26.650,106.640 26.655)"
}
```

## 7. 桩号定位

```http
GET /api/road-assets/stake-location?routeCode=G210&direction=BOTH&stake=0.5
X-Tenant-Id: default
```

## 8. 注意事项

1. `geomWkt` 使用 WKT 格式，坐标顺序为 `经度 纬度`。
2. 坐标系固定为 EPSG:4326。
3. 更新接口如果不传 `geomWkt`，不会覆盖原空间字段。
4. 所有唯一约束均按 `tenant_id` 隔离。
