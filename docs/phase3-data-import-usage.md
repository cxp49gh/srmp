# 阶段三：数据导入模块使用说明

本阶段实现基础数据导入能力，支持 CSV / Excel 文件上传后立即解析、校验并入库。

## 1. 支持导入类型

| dataType | 说明 |
|---|---|
| ROAD_ROUTE | 路线 |
| ROAD_SECTION | 路段 |
| EVALUATION_UNIT | 评定单元 |
| DISEASE | 病害记录 |
| ASSESSMENT | 综合评定结果 |
| INDEX_RESULT | 指标结果 |

## 2. 下载模板

```http
GET /api/import/templates/ROAD_ROUTE
X-Tenant-Id: default
```

其他模板：

```text
/api/import/templates/ROAD_SECTION
/api/import/templates/EVALUATION_UNIT
/api/import/templates/DISEASE
/api/import/templates/ASSESSMENT
/api/import/templates/INDEX_RESULT
```

## 3. 上传导入

```bash
curl -X POST 'http://localhost:8080/api/import/upload?dataType=ROAD_ROUTE&importName=路线导入'   -H 'X-Tenant-Id: default'   -F 'file=@road_route_template.csv'
```

返回示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "importTaskId": "xxx",
    "importCode": "IMP1710000000000",
    "dataType": "ROAD_ROUTE",
    "status": "SUCCESS",
    "totalCount": 1,
    "successCount": 1,
    "failedCount": 0,
    "errors": []
  }
}
```

## 4. 查看错误日志

```http
GET /api/import/tasks/{importTaskId}/errors
X-Tenant-Id: default
```

## 5. 空间字段说明

导入模板优先支持 WKT：

```text
POINT(106.630 26.650)
LINESTRING(106.630 26.650,106.720 26.710)
POLYGON((106.1 26.1,106.2 26.1,106.2 26.2,106.1 26.1))
```

病害导入也支持 `longitude` + `latitude` 自动转换为 `POINT(lng lat)`。

## 6. 当前实现边界

1. 阶段三采用"上传后立即入库"的简化流程；
2. 字段映射和导入预览后续阶段增强；
3. CSV 支持英文逗号和双引号转义；
4. Excel 支持 `.xls` / `.xlsx`，读取第一个 Sheet；
5. 失败行会写入 `data_import_error_log`，成功行正常入库；
6. 多租户继续通过 `X-Tenant-Id` 请求头隔离。

## 7. 验收顺序

```text
1. 下载 ROAD_ROUTE 模板
2. 上传路线 CSV，确认 road_route 有数据
3. 调用 /api/gis/road-routes，确认路线可上图
4. 下载 ROAD_SECTION 模板并导入路段
5. 下载 EVALUATION_UNIT 模板并导入评定单元
6. 下载 DISEASE 模板并导入病害
7. 调用 /api/gis/diseases，确认病害可上图
8. 下载 ASSESSMENT 模板并导入评定结果
9. 调用 /api/gis/assessment-results，确认评定专题图可展示
```
