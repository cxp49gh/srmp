# 阶段三十五：方案模板生效验证与模板管理增强

## 1. 背景

阶段三十二已经完成：

```text
地图对象 → 生成方案草稿 → 预览 → 复制 / 下载
```

阶段三十三已经完成：

```text
方案草稿 → 保存为 ai_solution_task → 历史版本 → 状态管理
```

阶段三十四已经完成：

```text
框选区域 → 区域分析 → 区域养护建议 → AI Trace
```

下一步需要解决一个关键问题：

```text
生成出来的方案，是否真的使用了模板？
使用的是哪个模板？
模板变量是否正确填充？
模板修改后是否能快速生效？
如果没有命中模板，系统为什么走了兜底？
```

因此阶段三十五建议建设：

```text
方案模板生效验证与模板管理增强
```

目标是让 AI 方案生成从“能生成”进一步升级为“可配置、可验证、可追踪、可管理”。

---

## 2. 阶段目标

阶段三十五目标：

```text
模板匹配
  ↓
变量构建
  ↓
模板渲染
  ↓
变量校验
  ↓
方案生成
  ↓
Trace 可见
  ↓
前端可验证
```

核心目标：

1. 确认方案生成是否命中模板；
2. 展示命中的模板名称、编码、版本；
3. 展示模板匹配原因；
4. 展示模板变量填充结果；
5. 支持模板渲染预览；
6. 支持模板启用、停用、默认模板、优先级；
7. 支持模板版本管理；
8. AI Trace 中记录模板匹配、渲染、校验过程；
9. 方案任务中保留模板来源信息；
10. 前端可以验证“模板是否真的生效”。

---

## 3. 需要解决的问题

当前用户看到的是最终 Markdown 方案，但无法判断它来自：

```text
模板生成
模板 + 知识库增强
模板 + 大模型润色
本地兜底规则
纯大模型回答
```

阶段三十五需要回答 5 个问题：

```text
1. 本次生成是否命中了模板？
2. 命中的是哪个模板？
3. 命中的是哪个模板版本？
4. 模板变量是否正确填充？
5. 如果未命中模板，为什么走兜底？
```

---

## 4. 总体设计

### 4.1 后端统一模板生成入口

阶段三十五不建议让路线报告、地图对象方案、区域方案分别实现模板匹配和渲染逻辑。应新增统一模板生成管线，例如：

```text
AiSolutionTemplatePipelineService
```

所有方案生成入口统一调用该管线：

```text
路线报告 / 地图对象方案 / 框选区域方案
  ↓
构建业务上下文 SolutionTemplateContext
  ↓
调用统一模板生成管线
  ↓
返回 markdown + templateMeta + sourceSummaries + trace steps
```

各业务服务只负责准备上下文：

| 业务入口 | 职责 |
|---|---|
| `AiSolutionGenerateServiceImpl` | 路线报告上下文、路线/评定/病害业务摘要 |
| `MapObjectSolutionServiceImpl` | 地图对象详情、对象摘要、对象质量检查所需字段 |
| `MapRegionSolutionServiceImpl` | 区域统计、热点、知识库/大纲检索上下文 |

模板匹配、变量构建、模板渲染、变量校验、模板来源记录、Trace step 名称和返回结构必须由统一管线负责，避免后续每一种生成方式都形成一套模板逻辑。

### 4.2 统一方案生成链路

建议生成链路调整为：

```text
地图对象 / 框选区域
  ↓
构建业务上下文
  ↓
统一模板管线
  ↓
构建模板变量
  ↓
匹配模板
  ↓
渲染模板
  ↓
变量检查
  ↓
结合知识库 / 业务数据 / AI 润色
  ↓
生成方案 Markdown
  ↓
质量检查
  ↓
统一返回 markdown + templateMeta + trace + sourceSummaries
```

### 4.3 统一模板类型

模板分类建议统一为三层，后端、前端、DDL、验收脚本都使用同一套字段含义：

```text
originType：生成入口 / 来源场景
objectType：业务对象类型
solutionType：方案类型
```

推荐第一版支持：

| 字段 | 示例值 | 说明 |
|---|---|---|
| `originType` | `ROUTE_REPORT` / `MAP_OBJECT` / `MAP_REGION` / `GENERAL_SOLUTION` | 从哪个入口生成 |
| `objectType` | `ROAD_ROUTE` / `DISEASE` / `ASSESSMENT_RESULT` / `ROAD_SECTION` / `MAP_REGION` | 方案作用对象 |
| `solutionType` | `ROAD_ASSESSMENT_REPORT` / `DISEASE_TREATMENT` / `ROUTE_REPORT` / `REGION_MAINTENANCE_SUGGESTION` | 具体方案类别 |

普通问答继续统一使用 AI Trace，但不强制进入方案模板体系；如果后续需要问答提示词模板，再单独扩展 `GENERAL_QA_PROMPT` 类型。

### 4.4 不同模板来源的统一流程

模板可以来自多个入口：

```text
手工新建
知识库导入
复制已有模板生成新版本
系统内置模板
后续可选 AI 辅助生成模板
```

但进入系统后必须走同一套生命周期：

```text
解析变量
  ↓
保存模板和版本
  ↓
渲染预览
  ↓
变量检查
  ↓
启用 / 停用 / 设为默认 / 调整优先级
  ↓
方案生成时统一匹配和记录 templateMeta
```

因此“从知识库登记为模板”和“手工创建模板”只是前端入口不同，落库字段、版本规则、变量解析和生效验证规则应完全一致。

### 4.5 模板来源记录

生成结果中应增加：

```json
{
  "templateMeta": {
    "matched": true,
    "templateId": "tpl_xxx",
    "templateCode": "DISEASE_TREATMENT_DEFAULT",
    "templateName": "病害处置建议默认模板",
    "templateVersion": 3,
    "solutionType": "DISEASE_TREATMENT",
    "objectType": "DISEASE",
    "originType": "MAP_OBJECT",
    "matchReason": "objectType=DISEASE, solutionType=DISEASE_TREATMENT",
    "fallback": false,
    "missingVariables": [],
    "unusedVariables": []
  }
}
```

如果未命中模板：

```json
{
  "templateMeta": {
    "matched": false,
    "fallback": true,
    "fallbackReason": "未找到启用的 DISEASE_TREATMENT + DISEASE 模板，使用系统内置兜底模板"
  }
}
```

---

## 5. 数据库设计建议

### 5.1 ai_solution_template 字段增强

现有 `ai_solution_template` 已有 `template_code`、`solution_type`、`current_version`、`status` 等字段。阶段三十五不建议再新增 `enabled`、`version_no` 等与现有字段并行的状态/版本字段，避免双轨维护。

建议在现有表上补齐模板适用范围和匹配优先级：

```sql
ALTER TABLE ai_solution_template
ADD COLUMN IF NOT EXISTS object_type VARCHAR(100),
ADD COLUMN IF NOT EXISTS origin_type VARCHAR(100),
ADD COLUMN IF NOT EXISTS is_default BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS priority INTEGER DEFAULT 0;
```

字段说明：

| 字段 | 说明 |
|---|---|
| `object_type` | 适用对象类型 |
| `origin_type` | 来源场景，如 `ROUTE_REPORT` / `MAP_OBJECT` / `MAP_REGION` |
| `is_default` | 是否默认模板 |
| `priority` | 匹配优先级 |
| `status` | 继续沿用 `ENABLED` / `DISABLED` 表示启停 |
| `current_version` | 继续沿用现有字符串版本，如 `v1` / `v2` |

### 5.2 ai_solution_template_version

已有 `ai_solution_template_version`，建议继续复用现有字段：

```text
version
content
variables
content_hash
source_url
published_at
```

如需支持版本说明，可增量增加：

```sql
ALTER TABLE ai_solution_template_version
ADD COLUMN IF NOT EXISTS change_note VARCHAR(500),
ADD COLUMN IF NOT EXISTS created_by VARCHAR(64);
```

### 5.3 ai_solution_task 保存模板来源

阶段三十三已复用 `ai_solution_task` 管理方案任务。阶段三十五建议增加或确认：

```sql
ALTER TABLE ai_solution_task
ADD COLUMN IF NOT EXISTS template_meta JSONB;
```

`template_id`、`template_version` 已存在，应继续沿用；`template_meta` 保存匹配原因、兜底原因、缺失变量、未使用变量、渲染摘要等结构化信息。

### 5.4 ai_trace_step 保存模板 step 明细

当前 Trace step 已能记录名称、状态、耗时、数量和错误。若前端需要展示模板编码、版本、变量缺失列表等细节，建议补充：

```sql
ALTER TABLE ai_trace_step
ADD COLUMN IF NOT EXISTS step_data JSONB;
```

如果第一版不扩 Trace 表，则模板细节至少必须保存在 `template_meta` 和 `ai_solution_source` 中，Trace step 只展示成功/失败和耗时。

### 5.5 ai_solution_source 记录模板来源

建议继续复用 `ai_solution_source`，新增来源类型：

```text
TEMPLATE
TEMPLATE_RENDER
TEMPLATE_VARIABLE
```

用于记录：

```text
模板ID
模板版本
模板变量
渲染结果摘要
兜底原因
```

---

## 6. 模板匹配规则

### 6.1 推荐匹配条件

模板匹配建议基于：

```text
tenantId
originType
objectType
solutionType
status = ENABLED
priority
isDefault
currentVersion
```

### 6.2 匹配优先级

推荐规则：

```text
1. tenantId + originType + objectType + solutionType + status=ENABLED
2. priority desc
3. is_default desc
4. current_version / updated_at desc
5. 系统内置兜底模板
```

如果用户显式指定 `templateId` 或 `templateCode`，应先校验该模板是否属于当前租户、是否启用、是否适用于当前 `originType/objectType/solutionType`。校验失败时返回明确 warning 或 fallback reason，而不是静默切换到其他模板。

同一 `tenantId + originType + objectType + solutionType` 下建议只允许一个默认模板：

```text
唯一默认模板约束：tenantId + originType + objectType + solutionType + is_default=true + deleted=false
```

### 6.3 示例

病害处置建议：

```text
solutionType = DISEASE_TREATMENT
objectType = DISEASE
originType = MAP_OBJECT
```

框选区域养护建议：

```text
solutionType = REGION_MAINTENANCE_SUGGESTION
objectType = MAP_REGION
originType = MAP_REGION
```

路线技术状况报告：

```text
solutionType = ROAD_ASSESSMENT_REPORT
objectType = ROAD_ROUTE
originType = ROUTE_REPORT
```

---

## 7. 模板变量设计

### 7.1 地图对象变量

适用于 `MAP_OBJECT`：

```text
routeCode
routeName
objectType
objectId
startStake
endStake
stakeRange
diseaseName
diseaseType
severity
quantity
measureUnit
mqi
pqi
pci
grade
year
```

### 7.2 区域变量

适用于 `MAP_REGION`：

```text
routeCode
year
regionName
bbox
geometry
routeCount
sectionCount
assessmentCount
diseaseCount
avgMqi
avgPqi
avgPci
lowScoreCount
heavyDiseaseCount
mediumDiseaseCount
hotspots
```

### 7.3 知识库变量

```text
knowledgeSources
outlineSources
templateSources
businessRules
professionalGuidelines
```

---

## 8. 模板渲染预览

### 8.1 新增接口

```http
POST /api/ai/solution/templates/{id}/render-preview
```

### 8.2 请求示例

```json
{
  "originType": "MAP_OBJECT",
  "objectType": "DISEASE",
  "solutionType": "DISEASE_TREATMENT",
  "variables": {
    "routeCode": "G210",
    "startStake": 69.007,
    "endStake": 69.034,
    "diseaseName": "修补损坏",
    "severity": "MEDIUM",
    "quantity": 11.02,
    "measureUnit": "m2"
  }
}
```

### 8.3 返回示例

```json
{
  "code": 0,
  "data": {
    "templateId": "tpl_xxx",
    "templateCode": "DISEASE_TREATMENT_DEFAULT",
    "templateVersion": 3,
    "renderedMarkdown": "# G210 修补损坏处置建议\n...",
    "variables": {
      "routeCode": "G210",
      "diseaseName": "修补损坏"
    },
    "missingVariables": [],
    "unusedVariables": [],
    "warnings": []
  }
}
```

---

## 9. 模板匹配预览

### 9.1 新增接口

```http
POST /api/ai/solution/templates/match-preview
```

### 9.2 请求

```json
{
  "tenantId": "default",
  "originType": "MAP_OBJECT",
  "objectType": "DISEASE",
  "solutionType": "DISEASE_TREATMENT"
}
```

### 9.3 返回

```json
{
  "matched": true,
  "templateId": "tpl_xxx",
  "templateCode": "DISEASE_TREATMENT_DEFAULT",
  "templateName": "病害处置建议默认模板",
  "templateVersion": 3,
  "matchReason": "按 solutionType + objectType + originType 匹配，priority=100"
}
```

---

## 10. 后端模块设计

建议新增或增强：

```text
AiSolutionTemplatePipelineService
AiSolutionTemplateMatchService
AiSolutionTemplateRenderService
AiSolutionTemplateVariableBuilder
AiSolutionTemplateVariableChecker
AiSolutionTemplateTraceHelper
```

### 10.1 AiSolutionTemplatePipelineService

职责：

```text
作为后端唯一模板生成入口
接收 SolutionTemplateContext
编排模板匹配、变量构建、渲染、校验、来源记录和 Trace
返回 TemplatePipelineResult
```

建议上下文结构：

```text
SolutionTemplateContext
- tenantId
- originType
- objectType
- solutionType
- routeCode
- year
- mapObject
- objectSummary
- regionSummary
- businessData
- knowledgeSources
- outlineSources
- options
```

建议返回结构：

```text
TemplatePipelineResult
- markdown
- templateMeta
- variables
- renderedMarkdown
- missingVariables
- unusedVariables
- warnings
- sourceSummaries
```

### 10.2 AiSolutionTemplateMatchService

职责：

```text
根据 tenantId / solutionType / objectType / originType 匹配模板
处理优先级、版本、默认模板、兜底模板
返回 TemplateMeta
```

### 10.3 AiSolutionTemplateRenderService

职责：

```text
接收模板内容和变量
渲染 Markdown
返回 renderedMarkdown
返回 missingVariables / unusedVariables
```

### 10.4 AiSolutionTemplateVariableBuilder

职责：

```text
从 mapObject / regionSummary / businessData / knowledgeSources 构建模板变量
统一变量名
处理空值
格式化桩号、评分、等级
```

变量构建器应按 `originType + objectType + solutionType` 分支，但输出变量命名保持统一。例如区域统计中的 `diseaseSummary.disease_count` 应统一映射为 `diseaseCount`，评定统计中的 `avg_mqi` 应统一映射为 `avgMqi`。

### 10.5 AiSolutionTemplateVariableChecker

职责：

```text
检查模板声明变量与实际变量
识别缺失变量
识别未使用变量
给出 warnings
```

变量缺失不能静默替换为空字符串。第一版建议：

```text
缺失关键变量：允许生成，但 templateMeta.missingVariables 和 warnings 必须展示
模板完全未命中：使用系统兜底模板，同时记录 fallbackReason
渲染异常：返回明确错误，并在 Trace 标记 template_render FAILED
```

### 10.6 现有服务接入边界

现有生成服务改造边界建议如下：

| 服务 | 改造方式 |
|---|---|
| `AiSolutionGenerateServiceImpl` | 保留路线业务分析，模板部分改为调用 Pipeline |
| `MapObjectSolutionServiceImpl` | 保留对象上下文和质量检查，Markdown 生成改为调用 Pipeline |
| `MapRegionSolutionServiceImpl` | 保留区域分析、热点、知识库检索，Markdown 生成改为调用 Pipeline |
| `AiSolutionDraftServiceImpl` | 保存 `templateMeta`、模板来源和变量摘要 |

---

## 11. AI Trace 增强

阶段三十四已经完成 AI Trace。阶段三十五应增加模板相关 step：

```text
template_match
template_variable_build
template_render
template_validate
solution_generate
quality_check
```

### 11.1 Trace step 示例

```json
{
  "name": "template_match",
  "label": "模板匹配",
  "status": "SUCCESS",
  "costMs": 18,
  "count": 1,
  "data": {
    "templateCode": "DISEASE_TREATMENT_DEFAULT",
    "templateVersion": 3,
    "matched": true,
    "fallback": false
  }
}
```

### 11.2 变量校验 step

```json
{
  "name": "template_validate",
  "label": "模板变量校验",
  "status": "SUCCESS",
  "data": {
    "missingVariables": [],
    "unusedVariables": ["extraNote"],
    "warnings": []
  }
}
```

---

## 12. 前端设计

### 12.1 统一前端模板展示协议

前端不应为对象方案、区域方案、路线报告分别实现模板信息展示。建议统一消费以下字段：

```text
templateMeta
trace
sourceSummaries
qualityCheck
```

并复用统一组件：

```text
TemplateMetaCard
TemplateVariableCheckPanel
TemplateRenderPreviewDialog
AiTraceDrawer
```

这些组件应被以下页面共用：

```text
地图对象方案预览
区域方案预览
路线报告任务详情
方案任务详情
模板管理页验证结果
```

### 12.2 SolutionPreviewDialog 显示模板信息

在方案预览弹窗中增加：

```text
生成模板
- 模板名称
- 模板编码
- 模板版本
- 匹配原因
- 是否兜底
- 缺失变量
```

如果未命中模板：

```text
未命中模板，已使用系统兜底模板
原因：未找到启用的 DISEASE_TREATMENT + DISEASE 模板
```

### 12.3 SolutionTasksPage 显示模板来源

方案任务列表和详情中增加：

```text
模板名称
模板版本
生成来源
Trace ID
```

支持点击查看：

```text
模板内容
变量填充结果
AI Trace
```

### 12.4 模板管理页增强

模板管理页建议支持：

```text
启用 / 停用
设为默认
设置优先级
复制为新版本
预览渲染
变量检测
查看历史版本
适用对象类型
适用方案类型
适用来源类型
```

### 12.5 模板生效验证按钮

模板列表中增加：

```text
验证
```

点击后可选择：

```text
病害样例
评定结果样例
框选区域样例
手动 JSON
```

返回：

```text
命中模板
变量填充结果
缺失变量
渲染 Markdown
```

验证结果展示应与方案预览一致，使用同一套 `TemplateMetaCard` 和 `TemplateVariableCheckPanel`，避免模板管理页和生成预览页对同一个 templateMeta 有两种解释。

---

## 13. 前端新增组件建议

```text
TemplateMetaCard.vue
TemplateRenderPreviewDialog.vue
TemplateVariableCheckPanel.vue
```

`TemplateTracePanel` 不建议单独新增，Trace 展示继续复用现有 `AiTraceDrawer`。模板相关 Trace step 只作为现有 Trace 的一部分展示，避免形成第二套 Trace UI。

---

## 14. API 建议

### 14.1 模板渲染预览

```http
POST /api/ai/solution/templates/{id}/render-preview
```

### 14.2 模板匹配预览

```http
POST /api/ai/solution/templates/match-preview
```

### 14.3 模板启用 / 停用

```http
POST /api/ai/solution/templates/{id}/status
```

请求：

```json
{
  "status": "ENABLED"
}
```

`status` 取值沿用现有模板表：`ENABLED` / `DISABLED`。

### 14.4 设为默认模板

```http
POST /api/ai/solution/templates/{id}/default
```

### 14.5 复制为新版本

```http
POST /api/ai/solution/templates/{id}/versions
```

---

## 15. 验收方案

阶段三十五验收必须能证明模板真的生效。

### 15.1 病害模板验证

1. 新建或启用模板：

```text
templateCode = DISEASE-TPL-001
solutionType = DISEASE_TREATMENT
objectType = DISEASE
originType = MAP_OBJECT
```

2. 模板正文里写明显标识：

```text
【模板编号：DISEASE-TPL-001】
```

3. 点击病害对象生成方案；

4. 验证生成结果包含：

```text
【模板编号：DISEASE-TPL-001】
```

5. 验证方案预览显示：

```text
模板：DISEASE-TPL-001
版本：v1
```

6. 验证 AI Trace 中有：

```text
template_match
template_render
template_validate
```

### 15.2 模板变量验证

模板内容：

```text
路线：{{routeCode}}
病害：{{diseaseName}}
桩号：{{startStake}} - {{endStake}}
```

生成结果应显示：

```text
路线：G210
病害：修补损坏
桩号：69.007 - 69.034
```

### 15.3 模板版本验证

1. 修改模板内容；
2. 发布 v2；
3. 再次生成方案；
4. 方案预览显示模板版本 v2；
5. 内容体现 v2 改动。

---

## 16. 脚本建议

新增：

```text
scripts/check-phase35-template-effectiveness.sh
```

检查项：

```text
1. 模板匹配服务存在；
2. 模板渲染服务存在；
3. 模板变量构建器存在；
4. render-preview 接口存在；
5. match-preview 接口存在；
6. templateMeta 返回字段存在；
7. AI Trace 中包含 template_match；
8. SolutionPreviewDialog 显示模板信息；
9. 模板管理页支持启用/默认/预览。
```

---

## 17. 推荐实施顺序

```text
1. 梳理现有 ai_solution_template 表和接口；
2. 补齐 object_type / origin_type / priority / is_default / template_meta 等字段；
3. 定义统一 SolutionTemplateContext 和 TemplatePipelineResult；
4. 实现 AiSolutionTemplatePipelineService；
5. 实现模板匹配、变量构建、渲染、变量校验；
6. 接入地图对象方案和区域方案生成链路；
7. 保存 templateMeta、模板变量摘要和模板来源到任务/source；
8. 增强 AI Trace，必要时补充 step_data JSONB；
9. 前端预览弹窗和任务详情统一显示 TemplateMetaCard；
10. 模板管理页增加验证、启停、默认、优先级和预览能力；
11. 增加验收脚本和文档。
```

---

## 18. 最小闭环

阶段三十五第一版最小闭环：

```text
统一模板管线
  ↓
模板匹配
  ↓
模板变量填充
  ↓
模板渲染
  ↓
方案生成结果包含模板标识
  ↓
SolutionPreviewDialog 显示 templateMeta
  ↓
AI Trace 显示 template_match / template_render
```

第一版不建议做复杂审批、模板灰度、多租户模板继承等高级功能。

第一版也不建议把普通问答纳入方案模板体系；普通问答继续保持 Trace 可见，方案类生成优先完成统一模板管线。

---

## 19. 风险与注意事项

### 19.1 AI 自由生成覆盖模板结构

如果后续还要让大模型润色，应明确提示：

```text
必须保留模板标题、章节结构和模板编号。
```

### 19.2 模板变量缺失

变量缺失时不要静默生成空内容，应返回 warnings，并在前端显示。

### 19.3 模板缓存

如果使用模板缓存，修改模板后要支持刷新缓存，否则用户会误以为模板未生效。

### 19.4 兜底模板

必须明确记录兜底原因，避免用户误以为命中了业务模板。

---

## 20. 结论

阶段三十五非常有必要。

它解决的是：

```text
AI 生成方案是否可控
模板是否真的生效
模板变量是否正确填充
模板版本是否可追踪
生成过程是否可解释
```

建议阶段名称：

```text
阶段三十五：方案模板生效验证与模板管理增强
```

阶段三十五完成后，平台会从：

```text
AI 能生成方案
```

升级为：

```text
AI 按可管理模板生成方案，并且全过程可验证、可追踪、可维护
```
