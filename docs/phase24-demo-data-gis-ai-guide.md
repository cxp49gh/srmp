# 阶段二十四：演示数据接入与 GIS/AI 联调收口

## 1. 阶段目标

阶段二十四用于确认 Phase 1-22 最新 schema 下的大规模演示数据已经真正接入系统，并能被 GIS、AI 问答、方案生成使用。

本阶段新增：

```text
1. 演示数据状态接口；
2. 演示驾驶舱接口；
3. 演示路线统计接口；
4. AI 演示快捷问题接口；
5. 前端演示数据联调看板；
6. 演示数据检查脚本；
7. AI 问答检查脚本；
8. 联调说明文档。
```

## 2. 新增接口

```text
GET /api/demo/status
GET /api/demo/dashboard
GET /api/demo/routes
GET /api/demo/questions
```

参数：

```text
tenantId=default
year=2026
```

示例：

```bash
curl -s "http://localhost:8080/api/demo/status?tenantId=default&year=2026" \
  -H "X-Tenant-Id: default"
```

## 3. 前端页面

```text
http://localhost:5173/demo/dashboard
```

页面包含：

```text
1. 演示数据健康状态；
2. 路线数、总里程、评定结果、病害记录；
3. 平均 MQI / PQI / PCI；
4. 路线评定概览；
5. 等级分布；
6. 病害 TOP；
7. 低分评定单元；
8. AI 快捷问题。
```

## 4. 数据导入前置

请先导入最新 schema 兼容演示数据包：

```bash
unzip srmp-demo-road-data-phase1-22-fixed.zip
cd srmp-demo-road-data-phase1-22-fixed
chmod +x import_demo_phase1_22.sh
./import_demo_phase1_22.sh
```

导入后至少应达到：

```text
road_route              8+
road_section            100+
road_evaluation_unit    1000+
assessment_result       1000+
disease_record          3000+
```

## 5. 命令行验收

```bash
chmod +x scripts/*.sh
./scripts/check-demo-data.sh
./scripts/check-demo-ai.sh
./scripts/check-demo-gis-ai.sh
```

可指定参数：

```bash
BASE_URL=http://localhost:8080 TENANT_ID=default YEAR=2026 ./scripts/check-demo-data.sh
```

## 6. 推荐 AI 演示问题

```text
分析 G210 2026 年路况
生成 G210 2026 年技术状况评定报告草稿
对比 G210 和 S205 的 MQI、PCI 情况
统计 G210 主要病害类型和养护建议
找出 2026 年次差路段较多的路线
结合知识库生成 S205 养护建议方案
```

## 7. 验收标准

```text
1. /api/demo/status 返回 ready=true；
2. /api/demo/dashboard 返回 summary / routeRanking / gradeDistribution / diseaseTop；
3. /demo/dashboard 页面能正常展示；
4. /api/agent/chat 对"分析 G210 2026 年路况"返回非空 answer；
5. 方案生成页面能使用 G210 / 2026 生成方案；
6. 质量校验和 Markdown 导出可执行。
```

## 8. 常见问题

### 8.1 status 显示 ready=false

通常是演示数据未导入，或者导入到了其他库。

检查：

```sql
SELECT route_code, count(*)
FROM assessment_result
WHERE tenant_id='default' AND year=2026
GROUP BY route_code;
```

### 8.2 AI 回答总数为 0

确认请求中使用：

```json
{
  "context": {
    "routeCode": "G210",
    "year": 2026
  }
}
```

并确认后端读取的 `X-Tenant-Id` 是：

```text
default
```

### 8.3 GIS 地图没有数据

确认地图接口使用的表是：

```text
road_route
road_section
road_evaluation_unit
disease_record
assessment_result
```

并确认几何字段是 PostGIS：

```text
geom
center_point
```