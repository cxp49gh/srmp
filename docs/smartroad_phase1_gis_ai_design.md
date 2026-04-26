# 智路养护平台一期可行设计方案

英文名：SmartRoad Maintenance Platform  
简称：SRMP  
一期子系统：智路养护 GIS 智能分析平台  
英文名：SmartRoad GIS Intelligence Platform  
版本：V1.0  

---

# 1. 一期建设定位

一期不追求完整养护闭环，先建设一个 **“可接入、可展示、可分析、可辅助决策”** 的公路养护 GIS 智能分析平台。

核心目标：

```text
数据导入 → 数据治理 → GIS一张图展示 → 路况/病害/评定分析 → AI大模型辅助研判
```

一期重点解决：

1. 道路资产数据可视化；
2. 病害数据可视化；
3. 巡检任务与评定结果可视化；
4. 支持 Excel、GeoJSON、Shapefile、CSV 等方式导入数据；
5. 接入 AI 大模型，对道路资产、病害、评定结果进行智能问答和分析；
6. 为后续养护计划、工单派发、验收闭环预留数据基础。

---

# 2. 一期建设范围

## 2.1 一期核心功能

```text
1. GIS 一张图
2. 道路资产管理
3. 病害数据管理
4. 巡检任务管理
5. 技术状况评定结果管理
6. 数据导入管理
7. AI 大模型智能分析
8. 统计驾驶舱
9. 多租户与基础权限
10. 文件资料管理
```

## 2.2 一期暂不建设内容

以下内容建议二期建设：

```text
1. 养护计划编制
2. 养护工单派发
3. 工单移动端执行
4. 验收整改闭环
5. AI 自动识别模型训练平台
6. 复杂审批流
7. 设备实时接入
8. IoT 实时监测
9. 预算管理
10. 项目管理
```

一期可以预留数据结构和接口，但不作为核心建设目标。

---

# 3. 总体架构设计

## 3.1 总体架构

```text
┌──────────────────────────────────────────────┐
│                 应用展示层                    │
│ GIS一张图 / 首页驾驶舱 / 数据分析 / 智能问答     │
└──────────────────────────────────────────────┘
                        │
┌──────────────────────────────────────────────┐
│                 业务服务层                    │
│ 道路资产 / 病害 / 巡检 / 评定 / 导入 / 统计       │
└──────────────────────────────────────────────┘
                        │
┌──────────────────────────────────────────────┐
│                 智能分析层                    │
│ 大模型问答 / 数据分析 Agent / 报告生成 / SQL生成  │
└──────────────────────────────────────────────┘
                        │
┌──────────────────────────────────────────────┐
│                 GIS服务层                     │
│ 空间查询 / 图层管理 / 桩号定位 / 专题渲染         │
└──────────────────────────────────────────────┘
                        │
┌──────────────────────────────────────────────┐
│                 数据接入层                    │
│ Excel / CSV / GeoJSON / Shapefile / API导入     │
└──────────────────────────────────────────────┘
                        │
┌──────────────────────────────────────────────┐
│                 数据存储层                    │
│ PostgreSQL + PostGIS / MinIO / Redis           │
└──────────────────────────────────────────────┘
```

## 3.2 一期核心业务链路

```text
道路资产导入
    ↓
路线、路段、评定单元入库
    ↓
GIS 一张图展示道路资产
    ↓
导入巡检病害数据
    ↓
病害点、线、面上图
    ↓
导入评定结果 MQI / PQI / PCI 等
    ↓
按路段渲染路况等级
    ↓
统计分析与大模型问答
```

---

# 4. 一期功能模块设计

---

# 4.1 GIS 一张图中心

## 4.1.1 模块定位

GIS 一张图是一期核心入口，用地图统一展示：

```text
道路资产
病害分布
巡检轨迹
评定结果
路况等级
行政区划
统计指标
```

## 4.1.2 核心功能

| 功能 | 说明 |
|---|---|
| 路网展示 | 展示路线、路段、评定单元 |
| 路况专题图 | 按 MQI、PQI、PCI 等等级渲染道路颜色 |
| 病害分布图 | 展示裂缝、坑槽、车辙、沉陷等病害 |
| 巡检轨迹图 | 展示巡检车辆或人工巡查轨迹 |
| 评定结果图 | 展示评定单元的技术状况等级 |
| 图层控制 | 控制路线、路段、病害、轨迹、评定图层显隐 |
| 空间查询 | 支持框选、圈选、多边形查询 |
| 桩号定位 | 输入路线编号和桩号，定位到地图位置 |
| 属性查看 | 点击地图对象查看详细属性 |
| 统计联动 | 地图范围变化后联动统计面板 |
| 数据筛选 | 按路线、行政区、时间、等级、病害类型过滤 |
| 颜色渲染 | 按优、良、中、次、差渲染不同颜色 |

## 4.1.3 地图图层设计

| 图层 | 数据来源 | 几何类型 | 展示方式 |
|---|---|---|---|
| 行政区划图层 | sys_administrative_region | Polygon | 边界面 |
| 路线图层 | road_route | LineString | 线路 |
| 路段图层 | road_section | LineString | 分段线路 |
| 评定单元图层 | road_evaluation_unit | LineString | 1000m 单元 |
| 病害点图层 | disease_record | Point | 图标点 |
| 病害线图层 | disease_record | LineString | 线段 |
| 病害面图层 | disease_record | Polygon | 面状区域 |
| 巡检轨迹图层 | inspection_track | LineString | 轨迹线 |
| 评定结果图层 | assessment_result + evaluation_unit | LineString | 等级着色 |
| 文件影像图层 | file_resource | Point | 图片/视频点位 |

## 4.1.4 地图交互设计

### 左侧图层面板

```text
图层控制
├── 道路资产
│   ├── 路线
│   ├── 路段
│   └── 评定单元
├── 巡检数据
│   ├── 巡检轨迹
│   └── 巡检点位
├── 病害数据
│   ├── 裂缝
│   ├── 坑槽
│   ├── 车辙
│   └── 沉陷
└── 评定结果
    ├── MQI
    ├── PQI
    ├── PCI
    └── RQI
```

### 顶部筛选区

```text
租户 / 行政区 / 路线 / 路段 / 年度 / 检测任务 / 指标类型 / 评定等级
```

### 右侧详情面板

点击地图对象后展示：

```text
对象类型
路线编号
路线名称
起止桩号
技术等级
路面类型
病害类型
严重程度
评定指标
评定等级
关联图片
关联任务
```

### 底部统计面板

展示当前地图范围内：

```text
路线总里程
病害总数
严重病害数
MQI 平均值
优良路率
次差路率
巡检里程
检测任务数
```

---

# 4.2 道路资产管理

## 4.2.1 模块定位

管理平台最基础的空间骨架，包括路线、路段、评定单元。

## 4.2.2 一期功能

| 功能 | 说明 |
|---|---|
| 路线导入 | 支持 Excel、GeoJSON、Shapefile 导入 |
| 路线管理 | 查询、查看、编辑路线基础信息 |
| 路段管理 | 按路线、行政区、桩号维护路段 |
| 评定单元管理 | 支持导入或按规则生成 1000m 评定单元 |
| 空间数据维护 | 维护线形 geom |
| 资产地图查看 | 在 GIS 一张图查看资产 |
| 桩号查询 | 根据路线编号、桩号查询空间位置 |
| 数据校验 | 校验桩号范围、路线重复、空间缺失 |

## 4.2.3 核心数据

```text
road_route
road_section
road_evaluation_unit
road_lane
road_stake_chain
```

一期最少需要：

```text
road_route
road_section
road_evaluation_unit
```

---

# 4.3 病害数据管理

## 4.3.1 模块定位

用于接入、维护、展示和统计道路病害数据。

## 4.3.2 一期功能

| 功能 | 说明 |
|---|---|
| 病害导入 | 支持 Excel、CSV、GeoJSON 导入 |
| 病害查询 | 按路线、桩号、类型、严重程度查询 |
| 病害上图 | 在 GIS 一张图中展示点、线、面病害 |
| 病害详情 | 查看病害属性、图片、视频、位置 |
| 病害复核 | 人工确认、修改、忽略 |
| 病害统计 | 按路线、类型、严重程度、时间统计 |
| 病害关联 | 关联巡检任务、评定单元 |
| 病害导出 | 导出病害清单 |

## 4.3.3 病害类型示例

```text
裂缝
坑槽
车辙
沉陷
松散
泛油
修补
路肩损坏
边坡损坏
标志缺损
标线磨损
护栏损坏
```

## 4.3.4 核心数据

```text
disease_type_dict
disease_record
disease_image
disease_review_record
```

一期最少需要：

```text
disease_type_dict
disease_record
file_resource
```

---

# 4.4 巡检任务管理

## 4.4.1 模块定位

管理巡检批次、检测任务、检测范围、巡检轨迹和导入记录。

## 4.4.2 一期功能

| 功能 | 说明 |
|---|---|
| 巡检任务创建 | 创建年度检测、专项检测、日常巡查任务 |
| 巡检范围管理 | 绑定路线、路段、起止桩号 |
| 巡检轨迹导入 | 导入 GPX、GeoJSON、CSV 轨迹 |
| 巡检成果管理 | 关联病害数据、图片、评定结果 |
| 任务状态管理 | 创建、导入中、已完成、已评定 |
| 任务地图展示 | 在地图上展示任务范围和轨迹 |

## 4.4.3 核心数据

```text
inspection_task
inspection_task_route_scope
inspection_track
inspection_data_import_batch
```

一期最少需要：

```text
inspection_task
inspection_track
data_import_task
```

---

# 4.5 技术状况评定结果管理

## 4.5.1 模块定位

接入并展示巡检评定结果，如 MQI、SCI、PQI、PCI、RQI、RDI、BCI、TCI 等。

## 4.5.2 一期功能

| 功能 | 说明 |
|---|---|
| 评定结果导入 | 支持 Excel、CSV 导入 |
| 指标结果管理 | 管理 MQI、PQI、PCI 等指标 |
| 等级展示 | 优、良、中、次、差 |
| 路况专题图 | 按指标和等级渲染地图 |
| 评定详情 | 查看单元指标明细 |
| 年度对比 | 对比不同年度路况变化 |
| 路线统计 | 按路线统计优良率、次差率、平均 MQI |
| 区域统计 | 按行政区统计路况 |

## 4.5.3 指标体系

```text
MQI 公路技术状况指数
├── SCI 路基技术状况指数
├── PQI 路面技术状况指数
│   ├── PCI 路面损坏状况指数
│   ├── RQI 路面行驶质量指数
│   ├── RDI 路面车辙深度指数
│   ├── PBI 路面跳车指数
│   ├── PWI 路面磨耗指数
│   ├── SRI 路面抗滑性能指数
│   └── PSSI 路面结构强度指数
├── BCI 桥隧构造物技术状况指数
└── TCI 沿线设施技术状况指数
```

## 4.5.4 核心数据

```text
index_result
assessment_result
road_condition_statistics
```

一期最少需要：

```text
assessment_result
index_result
```

---

# 4.6 数据导入中心

## 4.6.1 模块定位

一期数据主要通过导入方式接入，因此数据导入中心是关键模块。

## 4.6.2 支持导入类型

| 数据类型 | 支持格式 | 说明 |
|---|---|---|
| 路线数据 | Excel、CSV、GeoJSON、Shapefile | 路线编号、名称、线形 |
| 路段数据 | Excel、CSV、GeoJSON、Shapefile | 起止桩号、行政区、管养单位 |
| 评定单元 | Excel、CSV、GeoJSON | 1000m 单元 |
| 病害数据 | Excel、CSV、GeoJSON | 病害类型、桩号、坐标、严重程度 |
| 巡检轨迹 | GPX、GeoJSON、CSV | 车辆轨迹、采集轨迹 |
| 评定结果 | Excel、CSV | MQI、PQI、PCI 等指标 |
| 图片资料 | ZIP、图片文件 | 关联病害或任务 |
| 统计数据 | Excel、CSV | 历史统计结果 |

## 4.6.3 导入流程

```text
上传文件
  ↓
选择数据类型
  ↓
字段映射
  ↓
数据预校验
  ↓
空间数据解析
  ↓
业务规则校验
  ↓
生成导入预览
  ↓
确认入库
  ↓
生成导入日志
```

## 4.6.4 导入校验规则

| 数据 | 校验内容 |
|---|---|
| 路线 | route_code 不为空、路线编号不重复 |
| 路段 | 起点桩号小于终点桩号、路线存在 |
| 评定单元 | 单元不重叠、桩号在路段范围内 |
| 病害 | 病害类型合法、桩号合法、坐标合法 |
| 评定结果 | 指标值 0—100、等级合法 |
| 轨迹 | 坐标合法、轨迹点数量大于 1 |
| 图片 | 文件格式合法、可关联业务对象 |

## 4.6.5 导入任务状态

```text
CREATED       已创建
UPLOADED      已上传
PARSING       解析中
CHECKING      校验中
CHECK_FAILED  校验失败
WAIT_CONFIRM  等待确认
IMPORTING     导入中
SUCCESS       导入成功
FAILED        导入失败
```

## 4.6.6 核心数据

```text
data_import_task
data_import_field_mapping
data_import_error_log
file_resource
```

---

# 4.7 AI 大模型智能分析中心

## 4.7.1 模块定位

一期不做复杂 AI 模型训练，重点接入大模型能力，对平台已有结构化数据和文档资料进行分析。

大模型主要承担：

```text
自然语言查询
数据分析解释
病害趋势总结
路况评定解读
养护建议辅助生成
自动报告生成
```

## 4.7.2 一期大模型能力

| 能力 | 说明 |
|---|---|
| 路况问答 | 问“G210 今年路况怎么样？” |
| 病害分析 | 问“哪些路段坑槽最多？” |
| 评定解读 | 解释 MQI、PQI、PCI 变化原因 |
| 趋势分析 | 分析某路线近几年路况变化 |
| 养护建议 | 根据病害和评定结果给出建议措施 |
| 自动报告 | 自动生成路线评定分析报告 |
| 数据查询 | 自然语言转 SQL 查询 |
| 地图联动 | 问答结果可定位到地图对象 |
| 异常发现 | 识别次差路段、严重病害集中区 |
| 标准问答 | 支持导入标准文档后问答 |

## 4.7.3 大模型交互示例

### 示例 1：路况分析

```text
用户：分析一下 G210 2026 年的整体路况。

大模型输出：
G210 2026 年共评定 356.8 公里，平均 MQI 为 86.4，整体等级为良。
优良路率为 82.6%，次差路率为 6.1%。
主要问题集中在 K120+000 至 K135+000 区间，PCI 明显低于全线平均值。
建议优先关注裂缝、坑槽和车辙病害集中的路段。
```

### 示例 2：病害热点分析

```text
用户：找出病害最严重的 10 个路段。

大模型输出：
病害最严重的路段主要集中在以下区间：
1. G210 K120+000 - K121+000，重度病害 12 处
2. G210 K128+000 - K129+000，坑槽 8 处
3. G210 K132+000 - K133+000，裂缝总长度 320 米

这些路段建议纳入近期修复养护计划。
```

### 示例 3：地图联动

```text
用户：把 PCI 小于 70 的路段显示出来。

系统动作：
1. 大模型识别查询意图；
2. 转换为结构化查询；
3. 查询 assessment_result；
4. 返回符合条件的评定单元；
5. GIS 地图高亮显示。
```

## 4.7.4 大模型技术路径

一期建议采用 **RAG + 工具调用 + SQL 查询** 的方式。

```text
用户问题
  ↓
意图识别
  ↓
判断问题类型
  ├── 标准知识问答 → RAG 检索
  ├── 数据统计分析 → Text2SQL / 预置 API
  ├── 地图查询定位 → GIS API
  ├── 报告生成 → 报告模板 + 数据查询
  └── 养护建议 → 规则库 + 大模型生成
  ↓
结果生成
  ↓
地图 / 表格 / 报告联动展示
```

## 4.7.5 大模型工具设计

| 工具 | 说明 |
|---|---|
| 查询路线工具 | 根据路线编号查询路线基础信息 |
| 查询病害工具 | 根据条件查询病害记录 |
| 查询评定结果工具 | 查询 MQI、PQI、PCI 等指标 |
| 查询统计工具 | 查询优良率、次差率、平均指标 |
| GIS 定位工具 | 返回地图高亮对象 |
| 报告生成工具 | 生成 Word / PDF 报告 |
| 标准知识检索工具 | 检索规范、制度、标准文档 |
| 养护建议工具 | 根据病害和指标生成建议 |

## 4.7.6 大模型边界控制

一期大模型需要设置边界：

```text
1. 不直接修改业务数据；
2. 不直接生成正式养护计划；
3. 不自动派发工单；
4. 所有建议仅作为辅助决策；
5. 查询 SQL 必须经过白名单或 API 工具封装；
6. 涉及统计结果必须返回数据来源；
7. 涉及地图联动必须返回对象 ID；
8. 重要报告需要人工确认。
```

---

# 4.8 统计驾驶舱

## 4.8.1 模块定位

提供平台首页和专题统计面板，辅助管理人员快速了解道路养护状况。

## 4.8.2 首页指标

```text
道路总里程
路线数量
路段数量
评定单元数量
巡检任务数量
巡检里程
病害总数
重度病害数
平均 MQI
平均 PQI
平均 PCI
优良路率
次差路率
待复核病害数
```

## 4.8.3 专题统计

| 专题 | 指标 |
|---|---|
| 路况统计 | MQI、PQI、PCI、优良率、次差率 |
| 病害统计 | 病害数量、面积、长度、严重程度 |
| 巡检统计 | 巡检任务数、巡检里程、任务完成率 |
| 路线排名 | MQI 排名、病害数量排名、次差路率排名 |
| 趋势分析 | 年度 MQI 变化、病害变化 |
| 区域分析 | 按行政区、管养单位统计 |

---

# 4.9 文件与影像资料管理

## 4.9.1 模块定位

用于管理导入文件、病害图片、巡检影像、评定报告等非结构化资料。

## 4.9.2 一期功能

| 功能 | 说明 |
|---|---|
| 文件上传 | 上传图片、视频、文档、压缩包 |
| 文件关联 | 关联病害、巡检任务、路线、评定结果 |
| 在线预览 | 图片、PDF、视频预览 |
| 文件下载 | 下载原始导入文件和成果文件 |
| 文件归档 | 按任务或路线归档 |
| 对象存储 | 支持 MinIO / OSS / S3 |

---

# 5. 一期数据设计

## 5.1 核心表清单

```text
tenant                          租户表
sys_user                        用户表
sys_org                         组织表
sys_dict_type                   字典类型表
sys_dict_item                   字典项表

road_route                      路线表
road_section                    路段表
road_evaluation_unit            评定单元表

inspection_task                 巡检任务表
inspection_track                巡检轨迹表

disease_type_dict               病害类型字典
disease_record                  病害记录表

index_result                    指标结果表
assessment_result               评定结果表
road_condition_statistics       路况统计表

data_import_task                数据导入任务表
data_import_error_log           导入错误日志表

file_resource                   文件资源表
file_relation                   文件关联表

agent_session                   大模型会话表
agent_message                   大模型消息表
agent_tool_call_log             大模型工具调用日志表
knowledge_document              知识库文档表
knowledge_chunk                 知识片段表
```

## 5.2 多租户设计原则

所有业务表均增加：

```text
tenant_id
```

核心唯一索引需要包含 tenant_id。

例如：

```sql
UNIQUE(tenant_id, route_code)
UNIQUE(tenant_id, task_code)
UNIQUE(tenant_id, unit_code)
```

所有查询必须带租户条件：

```sql
WHERE tenant_id = 当前租户ID
```

## 5.3 核心表关系

```text
tenant 1 ─── N road_route
road_route 1 ─── N road_section
road_section 1 ─── N road_evaluation_unit

inspection_task 1 ─── N disease_record
inspection_task 1 ─── N assessment_result

road_evaluation_unit 1 ─── N disease_record
road_evaluation_unit 1 ─── N assessment_result

assessment_result 1 ─── N index_result

data_import_task 1 ─── N data_import_error_log

file_resource N ─── N disease_record / inspection_task / road_route
```

---

# 6. GIS 一张图页面设计

## 6.1 页面布局

```text
┌─────────────────────────────────────────────────────┐
│ 顶部导航：租户 / 行政区 / 路线 / 年度 / 任务 / 搜索    │
├───────────────┬───────────────────────┬─────────────┤
│ 左侧图层树     │        地图区域         │ 右侧详情面板 │
│               │                       │             │
│ 道路资产       │ 路线、病害、评定结果     │ 对象属性     │
│ 病害数据       │ 专题渲染、空间查询       │ 指标详情     │
│ 评定结果       │ 地图定位、高亮           │ 图片资料     │
│ 巡检轨迹       │                       │ 分析建议     │
├───────────────┴───────────────────────┴─────────────┤
│ 底部统计栏：里程 / 病害数 / MQI / 优良率 / 次差率       │
└─────────────────────────────────────────────────────┘
```

## 6.2 主要地图操作

```text
1. 路线定位
2. 桩号定位
3. 图层开关
4. 病害筛选
5. 评定等级筛选
6. 框选查询
7. 多边形查询
8. 点击查看详情
9. 地图范围统计
10. 导出当前查询结果
```

## 6.3 路况颜色建议

| 等级 | 编码 | 颜色建议 |
|---|---|---|
| 优 | EXCELLENT | 绿色 |
| 良 | GOOD | 蓝色 |
| 中 | MEDIUM | 黄色 |
| 次 | POOR | 橙色 |
| 差 | BAD | 红色 |

---

# 7. 技术架构建议

## 7.1 后端技术选型

| 类型 | 技术 |
|---|---|
| 后端框架 | Spring Boot |
| ORM | MyBatis Plus / JPA |
| 数据库 | PostgreSQL |
| 空间扩展 | PostGIS |
| 缓存 | Redis |
| 文件存储 | MinIO |
| 搜索 | PostgreSQL Full Text / Elasticsearch 可二期引入 |
| 异步任务 | Spring Task / XXL-JOB / Quartz |
| 消息队列 | RabbitMQ，可二期引入 |
| 权限认证 | Spring Security + JWT |
| API 文档 | Swagger / Knife4j |

## 7.2 前端技术选型

| 类型 | 技术 |
|---|---|
| 前端框架 | Vue 3 / React |
| 地图库 | Leaflet / OpenLayers / Mapbox GL |
| UI 组件 | Element Plus / Ant Design Vue |
| 图表 | ECharts |
| 文件上传 | 分片上传，可二期 |
| 地图服务 | GeoServer / 后端 GeoJSON API |

一期推荐：

```text
Vue 3 + Element Plus + Leaflet + ECharts
```

原因：

```text
轻量、易落地、对 GeoJSON 支持好、适合快速构建 GIS 一张图。
```

## 7.3 AI 大模型技术选型

| 类型 | 技术 |
|---|---|
| 大模型接入 | OpenAI API / 私有化大模型 / 国产大模型 |
| 知识库 | 文档切片 + 向量检索 |
| 向量库 | PostgreSQL pgvector / Milvus |
| 工具调用 | Function Calling / 自定义 Tool Router |
| Text2SQL | 受控 SQL 生成 + 查询白名单 |
| 报告生成 | 模板 + 大模型摘要 |
| 文档解析 | PDF / Word / Markdown 解析 |

一期推荐：

```text
PostgreSQL + pgvector + RAG + API工具调用
```

## 7.4 部署架构

```text
Nginx
  ↓
前端 Web
  ↓
API Gateway / Spring Boot
  ↓
业务服务
  ├── 道路资产服务
  ├── 病害服务
  ├── 巡检服务
  ├── 评定服务
  ├── GIS服务
  ├── 导入服务
  └── AI分析服务
  ↓
PostgreSQL + PostGIS
Redis
MinIO
大模型服务
```

一期可以采用单体模块化部署：

```text
srmp-admin
├── srmp-base
├── srmp-road-asset
├── srmp-inspection
├── srmp-disease
├── srmp-assessment
├── srmp-gis
├── srmp-import
├── srmp-agent
└── srmp-file
```

---

# 8. 推荐一期工程模块

```text
srmp-common              公共模块
srmp-security            登录认证、权限、多租户
srmp-base                字典、行政区划、管养单位
srmp-road-asset          路线、路段、评定单元
srmp-inspection          巡检任务、巡检轨迹
srmp-disease             病害管理
srmp-assessment          技术状况评定结果
srmp-gis                 GIS 一张图服务
srmp-import              数据导入服务
srmp-file                文件资料服务
srmp-agent               AI 大模型分析服务
srmp-dashboard           首页驾驶舱与统计
srmp-admin               启动模块
```

---

# 9. 一期接口设计

## 9.1 GIS 接口

```text
GET  /api/gis/layers
GET  /api/gis/road-routes
GET  /api/gis/road-sections
GET  /api/gis/evaluation-units
GET  /api/gis/diseases
GET  /api/gis/assessment-results
GET  /api/gis/inspection-tracks
POST /api/gis/spatial-query
GET  /api/gis/stake-location
```

## 9.2 道路资产接口

```text
POST /api/road-routes/page
GET  /api/road-routes/{id}
POST /api/road-routes
PUT  /api/road-routes/{id}
DELETE /api/road-routes/{id}

POST /api/road-sections/page
GET  /api/road-sections/{id}

POST /api/evaluation-units/page
GET  /api/evaluation-units/{id}
```

## 9.3 病害接口

```text
POST /api/diseases/page
GET  /api/diseases/{id}
POST /api/diseases
PUT  /api/diseases/{id}
POST /api/diseases/statistics
GET  /api/diseases/{id}/files
POST /api/diseases/{id}/review
```

## 9.4 巡检接口

```text
POST /api/inspection-tasks/page
GET  /api/inspection-tasks/{id}
POST /api/inspection-tasks
PUT  /api/inspection-tasks/{id}
GET  /api/inspection-tasks/{id}/tracks
GET  /api/inspection-tasks/{id}/diseases
```

## 9.5 评定结果接口

```text
POST /api/assessment-results/page
GET  /api/assessment-results/{id}
POST /api/assessment-results/statistics
GET  /api/assessment-results/by-unit/{unitId}
GET  /api/assessment-results/route-summary
```

## 9.6 数据导入接口

```text
POST /api/import/upload
POST /api/import/tasks
GET  /api/import/tasks/{id}
POST /api/import/tasks/{id}/parse
POST /api/import/tasks/{id}/check
POST /api/import/tasks/{id}/confirm
GET  /api/import/tasks/{id}/errors
GET  /api/import/templates/{dataType}
```

## 9.7 大模型分析接口

```text
POST /api/agent/chat
POST /api/agent/analyze/route
POST /api/agent/analyze/disease
POST /api/agent/analyze/assessment
POST /api/agent/report/assessment
POST /api/agent/map-query
GET  /api/agent/sessions
GET  /api/agent/sessions/{id}/messages
```

---

# 10. 数据导入模板设计

## 10.1 路线导入模板

| 字段 | 说明 | 必填 |
|---|---|---|
| route_code | 路线编号 | 是 |
| route_name | 路线名称 | 是 |
| route_type | 路线类型 | 是 |
| technical_grade | 技术等级 | 否 |
| start_stake | 起点桩号 | 是 |
| end_stake | 终点桩号 | 是 |
| length_km | 长度 | 否 |
| adcode | 行政区划 | 否 |
| manage_org_code | 管养单位 | 否 |
| geom | GeoJSON 或 WKT | 否 |

## 10.2 病害导入模板

| 字段 | 说明 | 必填 |
|---|---|---|
| route_code | 路线编号 | 是 |
| direction | 方向 | 否 |
| lane_no | 车道编号 | 否 |
| start_stake | 起点桩号 | 是 |
| end_stake | 终点桩号 | 否 |
| disease_type | 病害类型 | 是 |
| severity | 严重程度 | 否 |
| quantity | 数量 | 否 |
| measure_unit | 单位 | 否 |
| longitude | 经度 | 否 |
| latitude | 纬度 | 否 |
| geom | GeoJSON 或 WKT | 否 |
| image_urls | 图片地址 | 否 |
| inspection_task_code | 巡检任务编号 | 否 |

## 10.3 评定结果导入模板

| 字段 | 说明 | 必填 |
|---|---|---|
| route_code | 路线编号 | 是 |
| direction | 方向 | 否 |
| start_stake | 起点桩号 | 是 |
| end_stake | 终点桩号 | 是 |
| year | 年度 | 是 |
| mqi | MQI | 否 |
| sci | SCI | 否 |
| pqi | PQI | 否 |
| bci | BCI | 否 |
| tci | TCI | 否 |
| pci | PCI | 否 |
| rqi | RQI | 否 |
| rdi | RDI | 否 |
| pbi | PBI | 否 |
| pwi | PWI | 否 |
| sri | SRI | 否 |
| pssi | PSSI | 否 |
| grade | 评定等级 | 是 |

---

# 11. 大模型分析场景设计

## 11.1 场景一：路线综合分析

用户提问：

```text
分析 G210 2026 年整体路况。
```

系统处理：

```text
1. 识别路线编号 G210 和年份 2026；
2. 查询 assessment_result；
3. 查询 disease_record；
4. 聚合 MQI、PQI、PCI、病害数量；
5. 生成自然语言分析；
6. 返回可点击的地图定位结果。
```

## 11.2 场景二：病害热点分析

用户提问：

```text
哪些路段病害最集中？
```

系统处理：

```text
1. 按评定单元聚合病害数量；
2. 按严重程度加权；
3. 排序输出 TOP10；
4. GIS 地图高亮 TOP10 路段；
5. 大模型解释病害集中原因和建议。
```

## 11.3 场景三：次差路段识别

用户提问：

```text
把所有次差路段找出来，并分析原因。
```

系统处理：

```text
1. 查询 assessment_result 中 grade in POOR, BAD；
2. 关联 disease_record；
3. 分析低分指标；
4. 生成原因总结；
5. 地图高亮显示。
```

## 11.4 场景四：养护建议生成

用户提问：

```text
根据当前病害和评定结果，给出优先养护建议。
```

系统处理：

```text
1. 查询低 MQI / 低 PCI / 高病害密度路段；
2. 根据病害类型匹配养护措施；
3. 结合交通量、技术等级、严重程度计算优先级；
4. 生成建议清单；
5. 支持导出为 Excel 或报告。
```

## 11.5 场景五：自动报告生成

用户操作：

```text
选择路线 G210、年度 2026，点击生成评定分析报告。
```

系统生成：

```text
1. 路线基本情况；
2. 检测任务概况；
3. 技术状况评定结果；
4. 病害分布情况；
5. 重点问题路段；
6. 趋势分析；
7. 养护建议；
8. 附图和附表。
```

---

# 12. 一期实施步骤

## 12.1 阶段一：基础平台搭建

```text
1. 搭建后端工程
2. 搭建前端框架
3. 完成登录认证和多租户
4. 完成 PostgreSQL + PostGIS 初始化
5. 完成 MinIO 文件服务
6. 完成基础字典
```

## 12.2 阶段二：数据模型与导入

```text
1. 建立道路资产表
2. 建立病害表
3. 建立巡检任务表
4. 建立评定结果表
5. 实现 Excel/CSV 导入
6. 实现 GeoJSON/WKT 空间解析
7. 实现导入校验和错误日志
```

## 12.3 阶段三：GIS 一张图

```text
1. 实现路线图层
2. 实现路段图层
3. 实现评定单元图层
4. 实现病害图层
5. 实现评定结果专题图
6. 实现巡检轨迹图层
7. 实现图层控制和属性弹窗
8. 实现地图范围统计
```

## 12.4 阶段四：统计分析

```text
1. 首页驾驶舱
2. 路况统计
3. 病害统计
4. 巡检统计
5. 路线排名
6. 评定结果导出
```

## 12.5 阶段五：AI 大模型接入

```text
1. 接入大模型 API
2. 建立智能问答会话
3. 封装业务查询工具
4. 实现路况分析问答
5. 实现病害分析问答
6. 实现地图联动查询
7. 实现评定报告生成
```

---

# 13. 一期交付成果

## 13.1 系统功能成果

```text
1. GIS 一张图系统
2. 道路资产管理
3. 病害数据管理
4. 巡检任务管理
5. 评定结果管理
6. 数据导入中心
7. 统计驾驶舱
8. AI 大模型智能分析
9. 文件资料管理
10. 基础权限与多租户
```

## 13.2 数据成果

```text
1. 路线数据
2. 路段数据
3. 评定单元数据
4. 病害数据
5. 巡检轨迹数据
6. 技术状况评定数据
7. 文件影像资料
8. 知识库文档
```

## 13.3 文档成果

```text
1. 系统设计方案
2. 数据库设计文档
3. 数据导入模板
4. 接口文档
5. GIS 图层说明
6. 大模型工具说明
7. 部署文档
8. 用户操作手册
```

---

# 14. 一期验收标准

## 14.1 GIS 一张图验收

| 验收项 | 标准 |
|---|---|
| 路线展示 | 可展示路线空间线形 |
| 路段展示 | 可按路线查看路段 |
| 评定单元展示 | 可展示评定单元 |
| 病害展示 | 可展示点、线、面病害 |
| 评定专题图 | 可按 MQI、PCI 等渲染颜色 |
| 图层控制 | 可开关图层 |
| 属性查看 | 点击对象可查看详情 |
| 地图筛选 | 可按路线、年度、等级筛选 |
| 空间查询 | 支持框选或多边形查询 |

## 14.2 数据导入验收

| 验收项 | 标准 |
|---|---|
| Excel 导入 | 支持道路资产、病害、评定结果 |
| GeoJSON 导入 | 支持空间数据导入 |
| 字段映射 | 支持字段对应配置 |
| 数据校验 | 支持必填、格式、业务规则校验 |
| 错误日志 | 可下载错误明细 |
| 导入记录 | 可查看导入任务历史 |

## 14.3 AI 大模型验收

| 验收项 | 标准 |
|---|---|
| 路况问答 | 能按路线、年度回答路况情况 |
| 病害分析 | 能分析病害数量、类型、热点 |
| 评定解读 | 能解释 MQI、PQI、PCI 等结果 |
| 地图联动 | 查询结果可在地图高亮 |
| 报告生成 | 可生成路线评定分析报告 |
| 安全边界 | 不直接修改业务数据 |

---

# 15. 一期推荐技术方案总结

```text
后端：
Spring Boot + PostgreSQL + PostGIS + Redis + MinIO

前端：
Vue 3 + Element Plus + Leaflet + ECharts

GIS：
PostGIS 空间查询 + GeoJSON API + Leaflet 前端渲染

数据导入：
Excel / CSV / GeoJSON / WKT / Shapefile

AI 大模型：
RAG + 工具调用 + 受控 Text2SQL + 报告生成

部署：
Nginx + Java 服务 + PostgreSQL + Redis + MinIO + 大模型 API
```

---

# 16. 一期建设重点总结

一期系统的核心不是“工单流程”，而是先把数据和地图能力建设起来。

最重要的四个能力是：

```text
1. 数据能导入
2. 道路资产能上图
3. 病害和评定结果能分析展示
4. 大模型能基于真实数据做问答和辅助分析
```

建议一期最终形成：

```text
一个以 GIS 一张图为核心入口，
以道路资产、病害、巡检评定结果为核心数据，
以大模型智能分析为辅助能力，
可支撑养护管理人员进行路况研判和辅助决策的智能化平台。
```
