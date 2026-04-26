# 智路养护平台一期系统模块拆分与系统骨架设计文档

系统名称：智路养护平台  
英文名：SmartRoad Maintenance Platform  
简称：SRMP  
一期定位：GIS 一张图 + 数据导入 + 道路资产展示 + 病害展示 + 巡检评定结果展示 + AI 大模型数据分析  
版本：V1.0  

---

# 1. 一期系统模块总览

一期重点不是复杂流程，而是先做到：

```text
数据可导入
资产可上图
病害可展示
评定可渲染
地图可查询
大模型可分析
报告可生成
```

系统模块总览：

```text
SmartRoad Maintenance Platform
智路养护平台一期

├── 1. 平台基础模块 srmp-base
├── 2. 多租户与权限模块 srmp-security / srmp-tenant
├── 3. 道路资产模块 srmp-road-asset
├── 4. 巡检任务模块 srmp-inspection
├── 5. 病害管理模块 srmp-disease
├── 6. 评定结果模块 srmp-assessment
├── 7. GIS 一张图模块 srmp-gis
├── 8. 数据导入模块 srmp-import
├── 9. 文件资料模块 srmp-file
├── 10. 大模型分析模块 srmp-agent
├── 11. 首页驾驶舱模块 srmp-dashboard
├── 12. 系统启动模块 srmp-admin
```

---

# 2. 一期推荐后端工程结构

建议使用 **Spring Boot 单体多模块**，后续可平滑拆成微服务。

```text
srmp-parent
├── pom.xml
│
├── srmp-common             公共基础模块
├── srmp-web                Web 通用模块
├── srmp-security           登录认证与权限模块
├── srmp-tenant             多租户模块
├── srmp-base               基础数据模块
├── srmp-road-asset         道路资产模块
├── srmp-inspection         巡检任务模块
├── srmp-disease            病害管理模块
├── srmp-assessment         评定结果模块
├── srmp-gis                GIS 一张图模块
├── srmp-import             数据导入模块
├── srmp-file               文件资料模块
├── srmp-agent              AI 大模型分析模块
├── srmp-dashboard          首页驾驶舱模块
└── srmp-admin              启动模块
```

---

# 3. 模块依赖关系

```text
srmp-admin
 ├── srmp-web
 ├── srmp-security
 ├── srmp-tenant
 ├── srmp-base
 ├── srmp-road-asset
 ├── srmp-inspection
 ├── srmp-disease
 ├── srmp-assessment
 ├── srmp-gis
 ├── srmp-import
 ├── srmp-file
 ├── srmp-agent
 └── srmp-dashboard

srmp-road-asset  → srmp-base, srmp-tenant
srmp-disease     → srmp-road-asset, srmp-inspection, srmp-file
srmp-assessment  → srmp-road-asset, srmp-inspection
srmp-gis         → srmp-road-asset, srmp-disease, srmp-assessment, srmp-inspection
srmp-import      → srmp-road-asset, srmp-disease, srmp-assessment, srmp-file
srmp-agent       → srmp-road-asset, srmp-disease, srmp-assessment, srmp-gis
srmp-dashboard   → srmp-road-asset, srmp-disease, srmp-assessment, srmp-inspection
```

---

# 4. 后端模块职责拆分

## 4.1 srmp-common：公共基础模块

### 职责

提供全项目通用能力。

```text
统一响应
分页模型
异常定义
基础枚举
工具类
常量定义
通用 DTO
通用 Entity 基类
```

### 包结构

```text
com.smartroad.srmp.common
├── core
│   ├── R.java
│   ├── PageRequest.java
│   ├── PageResult.java
│   └── BaseEntity.java
├── exception
│   ├── BizException.java
│   └── ErrorCode.java
├── enums
│   ├── YesNoEnum.java
│   ├── DeletedEnum.java
│   └── StatusEnum.java
├── util
│   ├── JsonUtils.java
│   ├── DateUtils.java
│   ├── GeoJsonUtils.java
│   └── IdUtils.java
└── constants
    ├── CommonConstants.java
    └── TenantConstants.java
```

---

## 4.2 srmp-web：Web 通用模块

### 职责

```text
统一异常处理
请求日志
跨域配置
参数校验
Swagger / Knife4j 配置
WebMvc 配置
```

### 包结构

```text
com.smartroad.srmp.web
├── config
│   ├── WebMvcConfig.java
│   ├── CorsConfig.java
│   └── SwaggerConfig.java
├── handler
│   └── GlobalExceptionHandler.java
├── interceptor
│   ├── RequestLogInterceptor.java
│   └── TenantInterceptor.java
└── advice
    └── ResponseAdvice.java
```

---

## 4.3 srmp-tenant：多租户模块

### 职责

一期建议采用 **字段级多租户**，所有业务表带 `tenant_id`。

```text
租户上下文
租户解析
租户拦截器
租户数据过滤
租户配置
```

### 包结构

```text
com.smartroad.srmp.tenant
├── context
│   └── TenantContextHolder.java
├── interceptor
│   └── TenantInterceptor.java
├── entity
│   └── Tenant.java
├── mapper
│   └── TenantMapper.java
├── service
│   ├── TenantService.java
│   └── impl/TenantServiceImpl.java
├── controller
│   └── TenantController.java
└── config
    └── TenantProperties.java
```

### 核心表

```text
tenant
```

---

## 4.4 srmp-security：认证权限模块

### 职责

一期权限不要做太复杂，先支持：

```text
登录
JWT Token
用户信息
菜单权限
基础角色
接口鉴权
```

### 包结构

```text
com.smartroad.srmp.security
├── config
│   └── SecurityConfig.java
├── controller
│   └── AuthController.java
├── dto
│   ├── LoginRequest.java
│   ├── LoginResponse.java
│   └── CurrentUserDTO.java
├── entity
│   ├── SysUser.java
│   ├── SysRole.java
│   └── SysMenu.java
├── mapper
│   ├── SysUserMapper.java
│   ├── SysRoleMapper.java
│   └── SysMenuMapper.java
├── service
│   ├── AuthService.java
│   └── SysUserService.java
├── filter
│   └── JwtAuthenticationFilter.java
└── util
    └── JwtUtils.java
```

### 核心表

```text
sys_user
sys_org
sys_role
sys_menu
sys_user_role
sys_role_menu
```

一期最少可先落地：

```text
sys_user
sys_org
```

---

## 4.5 srmp-base：基础数据模块

### 职责

```text
行政区划
组织机构
数据字典
管养单位
基础枚举
```

### 包结构

```text
com.smartroad.srmp.base
├── controller
│   ├── DictController.java
│   ├── OrgController.java
│   └── RegionController.java
├── entity
│   ├── SysDictType.java
│   ├── SysDictItem.java
│   ├── SysOrg.java
│   └── SysAdministrativeRegion.java
├── mapper
│   ├── SysDictTypeMapper.java
│   ├── SysDictItemMapper.java
│   ├── SysOrgMapper.java
│   └── SysAdministrativeRegionMapper.java
├── service
│   ├── DictService.java
│   ├── OrgService.java
│   └── RegionService.java
├── dto
│   ├── DictQueryDTO.java
│   └── RegionTreeDTO.java
└── enums
    ├── RouteTypeEnum.java
    ├── TechnicalGradeEnum.java
    ├── PavementTypeEnum.java
    └── AssessmentGradeEnum.java
```

### 核心表

```text
sys_dict_type
sys_dict_item
sys_org
sys_administrative_region
```

---

# 5. 一期核心业务模块

---

## 5.1 srmp-road-asset：道路资产模块

### 职责

道路资产是 GIS 一张图的数据底座。

```text
路线管理
路段管理
评定单元管理
桩号定位
道路资产空间查询
道路资产导入落库接口
```

### 包结构

```text
com.smartroad.srmp.roadasset
├── controller
│   ├── RoadRouteController.java
│   ├── RoadSectionController.java
│   └── RoadEvaluationUnitController.java
├── entity
│   ├── RoadRoute.java
│   ├── RoadSection.java
│   └── RoadEvaluationUnit.java
├── mapper
│   ├── RoadRouteMapper.java
│   ├── RoadSectionMapper.java
│   └── RoadEvaluationUnitMapper.java
├── service
│   ├── RoadRouteService.java
│   ├── RoadSectionService.java
│   ├── RoadEvaluationUnitService.java
│   └── StakeLocationService.java
├── service/impl
│   ├── RoadRouteServiceImpl.java
│   ├── RoadSectionServiceImpl.java
│   ├── RoadEvaluationUnitServiceImpl.java
│   └── StakeLocationServiceImpl.java
├── dto
│   ├── RoadRouteQueryDTO.java
│   ├── RoadRouteSaveDTO.java
│   ├── RoadSectionQueryDTO.java
│   ├── EvaluationUnitQueryDTO.java
│   └── StakeLocationQueryDTO.java
├── vo
│   ├── RoadRouteVO.java
│   ├── RoadSectionVO.java
│   ├── RoadEvaluationUnitVO.java
│   └── StakeLocationVO.java
└── convert
    └── RoadAssetConvert.java
```

### 核心表

```text
road_route
road_section
road_evaluation_unit
```

### API 路由

```text
POST /api/road-routes/page
GET  /api/road-routes/{id}
POST /api/road-routes
PUT  /api/road-routes/{id}
DELETE /api/road-routes/{id}

POST /api/road-sections/page
GET  /api/road-sections/{id}
POST /api/road-sections
PUT  /api/road-sections/{id}
DELETE /api/road-sections/{id}

POST /api/evaluation-units/page
GET  /api/evaluation-units/{id}
POST /api/evaluation-units
PUT  /api/evaluation-units/{id}
DELETE /api/evaluation-units/{id}

GET  /api/road-assets/stake-location
```

---

## 5.2 srmp-inspection：巡检任务模块

### 职责

```text
巡检任务管理
巡检范围管理
巡检轨迹管理
巡检成果关联
```

### 包结构

```text
com.smartroad.srmp.inspection
├── controller
│   ├── InspectionTaskController.java
│   └── InspectionTrackController.java
├── entity
│   ├── InspectionTask.java
│   └── InspectionTrack.java
├── mapper
│   ├── InspectionTaskMapper.java
│   └── InspectionTrackMapper.java
├── service
│   ├── InspectionTaskService.java
│   └── InspectionTrackService.java
├── dto
│   ├── InspectionTaskQueryDTO.java
│   ├── InspectionTaskSaveDTO.java
│   └── InspectionTrackQueryDTO.java
└── vo
    ├── InspectionTaskVO.java
    └── InspectionTrackVO.java
```

### 核心表

```text
inspection_task
inspection_track
```

### API 路由

```text
POST /api/inspection-tasks/page
GET  /api/inspection-tasks/{id}
POST /api/inspection-tasks
PUT  /api/inspection-tasks/{id}
DELETE /api/inspection-tasks/{id}

GET  /api/inspection-tasks/{id}/tracks
GET  /api/inspection-tasks/{id}/diseases
GET  /api/inspection-tasks/{id}/assessment-results

POST /api/inspection-tracks/page
GET  /api/inspection-tracks/{id}
```

---

## 5.3 srmp-disease：病害管理模块

### 职责

```text
病害类型管理
病害记录管理
病害空间查询
病害复核
病害统计
病害文件关联
```

### 包结构

```text
com.smartroad.srmp.disease
├── controller
│   ├── DiseaseTypeController.java
│   └── DiseaseRecordController.java
├── entity
│   ├── DiseaseTypeDict.java
│   └── DiseaseRecord.java
├── mapper
│   ├── DiseaseTypeDictMapper.java
│   └── DiseaseRecordMapper.java
├── service
│   ├── DiseaseTypeService.java
│   ├── DiseaseRecordService.java
│   └── DiseaseStatisticsService.java
├── dto
│   ├── DiseaseQueryDTO.java
│   ├── DiseaseSaveDTO.java
│   ├── DiseaseReviewDTO.java
│   └── DiseaseStatisticsQueryDTO.java
├── vo
│   ├── DiseaseRecordVO.java
│   ├── DiseaseStatisticsVO.java
│   └── DiseaseMapVO.java
└── enums
    ├── DiseaseCategoryEnum.java
    ├── DiseaseSeverityEnum.java
    └── DiseaseStatusEnum.java
```

### 核心表

```text
disease_type_dict
disease_record
file_relation
file_resource
```

### API 路由

```text
POST /api/disease-types/page
GET  /api/disease-types/{id}
POST /api/disease-types
PUT  /api/disease-types/{id}
DELETE /api/disease-types/{id}

POST /api/diseases/page
GET  /api/diseases/{id}
POST /api/diseases
PUT  /api/diseases/{id}
DELETE /api/diseases/{id}

POST /api/diseases/review
POST /api/diseases/statistics
GET  /api/diseases/{id}/files
```

---

## 5.4 srmp-assessment：评定结果模块

### 职责

一期不一定自己计算所有指标，可以先支持 **评定结果导入 + 展示 + 统计 + 专题渲染**。

```text
指标结果管理
综合评定结果管理
路况统计
路线评定结果查询
评定结果地图专题数据
```

### 包结构

```text
com.smartroad.srmp.assessment
├── controller
│   ├── AssessmentResultController.java
│   ├── IndexResultController.java
│   └── RoadConditionStatisticsController.java
├── entity
│   ├── AssessmentResult.java
│   ├── IndexResult.java
│   └── RoadConditionStatistics.java
├── mapper
│   ├── AssessmentResultMapper.java
│   ├── IndexResultMapper.java
│   └── RoadConditionStatisticsMapper.java
├── service
│   ├── AssessmentResultService.java
│   ├── IndexResultService.java
│   └── RoadConditionStatisticsService.java
├── dto
│   ├── AssessmentResultQueryDTO.java
│   ├── IndexResultQueryDTO.java
│   └── RoadConditionStatisticsQueryDTO.java
├── vo
│   ├── AssessmentResultVO.java
│   ├── IndexResultVO.java
│   ├── AssessmentMapVO.java
│   └── RoadConditionStatisticsVO.java
└── enums
    ├── RoadIndexCodeEnum.java
    └── AssessmentGradeEnum.java
```

### 核心表

```text
assessment_result
index_result
road_condition_statistics
```

### API 路由

```text
POST /api/assessment-results/page
GET  /api/assessment-results/{id}
POST /api/assessment-results/statistics
GET  /api/assessment-results/by-unit/{unitId}
GET  /api/assessment-results/route-summary

POST /api/index-results/page
GET  /api/index-results/{id}

POST /api/road-condition-statistics/page
GET  /api/road-condition-statistics/summary
```

---

## 5.5 srmp-gis：GIS 一张图模块

### 职责

这是一期核心模块，对前端地图统一提供图层数据。

```text
图层配置
路线图层
路段图层
评定单元图层
病害图层
巡检轨迹图层
评定结果专题图
空间查询
桩号定位
地图范围统计
地图高亮对象查询
```

### 包结构

```text
com.smartroad.srmp.gis
├── controller
│   ├── GisLayerController.java
│   ├── GisMapController.java
│   ├── GisSpatialQueryController.java
│   └── GisStakeController.java
├── service
│   ├── GisLayerService.java
│   ├── GisRoadAssetLayerService.java
│   ├── GisDiseaseLayerService.java
│   ├── GisAssessmentLayerService.java
│   ├── GisInspectionLayerService.java
│   ├── GisSpatialQueryService.java
│   └── GisMapStatisticsService.java
├── dto
│   ├── GisLayerQueryDTO.java
│   ├── GisDiseaseLayerQueryDTO.java
│   ├── GisAssessmentLayerQueryDTO.java
│   ├── SpatialQueryDTO.java
│   └── MapStatisticsQueryDTO.java
├── vo
│   ├── GisLayerVO.java
│   ├── GeoJsonFeatureVO.java
│   ├── GeoJsonFeatureCollectionVO.java
│   ├── GisObjectDetailVO.java
│   ├── SpatialQueryResultVO.java
│   └── MapStatisticsVO.java
└── enums
    ├── GisLayerTypeEnum.java
    └── GisGeometryTypeEnum.java
```

### API 路由

```text
GET  /api/gis/layers

GET  /api/gis/road-routes
GET  /api/gis/road-sections
GET  /api/gis/evaluation-units

GET  /api/gis/diseases
GET  /api/gis/assessment-results
GET  /api/gis/inspection-tracks

POST /api/gis/spatial-query
POST /api/gis/map-statistics

GET  /api/gis/stake-location
GET  /api/gis/object-detail
```

### GIS 图层响应建议

统一返回 GeoJSON：

```json
{
  "type": "FeatureCollection",
  "features": [
    {
      "type": "Feature",
      "id": "xxx",
      "geometry": {
        "type": "LineString",
        "coordinates": []
      },
      "properties": {
        "objectType": "ROAD_SECTION",
        "routeCode": "G210",
        "name": "G210 K0-K1",
        "grade": "GOOD",
        "color": "#1E90FF"
      }
    }
  ]
}
```

---

## 5.6 srmp-import：数据导入模块

### 职责

一期数据主要靠导入，因此这个模块非常关键。

```text
导入模板下载
文件上传
字段映射
数据解析
数据校验
导入预览
确认入库
错误日志下载
```

### 包结构

```text
com.smartroad.srmp.importer
├── controller
│   ├── DataImportController.java
│   └── ImportTemplateController.java
├── entity
│   ├── DataImportTask.java
│   ├── DataImportFieldMapping.java
│   └── DataImportErrorLog.java
├── mapper
│   ├── DataImportTaskMapper.java
│   ├── DataImportFieldMappingMapper.java
│   └── DataImportErrorLogMapper.java
├── service
│   ├── DataImportTaskService.java
│   ├── DataImportParserService.java
│   ├── DataImportCheckService.java
│   ├── DataImportExecutorService.java
│   └── ImportTemplateService.java
├── parser
│   ├── ExcelImportParser.java
│   ├── CsvImportParser.java
│   ├── GeoJsonImportParser.java
│   ├── WktImportParser.java
│   └── ShapeFileImportParser.java
├── handler
│   ├── RoadRouteImportHandler.java
│   ├── RoadSectionImportHandler.java
│   ├── EvaluationUnitImportHandler.java
│   ├── DiseaseImportHandler.java
│   ├── AssessmentImportHandler.java
│   └── InspectionTrackImportHandler.java
├── validator
│   ├── ImportRowValidator.java
│   ├── RoadRouteImportValidator.java
│   ├── DiseaseImportValidator.java
│   └── AssessmentImportValidator.java
├── dto
│   ├── ImportCreateDTO.java
│   ├── ImportParseDTO.java
│   ├── ImportConfirmDTO.java
│   └── ImportPreviewDTO.java
└── vo
    ├── ImportTaskVO.java
    ├── ImportPreviewVO.java
    └── ImportErrorVO.java
```

### 核心表

```text
data_import_task
data_import_field_mapping
data_import_error_log
file_resource
```

### API 路由

```text
POST /api/import/upload
POST /api/import/tasks
GET  /api/import/tasks/{id}

POST /api/import/tasks/{id}/parse
POST /api/import/tasks/{id}/check
GET  /api/import/tasks/{id}/preview
POST /api/import/tasks/{id}/confirm

GET  /api/import/tasks/{id}/errors
GET  /api/import/templates/{dataType}
```

### 导入数据类型

```text
ROAD_ROUTE          路线
ROAD_SECTION        路段
EVALUATION_UNIT     评定单元
DISEASE             病害
ASSESSMENT          评定结果
INSPECTION_TRACK    巡检轨迹
```

---

## 5.7 srmp-file：文件资料模块

### 职责

```text
文件上传
文件下载
文件预览
对象存储
文件与业务对象关联
导入原始文件管理
病害图片管理
报告文件管理
```

### 包结构

```text
com.smartroad.srmp.file
├── controller
│   └── FileResourceController.java
├── entity
│   ├── FileResource.java
│   └── FileRelation.java
├── mapper
│   ├── FileResourceMapper.java
│   └── FileRelationMapper.java
├── service
│   ├── FileResourceService.java
│   ├── FileRelationService.java
│   └── ObjectStorageService.java
├── storage
│   ├── MinioStorageService.java
│   └── LocalStorageService.java
├── dto
│   ├── FileUploadDTO.java
│   └── FileRelationDTO.java
└── vo
    ├── FileResourceVO.java
    └── FileUploadVO.java
```

### 核心表

```text
file_resource
file_relation
```

### API 路由

```text
POST /api/files/upload
GET  /api/files/{id}
GET  /api/files/{id}/download
GET  /api/files/{id}/preview
DELETE /api/files/{id}

POST /api/files/relation
GET  /api/files/by-biz
```

---

## 5.8 srmp-agent：AI 大模型分析模块

### 职责

一期先做 **大模型 + 工具调用 + 业务数据分析**。

```text
智能问答
路况分析
病害分析
评定结果解释
地图联动查询
报告生成
知识库问答
工具调用日志
```

### 包结构

```text
com.smartroad.srmp.agent
├── controller
│   ├── AgentChatController.java
│   ├── AgentAnalysisController.java
│   └── KnowledgeController.java
├── entity
│   ├── AgentSession.java
│   ├── AgentMessage.java
│   ├── AgentToolCallLog.java
│   ├── KnowledgeDocument.java
│   └── KnowledgeChunk.java
├── mapper
│   ├── AgentSessionMapper.java
│   ├── AgentMessageMapper.java
│   ├── AgentToolCallLogMapper.java
│   ├── KnowledgeDocumentMapper.java
│   └── KnowledgeChunkMapper.java
├── service
│   ├── AgentChatService.java
│   ├── AgentToolRouter.java
│   ├── RoadAnalysisAgentService.java
│   ├── DiseaseAnalysisAgentService.java
│   ├── AssessmentAnalysisAgentService.java
│   ├── ReportGenerationService.java
│   └── KnowledgeService.java
├── tool
│   ├── QueryRoadRouteTool.java
│   ├── QueryDiseaseTool.java
│   ├── QueryAssessmentTool.java
│   ├── QueryStatisticsTool.java
│   ├── GisHighlightTool.java
│   └── ReportGenerateTool.java
├── llm
│   ├── LlmClient.java
│   ├── OpenAiLlmClient.java
│   └── LlmRequest.java
├── dto
│   ├── AgentChatRequest.java
│   ├── AgentAnalyzeRouteRequest.java
│   └── AgentMapQueryRequest.java
└── vo
    ├── AgentChatResponse.java
    ├── AgentAnalysisVO.java
    └── AgentMapQueryVO.java
```

### 核心表

```text
agent_session
agent_message
agent_tool_call_log
knowledge_document
knowledge_chunk
```

### API 路由

```text
POST /api/agent/chat

POST /api/agent/analyze/route
POST /api/agent/analyze/disease
POST /api/agent/analyze/assessment

POST /api/agent/map-query
POST /api/agent/report/assessment

GET  /api/agent/sessions
GET  /api/agent/sessions/{id}/messages

POST /api/knowledge/documents
POST /api/knowledge/documents/{id}/parse
POST /api/knowledge/search
```

### 大模型一期边界

```text
只查数据
只分析数据
只生成建议
只生成报告草稿
不直接修改病害
不直接修改评定结果
不自动生成正式养护计划
不自动派发工单
```

---

## 5.9 srmp-dashboard：首页驾驶舱模块

### 职责

```text
总览统计
路线统计
病害统计
评定统计
巡检统计
地图范围统计
趋势分析
TOP 排名
```

### 包结构

```text
com.smartroad.srmp.dashboard
├── controller
│   └── DashboardController.java
├── service
│   ├── DashboardService.java
│   ├── RouteSummaryService.java
│   ├── DiseaseSummaryService.java
│   └── AssessmentSummaryService.java
├── dto
│   └── DashboardQueryDTO.java
└── vo
    ├── DashboardOverviewVO.java
    ├── DiseaseSummaryVO.java
    ├── AssessmentSummaryVO.java
    ├── RouteRankVO.java
    └── TrendChartVO.java
```

### API 路由

```text
GET  /api/dashboard/overview
GET  /api/dashboard/disease-summary
GET  /api/dashboard/assessment-summary
GET  /api/dashboard/inspection-summary
GET  /api/dashboard/route-rank
GET  /api/dashboard/trend
```

---

# 6. srmp-admin：启动模块

## 6.1 职责

```text
Spring Boot 启动类
统一配置
模块装配
MyBatis 扫描
定时任务入口
Swagger 聚合
```

## 6.2 包结构

```text
com.smartroad.srmp.admin
├── SmartRoadApplication.java
├── config
│   ├── MybatisPlusConfig.java
│   ├── RedisConfig.java
│   ├── MinioConfig.java
│   ├── AsyncConfig.java
│   └── JacksonConfig.java
└── runner
    └── InitDataRunner.java
```

---

# 7. 后端 Maven 父工程骨架

## 7.1 srmp-parent/pom.xml

```xml
<project>
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.smartroad</groupId>
    <artifactId>srmp-parent</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>

    <modules>
        <module>srmp-common</module>
        <module>srmp-web</module>
        <module>srmp-security</module>
        <module>srmp-tenant</module>
        <module>srmp-base</module>
        <module>srmp-road-asset</module>
        <module>srmp-inspection</module>
        <module>srmp-disease</module>
        <module>srmp-assessment</module>
        <module>srmp-gis</module>
        <module>srmp-import</module>
        <module>srmp-file</module>
        <module>srmp-agent</module>
        <module>srmp-dashboard</module>
        <module>srmp-admin</module>
    </modules>

    <properties>
        <java.version>1.8</java.version>
        <spring.boot.version>2.7.18</spring.boot.version>
        <mybatis.plus.version>3.5.5</mybatis.plus.version>
        <postgresql.version>42.7.3</postgresql.version>
        <knife4j.version>4.4.0</knife4j.version>
        <minio.version>8.5.7</minio.version>
        <jjwt.version>0.11.5</jjwt.version>
    </properties>
</project>
```

说明：

```text
如果需要兼容 Java 8，Spring Boot 推荐使用 2.7.x。
如果后续使用 Java 17，可以升级到 Spring Boot 3.x。
```

---

# 8. 推荐数据库与模块映射

| 模块 | 核心表 |
|---|---|
| srmp-tenant | tenant |
| srmp-security | sys_user、sys_org、sys_role、sys_menu |
| srmp-base | sys_dict_type、sys_dict_item、sys_administrative_region |
| srmp-road-asset | road_route、road_section、road_evaluation_unit |
| srmp-inspection | inspection_task、inspection_track |
| srmp-disease | disease_type_dict、disease_record |
| srmp-assessment | assessment_result、index_result、road_condition_statistics |
| srmp-gis | 不单独建主表，主要聚合空间数据 |
| srmp-import | data_import_task、data_import_field_mapping、data_import_error_log |
| srmp-file | file_resource、file_relation |
| srmp-agent | agent_session、agent_message、agent_tool_call_log、knowledge_document、knowledge_chunk |
| srmp-dashboard | 不单独建主表，主要聚合统计 |

---

# 9. 一期前端工程骨架

建议使用：

```text
Vue 3 + Vite + Element Plus + Leaflet + ECharts
```

## 9.1 前端目录结构

```text
srmp-web-ui
├── package.json
├── vite.config.ts
├── src
│   ├── main.ts
│   ├── App.vue
│   ├── router
│   │   └── index.ts
│   ├── store
│   │   ├── user.ts
│   │   ├── tenant.ts
│   │   └── map.ts
│   ├── api
│   │   ├── auth.ts
│   │   ├── roadRoute.ts
│   │   ├── roadSection.ts
│   │   ├── evaluationUnit.ts
│   │   ├── inspection.ts
│   │   ├── disease.ts
│   │   ├── assessment.ts
│   │   ├── gis.ts
│   │   ├── importTask.ts
│   │   ├── file.ts
│   │   ├── agent.ts
│   │   └── dashboard.ts
│   ├── views
│   │   ├── login
│   │   ├── dashboard
│   │   ├── gis
│   │   │   ├── OneMap.vue
│   │   │   ├── components
│   │   │   │   ├── LayerTree.vue
│   │   │   │   ├── MapToolbar.vue
│   │   │   │   ├── ObjectDetailPanel.vue
│   │   │   │   ├── MapStatisticsPanel.vue
│   │   │   │   └── AgentChatPanel.vue
│   │   ├── road-asset
│   │   │   ├── RouteList.vue
│   │   │   ├── SectionList.vue
│   │   │   └── EvaluationUnitList.vue
│   │   ├── inspection
│   │   ├── disease
│   │   ├── assessment
│   │   ├── import
│   │   ├── file
│   │   ├── agent
│   │   └── system
│   ├── components
│   │   ├── PageTable.vue
│   │   ├── DictSelect.vue
│   │   ├── FileUpload.vue
│   │   └── GeoJsonViewer.vue
│   ├── utils
│   │   ├── request.ts
│   │   ├── auth.ts
│   │   ├── leaflet.ts
│   │   └── geojson.ts
│   └── styles
│       └── index.scss
```

---

# 10. 一期菜单结构

```text
首页驾驶舱

GIS 一张图
├── 路网一张图
├── 病害分布图
├── 评定专题图
└── 巡检轨迹图

道路资产
├── 路线管理
├── 路段管理
└── 评定单元

巡检管理
├── 巡检任务
└── 巡检轨迹

病害管理
├── 病害列表
├── 病害复核
└── 病害统计

评定结果
├── 综合评定结果
├── 指标结果
└── 路况统计

数据导入
├── 导入任务
├── 导入模板
└── 错误日志

智能分析
├── 智能问答
├── 路况分析
├── 病害分析
└── 报告生成

文件资料
├── 文件列表
└── 文件关联

系统管理
├── 租户管理
├── 组织机构
├── 用户管理
└── 数据字典
```

---

# 11. 一期核心页面清单

## 11.1 GIS 一张图页面

核心页面：

```text
/views/gis/OneMap.vue
```

页面组成：

```text
顶部筛选区
左侧图层树
中间地图区
右侧对象详情
底部统计条
AI 问答浮窗
```

功能：

```text
路线展示
路段展示
评定单元展示
病害展示
评定结果专题渲染
巡检轨迹展示
地图框选查询
点击对象查看详情
AI 分析当前地图范围
```

---

## 11.2 数据导入页面

页面：

```text
/views/import/ImportTaskList.vue
/views/import/ImportWizard.vue
/views/import/ImportErrorList.vue
```

导入向导步骤：

```text
1. 选择数据类型
2. 上传文件
3. 字段映射
4. 数据校验
5. 数据预览
6. 确认导入
7. 查看导入结果
```

---

## 11.3 智能分析页面

页面：

```text
/views/agent/AgentChat.vue
/views/agent/RouteAnalysis.vue
/views/agent/DiseaseAnalysis.vue
/views/agent/ReportGeneration.vue
```

能力：

```text
自然语言问答
路线分析
病害热点分析
次差路段识别
评定结果解释
地图联动高亮
分析报告生成
```

---

# 12. 一期开发顺序建议

## 阶段一：基础骨架

```text
1. 创建 Maven 多模块工程
2. 创建 Vue 前端工程
3. 接入 PostgreSQL + PostGIS
4. 接入 Redis
5. 接入 MinIO
6. 完成统一响应、异常处理、日志
7. 完成租户上下文
8. 完成登录认证
```

## 阶段二：基础数据与道路资产

```text
1. 字典管理
2. 组织机构
3. 路线管理
4. 路段管理
5. 评定单元管理
6. 桩号定位接口
```

## 阶段三：数据导入

```text
1. 文件上传
2. 导入任务
3. Excel / CSV 解析
4. GeoJSON / WKT 解析
5. 字段映射
6. 数据校验
7. 确认入库
8. 错误日志
```

## 阶段四：GIS 一张图

```text
1. Leaflet 地图初始化
2. 路线图层
3. 路段图层
4. 评定单元图层
5. 病害图层
6. 评定结果专题图
7. 巡检轨迹图层
8. 图层控制
9. 对象详情
10. 地图统计
```

## 阶段五：病害、巡检、评定

```text
1. 巡检任务管理
2. 巡检轨迹展示
3. 病害列表
4. 病害复核
5. 评定结果列表
6. 指标结果查询
7. 路况统计
```

## 阶段六：AI 大模型分析

```text
1. 大模型 API 接入
2. 会话管理
3. 工具调用框架
4. 查询路线工具
5. 查询病害工具
6. 查询评定结果工具
7. GIS 高亮工具
8. 路况分析
9. 病害分析
10. 报告生成
```

---

# 13. 一期接口分组

```text
/api/auth/**                 登录认证
/api/tenants/**              租户
/api/orgs/**                 组织
/api/dicts/**                字典

/api/road-routes/**          路线
/api/road-sections/**        路段
/api/evaluation-units/**     评定单元

/api/inspection-tasks/**     巡检任务
/api/inspection-tracks/**    巡检轨迹

/api/disease-types/**        病害类型
/api/diseases/**             病害记录

/api/assessment-results/**   评定结果
/api/index-results/**        指标结果
/api/statistics/**           路况统计

/api/gis/**                  GIS 一张图
/api/import/**               数据导入
/api/files/**                文件资料
/api/agent/**                大模型分析
/api/dashboard/**            首页驾驶舱
```

---

# 14. 一期核心配置文件结构

```text
srmp-admin
└── src/main/resources
    ├── application.yml
    ├── application-dev.yml
    ├── application-prod.yml
    ├── mapper
    │   ├── roadasset
    │   ├── disease
    │   ├── assessment
    │   ├── inspection
    │   └── import
    └── db
        ├── schema.sql
        ├── init_dict.sql
        └── init_admin.sql
```

---

# 15. application-dev.yml 示例

```yaml
server:
  port: 8080

spring:
  application:
    name: srmp-admin

  datasource:
    url: jdbc:postgresql://127.0.0.1:5432/srmp
    username: srmp
    password: srmp123
    driver-class-name: org.postgresql.Driver

  redis:
    host: 127.0.0.1
    port: 6379

mybatis-plus:
  mapper-locations: classpath*:mapper/**/*.xml
  type-aliases-package: com.smartroad.srmp.*.entity
  configuration:
    map-underscore-to-camel-case: true

srmp:
  tenant:
    enabled: true
    header-name: X-Tenant-Id

  minio:
    endpoint: http://127.0.0.1:9000
    access-key: minioadmin
    secret-key: minioadmin
    bucket: srmp

  llm:
    provider: openai-compatible
    base-url: http://127.0.0.1:8000/v1
    api-key: your-api-key
    model: gpt-4o-mini
```

---

# 16. 核心后端基础类建议

## 16.1 BaseEntity

```java
@Data
public class BaseEntity {

    private String id;

    private String tenantId;

    private String createdBy;

    private String updatedBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Boolean deleted;
}
```

## 16.2 统一响应 R

```java
@Data
public class R<T> {

    private Integer code;

    private String message;

    private T data;

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.setCode(0);
        r.setMessage("success");
        r.setData(data);
        return r;
    }

    public static <T> R<T> fail(String message) {
        R<T> r = new R<>();
        r.setCode(500);
        r.setMessage(message);
        return r;
    }
}
```

## 16.3 分页对象

```java
@Data
public class PageQuery {

    private Integer pageNo = 1;

    private Integer pageSize = 20;
}
```

---

# 17. GIS 一张图后端服务骨架

## 17.1 GisMapController

```java
@RestController
@RequestMapping("/api/gis")
@Api(tags = "GIS一张图")
public class GisMapController {

    @Resource
    private GisRoadAssetLayerService roadAssetLayerService;

    @Resource
    private GisDiseaseLayerService diseaseLayerService;

    @Resource
    private GisAssessmentLayerService assessmentLayerService;

    @GetMapping("/road-routes")
    @ApiOperation("查询路线图层")
    public R<GeoJsonFeatureCollectionVO> roadRoutes(GisLayerQueryDTO query) {
        return R.ok(roadAssetLayerService.queryRoadRoutes(query));
    }

    @GetMapping("/diseases")
    @ApiOperation("查询病害图层")
    public R<GeoJsonFeatureCollectionVO> diseases(GisDiseaseLayerQueryDTO query) {
        return R.ok(diseaseLayerService.queryDiseases(query));
    }

    @GetMapping("/assessment-results")
    @ApiOperation("查询评定结果专题图层")
    public R<GeoJsonFeatureCollectionVO> assessmentResults(GisAssessmentLayerQueryDTO query) {
        return R.ok(assessmentLayerService.queryAssessmentResults(query));
    }
}
```

---

# 18. 大模型工具调用骨架

## 18.1 AgentTool 接口

```java
public interface AgentTool {

    String name();

    String description();

    Object execute(Map<String, Object> params);
}
```

## 18.2 查询病害工具

```java
@Component
public class QueryDiseaseTool implements AgentTool {

    @Resource
    private DiseaseRecordService diseaseRecordService;

    @Override
    public String name() {
        return "queryDisease";
    }

    @Override
    public String description() {
        return "根据路线、桩号、病害类型、严重程度查询病害数据";
    }

    @Override
    public Object execute(Map<String, Object> params) {
        DiseaseQueryDTO query = new DiseaseQueryDTO();
        query.setRouteCode((String) params.get("routeCode"));
        query.setDiseaseType((String) params.get("diseaseType"));
        query.setSeverity((String) params.get("severity"));
        return diseaseRecordService.listForAgent(query);
    }
}
```

---

# 19. 一期最小可运行闭环

建议第一版先实现这个闭环：

```text
1. 登录系统
2. 创建租户
3. 导入路线
4. 导入路段
5. 导入评定单元
6. GIS 地图显示路线、路段、评定单元
7. 创建巡检任务
8. 导入病害数据
9. 地图显示病害
10. 导入评定结果
11. 地图按 MQI / PCI 渲染路况等级
12. 首页统计病害数量、优良率、平均 MQI
13. 大模型回答：
    - 某路线整体路况如何
    - 哪些路段病害最多
    - 哪些路段评定为次差
    - 给出优先养护建议
```

---

# 20. 一期建设最终模块边界

## 20.1 必须实现

```text
srmp-common
srmp-web
srmp-tenant
srmp-security
srmp-base
srmp-road-asset
srmp-inspection
srmp-disease
srmp-assessment
srmp-gis
srmp-import
srmp-file
srmp-dashboard
srmp-agent
srmp-admin
```

## 20.2 可以简化实现

```text
权限：先做用户 + 租户 + 简单角色
导入：先支持 Excel、CSV、GeoJSON，Shapefile 二期完善
大模型：先做 API 工具调用，Text2SQL 二期增强
知识库：先支持文档上传和普通检索，向量检索二期增强
地图：先用 GeoJSON 接口，瓦片服务二期增强
统计：先做实时 SQL 聚合，复杂宽表二期建设
```

---

# 21. 总结

一期系统骨架建议采用：

```text
Spring Boot 2.7.x
Java 8 / Java 17 均可
PostgreSQL + PostGIS
MyBatis Plus
Redis
MinIO
Vue 3 + Element Plus + Leaflet + ECharts
大模型 API + 工具调用
```

一期核心模块为：

```text
道路资产
病害管理
巡检任务
评定结果
GIS 一张图
数据导入
AI 大模型分析
首页驾驶舱
多租户权限
文件资料
```

最终形成：

```text
一个以 GIS 一张图为核心入口，
以道路资产、病害、巡检评定结果为核心数据，
以导入为主要数据接入方式，
以大模型为智能分析能力的
公路养护一期可运行平台骨架。
```
