# SmartRoad Maintenance Platform（SRMP）

智路养护平台 — 公路养护 GIS 智能分析平台

![](https://img.shields.io/badge/Java-8%2B-blue)
![](https://img.shields.io/badge/Spring%20Boot-2.7.x-green)
![](https://img.shields.io/badge/PostgreSQL-15+-orange)
![](https://img.shields.io/badge/PostGIS-3.x-red)
![](https://img.shields.io/badge/Vue-3-blueviolet)

---

## 项目简介

智路养护平台（SRMP）一期聚焦 **GIS 一张图 + 数据接入 + 道路资产展示 + 病害分析 + 巡检评定 + AI 大模型辅助分析**，为公路养护管理人员提供可接入数据、可展示路网、可分析病害、可辅助决策的智能化平台。

**核心链路：**

```
数据导入 → 道路资产入库 → GIS一张图展示 → 病害/评定结果上图 → 统计分析 → 大模型智能分析
```

---

## 技术栈

| 层次 | 技术 |
|------|------|
| 后端 | Spring Boot 2.7.x、MyBatis Plus、Java 8/17 |
| 数据库 | PostgreSQL 15+、PostGIS 3.x |
| 缓存 | Redis |
| 文件存储 | MinIO |
| 前端 | Vue 3、Vite、Element Plus、Leaflet、ECharts |
| AI | RAG + 工具调用 + 受控 Text2SQL |

---

## 项目结构

```
srmp-parent/
├── srmp-common              # 公共基础：统一响应、分页、异常、工具类
├── srmp-web                 # Web通用：跨域、参数校验、Swagger
├── srmp-security            # 认证授权：JWT、用户、角色
├── srmp-tenant              # 多租户：字段级租户隔离
├── srmp-base                # 基础数据：字典、行政区划、组织机构
├── srmp-road-asset          # 道路资产：路线、路段、评定单元
├── srmp-inspection          # 巡检任务：巡检任务、巡检轨迹
├── srmp-disease             # 病害管理：病害类型、病害记录、复核
├── srmp-assessment          # 评定结果：MQI/PQI/PCI 等指标
├── srmp-gis                 # GIS一张图：图层服务、空间查询、桩号定位
├── srmp-import              # 数据导入：Excel/CSV/GeoJSON 解析
├── srmp-file                # 文件资料：MinIO 对象存储
├── srmp-agent               # AI大模型：智能问答、路况分析、报告生成
├── srmp-dashboard           # 首页驾驶舱：统计概览、趋势分析
├── srmp-admin               # 启动模块：Spring Boot 主入口
└── srmp-web-ui              # 前端工程：Vue 3 + Vite + Element Plus + Leaflet
```

---

## 环境变量

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-1.8.jdk/Contents/Home
MAVEN_HOME=/Users/cxp/workspace/apache-maven-3.6.3/
HTTP_PROXY=http://127.0.0.1:7890
HTTPS_PROXY=http://127.0.0.1:7890
```

---

## 快速开始

### 环境要求

- JDK 8+
- Maven 3.6+
- PostgreSQL 15+（需启用 PostGIS 扩展）
- Redis 6+
- MinIO（可选，用于文件存储）

### 1. 启动基础依赖

```bash
docker compose up -d
```

### 2. 初始化数据库

```bash
psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/schema.sql
psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/init_dict.sql
psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/init_admin.sql
psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/demo_data.sql
```

> 默认数据库：`srmp`，用户名：`srmp`，密码：`srmp123`

### 3. 编译项目

```bash
mvn clean package -DskipTests
```

### 4. 启动后端

```bash
java -jar srmp-admin/target/srmp-admin-1.0.0.jar
```

### 5. 验证服务

```bash
curl http://localhost:8080/api/health
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

### 6. 启动前端（可选）

```bash
cd srmp-web-ui
npm install
npm run dev
```

访问 http://localhost:5173 查看 GIS 一张图。

---

## 实施阶段

| 阶段 | 内容 | 状态 |
|------|------|------|
| 阶段一 | 基础骨架搭建 | ✅ 已完成 |
| 阶段二 | 道路资产 CRUD + GIS 图层接口 | ✅ 已完成（路线/路段/评定单元 CRUD、GIS 路线/路段/评定单元图层、桩号定位） |
| 阶段二（续） | 病害 GIS 图层 + 评定结果 GIS 图层 | ✅ 已完成（病害类型/记录 CRUD、GIS 图层、评定结果 CRUD、GIS 专题图） |
| 阶段三 | 数据导入模块 | ✅ 已完成（CSV/Excel 导入、模板下载、错误日志、支持 ROAD_ROUTE/ROAD_SECTION/EVALUATION_UNIT/DISEASE/ASSESSMENT/INDEX_RESULT） |
| 阶段四 | GIS 一张图完善 | ✅ 已完成（GIS 一张图前端、图层树、工具栏、病害/评定专题图、AI 问答浮窗） |
| 阶段五 | AI 大模型接入 | ✅ 已完成（OpenAI兼容客户端、业务数据查询、路线综合分析、病害热点分析、评定结果分析、地图联动查询、报告草稿生成、本地规则兜底） |
| 阶段六 | 一期演示闭环与验收 | ✅ 已完成（演示数据脚本、快速启动脚本、冒烟测试、验收文档） |
| 阶段七 | AI Agent 接口修复 | ✅ 已完成（修复 AgentChatController 占位实现，完整接入 AgentChatService） |
| 阶段八 | 一期完整性修复 | ✅ 已完成（GIS地图统计、对象详情、Dashboard概览、SQL参数类型修复、前端组件完善） |

---

## 一期演示闭环

```bash
docker compose up -d
chmod +x scripts/*.sh
./scripts/init-db.sh
./scripts/start-backend.sh
./scripts/start-frontend.sh
```

访问 http://localhost:5173，输入路线 `G210` 即可演示。

---

## 多租户

所有请求需携带 `X-Tenant-Id` 请求头：

```http
X-Tenant-Id: default
```

所有业务表均含 `tenant_id` 字段，查询时自动按租户隔离。核心表唯一约束包含 `tenant_id`：

```sql
UNIQUE(tenant_id, route_code)
UNIQUE(tenant_id, section_code)
UNIQUE(tenant_id, unit_code)
```

---

## 道路资产 API

### 路线管理

```http
POST   /api/road-routes/page        # 分页查询
GET    /api/road-routes/{id}         # 详情
POST   /api/road-routes              # 新增
PUT    /api/road-routes/{id}         # 更新
DELETE /api/road-routes/{id}         # 删除
```

**新增路线示例：**

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

### 路段管理

```http
POST   /api/road-sections/page
GET    /api/road-sections/{id}
POST   /api/road-sections
PUT    /api/road-sections/{id}
DELETE /api/road-sections/{id}
```

### 评定单元管理

```http
POST   /api/evaluation-units/page
GET    /api/evaluation-units/{id}
POST   /api/evaluation-units
PUT    /api/evaluation-units/{id}
DELETE /api/evaluation-units/{id}
```

### 桩号定位

```http
GET /api/road-assets/stake-location?routeCode=G210&direction=BOTH&stake=0.5
X-Tenant-Id: default
```

### 病害类型管理

```http
POST   /api/disease-types/page
GET    /api/disease-types/{id}
POST   /api/disease-types
PUT    /api/disease-types/{id}
DELETE /api/disease-types/{id}
```

**新增病害类型示例：**

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

### 病害记录管理

```http
POST   /api/diseases/page
GET    /api/diseases/{id}
POST   /api/diseases
PUT    /api/diseases/{id}
DELETE /api/diseases/{id}
```

**新增病害记录示例：**

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

### 评定结果管理

```http
POST   /api/assessment-results/page
GET    /api/assessment-results/{id}
POST   /api/assessment-results
PUT    /api/assessment-results/{id}
DELETE /api/assessment-results/{id}
```

**新增评定结果示例：**

```http
POST /api/assessment-results
Content-Type: application/json
X-Tenant-Id: default
```

```json
{
  "taskId": "task_demo_001",
  "objectType": "EVALUATION_UNIT",
  "objectId": "单元ID",
  "unitId": "单元ID",
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

### 指标结果管理

```http
POST   /api/index-results/page
GET    /api/index-results/{id}
POST   /api/index-results
PUT    /api/index-results/{id}
DELETE /api/index-results/{id}
```

---

## GIS 图层 API

### 可用图层

```http
GET /api/gis/layers
```

返回：`["ROAD_ROUTE", "ROAD_SECTION", "EVALUATION_UNIT", "DISEASE", "ASSESSMENT", "INSPECTION_TRACK"]`

### 路线图层

```http
GET /api/gis/road-routes?routeCode=G210
X-Tenant-Id: default
```

返回 GeoJSON：

```json
{
  "type": "FeatureCollection",
  "features": [{
    "id": "xxx",
    "geometry": {
      "type": "LineString",
      "coordinates": [[106.630, 26.650], [106.720, 26.710]]
    },
    "properties": {
      "objectType": "ROAD_ROUTE",
      "routeCode": "G210",
      "routeName": "G210示例路线",
      "startStake": 0,
      "endStake": 10,
      "lengthKm": 10
    }
  }]
}
```

### 路段图层

```http
GET /api/gis/road-sections?routeCode=G210
```

### 评定单元图层

```http
GET /api/gis/evaluation-units?routeCode=G210
```

### 病害图层

```http
GET /api/gis/diseases?routeCode=G210
X-Tenant-Id: default
```

返回 GeoJSON，properties 包含：objectType=DISEASE, routeCode, diseaseType, diseaseName, severity, quantity, measureUnit, status, color

### 评定结果专题图

```http
GET /api/gis/assessment-results?routeCode=G210&year=2026
X-Tenant-Id: default
```

返回 GeoJSON，geometry 取自评定单元geom，properties 包含：objectType=ASSESSMENT_RESULT, routeCode, unitId, mqi, pqi, pci, rqi, grade, color

颜色规则：EXCELLENT=绿色, GOOD=蓝色, MEDIUM=黄色, POOR=橙色, BAD=红色

---

## AI 大模型分析 API

### 路线综合分析

```http
POST /api/agent/analyze/route
Content-Type: application/json
X-Tenant-Id: default
```

请求：
```json
{
  "routeCode": "G210",
  "year": 2026
}
```

### 病害热点分析

```http
POST /api/agent/analyze/disease
Content-Type: application/json
X-Tenant-Id: default
```

请求：
```json
{
  "routeCode": "G210",
  "diseaseType": "POTHOLE",
  "severity": "HEAVY"
}
```

### 评定结果分析

```http
POST /api/agent/analyze/assessment
Content-Type: application/json
X-Tenant-Id: default
```

请求：
```json
{
  "routeCode": "G210",
  "year": 2026,
  "indexCode": "PCI",
  "grade": "POOR"
}
```

### 地图联动查询

```http
POST /api/agent/map-query
Content-Type: application/json
X-Tenant-Id: default
```

请求：
```json
{
  "message": "把 PCI 小于 70 的路段显示出来",
  "routeCode": "G210",
  "year": 2026
}
```

### 评定分析报告草稿

```http
POST /api/agent/report/assessment
Content-Type: application/json
X-Tenant-Id: default
```

请求：
```json
{
  "routeCode": "G210",
  "year": 2026
}
```

返回 Markdown 报告草稿。

### 智能问答

```http
POST /api/agent/chat
Content-Type: application/json
X-Tenant-Id: default
```

请求：
```json
{
  "message": "分析 G210 的病害情况",
  "context": {
    "routeCode": "G210",
    "year": 2026
  }
}
```

**配置项**（application-dev.yml）：

```yaml
srmp:
  llm:
    provider: openai-compatible
    base-url: http://127.0.0.1:8000/v1
    api-key: your-api-key
    model: gpt-4o-mini
```

若 `api-key` 为空或为 `your-api-key`，系统使用本地规则分析兜底返回。

---

## 空间字段说明

### WKT 格式

请求时传入 `geomWkt`（非数据库字段，仅用于接收参数）：

```
LINESTRING(经度 纬度, 经度 纬度, ...)
```

坐标系固定为 **EPSG:4326**，坐标顺序为 **经度 纬度**。

### 入库转换

MyBatis Mapper 使用 PostGIS 函数转换：

```xml
ST_GeomFromText(#{geomWkt}, 4326)
```

### 出库转换

```xml
ST_AsGeoJSON(geom) AS geom_geo_json
```

### 更新行为

更新接口若不传 `geomWkt`，不会覆盖原有空间字段：

```xml
<if test="geomWkt != null and geomWkt != ''">
  , geom=ST_GeomFromText(#{geomWkt}, 4326)
</if>
```

---

## 核心功能

### GIS 一张图

- 路线、路段、评定单元空间展示
- 病害点、线、面分布展示
- 巡检轨迹上图
- 按 MQI/PQI/PCI 等渲染路况专题图
- 空间查询（框选、圈选、多边形）
- 桩号定位

### 道路资产

- 路线、路段、评定单元三级管理
- 空间线形（geom）维护
- 多格式数据导入

### 数据导入

- CSV / Excel 导入（Apache POI）
- 模板下载 `/api/import/templates/{dataType}`
- 上传导入 `/api/import/upload`
- 错误日志 `/api/import/tasks/{id}/errors`
- 支持类型：ROAD_ROUTE、ROAD_SECTION、EVALUATION_UNIT、DISEASE、ASSESSMENT、INDEX_RESULT

### AI 大模型分析

- 自然语言路况问答
- 病害热点分析
- 养护建议生成
- 自动报告生成
- 地图联动高亮
- 本地规则兜底（未配置大模型时）

### 指标体系

| 指标 | 名称 |
|------|------|
| MQI | 公路技术状况指数 |
| PQI | 路面技术状况指数 |
| PCI | 路面损坏状况指数 |
| RQI | 路面行驶质量指数 |
| RDI | 路面车辙深度指数 |
| SCI | 路基技术状况指数 |
| BCI | 桥隧构造物技术状况指数 |
| TCI | 沿线设施技术状况指数 |

---

## API 概览

| 模块 | 路由前缀 |
|------|---------|
| 登录认证 | `/api/auth/**` |
| 道路资产 | `/api/road-routes/**`、`/api/road-sections/**`、`/api/evaluation-units/**`、`/api/road-assets/**` |
| 巡检任务 | `/api/inspection-tasks/**`、`/api/inspection-tracks/**` |
| 病害管理 | `/api/disease-types/**`、`/api/diseases/**` |
| 评定结果 | `/api/assessment-results/**`、`/api/index-results/**` |
| GIS 一张图 | `/api/gis/**` |
| 数据导入 | `/api/import/**` |
| 文件资料 | `/api/files/**` |
| AI 大模型 | `/api/agent/**` |
| 首页驾驶舱 | `/api/dashboard/**` |

---

## 文档

更多设计文档位于 `docs/` 目录：

- `smartroad_phase1_gis_ai_design.md` — 一期可行设计方案
- `smartroad_phase1_module_skeleton.md` — 系统模块拆分与骨架设计
- `smartroad_phase1_database_design.md` — 一期数据库设计
- `smartroad_next_step_phase2_plan.md` — 下一步实施计划
- `phase2-road-asset-gis-usage.md` — 阶段二道路资产与 GIS 图层接口使用说明
- `phase2-continued-disease-assessment-gis-usage.md` — 阶段二续期病害与评定 GIS 图层使用说明
- `phase3-data-import-usage.md` — 阶段三数据导入使用说明
- `phase4-gis-web-ui-usage.md` — 阶段四 GIS 一张图前端使用说明
- `phase5-ai-agent-usage.md` — 阶段五 AI 大模型分析使用说明
- `phase6-demo-acceptance.md` — 阶段六一期演示闭环与验收说明
- `phase7-current-fix-ai-agent.md` — 阶段七 AI Agent 接口修复说明
- `phase8-current-completeness-fix.md` — 阶段八一期完整性修复说明
- `api-smoke-test.http` — API 冒烟测试 HTTP 文件
- `SRMP项目概述.md` — 项目完整概述

---

## 常见问题

### 骨架通不通？

验证服务：

```bash
curl http://localhost:8080/api/health
```

期望返回 `phase1-skeleton` 的健康响应。

### Lombok 编译失败

**问题：** 程序包lombok不存在、找不到符号（Data、EqualsAndHashCode等）

**原因：** 父 pom 的 lombok 仅在 `dependencyManagement` 中声明版本，但缺少 `annotationProcessorPaths` 配置，且子模块未显式声明依赖。

**修复：**

1. 父 pom `pom.xml` 的 `maven-compiler-plugin` 添加注解处理器路径：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <annotationProcessorPath>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>${lombok.version}</version>
            </annotationProcessorPath>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

2. 各子模块 `pom.xml` 显式声明 lombok 依赖（若通过 `srmp-common` 传递不生效）：

```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
</dependency>
```

### JAR 无主清单属性

**问题：** `xxx.jar中没有主清单属性`

**原因：** `spring-boot-maven-plugin` 未配置 `executions`，默认不执行 `repackage`。

**修复：** `srmp-admin/pom.xml` 添加 executions：

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <version>${spring.boot.version}</version>
    <executions>
        <execution>
            <goals>
                <goal>repackage</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### Maven 镜像 502 Bad Gateway

**问题：** `Could not transfer artifact ... 502 Bad Gateway`

**原因：** `nexus.kuembang.com` 镜像已下线。

**修复：** 编辑 `~/.m2/settings.xml`，将 `nexus.kuembang.com` 相关 URL 替换为可用的镜像（如 aliyun），并确保代理配置正确：

```xml
<proxy>
    <id>http-proxy</id>
    <active>true</active>
    <protocol>http</protocol>
    <host>127.0.0.1</host>
    <port>7890</port>
</proxy>
<proxy>
    <id>https-proxy</id>
    <active>true</active>
    <protocol>https</protocol>
    <host>127.0.0.1</host>
    <port>7890</port>
</proxy>
```

### MyBatis Mapper 注入失败

**问题：** `Error creating bean... Injection of resource dependencies failed... NoSuchBeanDefinitionException`

**原因：** `@MapperScan` 缺失，MyBatis Plus 无法扫描到 Mapper 接口，导致 `RoadRouteMapper`、`RoadSectionMapper` 等未被注册为 Spring Bean。

**修复：** 在 `MybatisPlusConfig` 上添加 `@MapperScan`：

```java
@Configuration
@MapperScan("com.smartroad.srmp.**.mapper")
public class MybatisPlusConfig {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }
}
```

### 前端跨域请求失败

**问题：** `Access to XMLHttpRequest at 'http://localhost:8080/api/...' from origin 'http://localhost:5173' has been blocked by CORS policy`

**原因：** 后端未配置 CORS 跨域，前端无法请求后端 API。

**修复：** 在 `srmp-web` 模块的 `CorsConfig` 中添加跨域配置：

```java
@Configuration
public class CorsConfig {
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.addAllowedOriginPattern("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
```

### PostgreSQL 隐式类型推断失败

**问题：** `could not determine data type of parameter`

**原因：** Spring JDBC NamedParameterJdbcTemplate 对 null 值的类型推断不明确。

**修复：** 使用空字符串 `''` 代替 null，并使用 `nullif(x, '') is not distinct from` 进行比较。

---

## 许可证

Private - All Rights Reserved