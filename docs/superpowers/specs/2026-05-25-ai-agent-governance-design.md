# AI Agent 能力与工具治理设计

## 背景

一张图 AI 养护助手已经具备地图对象分析、路线分析、区域分析、知识问答、方案生成和执行追踪能力。当前链路的核心能力分布在三处：

- Python orchestrator 的 `intent.py` 负责把用户请求识别成 intent。
- Python orchestrator 的 `planner.py` 和 `adaptive_planner.py` 负责决定调用哪些工具。
- Java `AiToolRegistry` 负责注册和执行真实业务工具。

这套结构已经能完成业务闭环，但“Agent 会什么”“为什么调用这个工具”“某个能力能不能禁用某个工具”“新工具如何接入某个能力”还没有形成统一治理模型。近期出现的“解释 PCI 指标却查询区域统计”就是典型症状：用户是在问知识，系统却因为当前地图上下文是路线模式而走了路线分析工具链。

本设计目标是把 AI Agent 从硬编码编排升级为可治理、可解释、可配置、可回放的能力平台。

## 当前问题

### 能力不是一等对象

系统当前的能力实际等于 intent/action 的组合，例如 `OBJECT_ANALYSIS`、`ROUTE_ANALYSIS`、`REGION_ANALYSIS`、`KNOWLEDGE_QA`、`SOLUTION_GENERATE`。这些定义散落在代码中：

- 哪些问题属于知识问答，由关键词判断。
- 哪些上下文会触发路线或区域分析，由 mode/object/action 判断。
- 哪些能力允许自动补充工具，由 adaptive planner 判断。

管理员无法在页面上看到完整能力清单，也无法知道每个能力的触发条件、上下文要求和工具边界。

### 工具可发现，但缺少治理元数据

Java 后端已经通过 `/api/agent/tools` 暴露工具清单，当前工具包括：

- `knowledge.retrieve`
- `gis.queryDiseases`
- `gis.queryAssessmentResults`
- `gis.queryDiseasesByStakeRange`
- `gis.queryRegionSummary`
- `gis.queryNearbyObjects`
- `template.match`
- `solution.generateDraft`

但工具元数据只有名称、描述和是否写工具，缺少：

- 输入输出 schema。
- 支持的业务范围。
- 数据口径和过滤规则。
- 所属能力。
- 是否可被 adaptive planner 自动追加。
- 健康状态、最近失败率和负责人。

### 编排策略硬编码

当前 `planner.py` 直接用 if/else 绑定 intent 与工具：

- 病害对象分析调用周边对象查询和知识检索。
- 评定结果调用评定查询、桩号范围病害查询和知识检索。
- 路线分析调用区域统计、评定、病害和知识检索。
- 区域分析调用区域统计和知识检索。

这让行为稳定但不透明。每次修正误判都需要改代码、发版、重启服务，管理员无法针对某个能力临时关闭某个工具。

### 客户侧与管理员侧语言混用

客户需要看到“正在分析当前路线、已参考病害和评定结果、建议如何处理”。管理员需要看到“intent、tool plan、answerMeta、fallbackReason、no embedded chunks、工具失败栈”。当前部分技术诊断会出现在客户操作链路中，容易造成困惑。

### 新工具和新能力缺少标准流程

开发一个新工具时，只要实现 `AiTool` 就能被注册；但它何时会被 planner 调用、是否允许自动追加、在前端如何展示、失败如何诊断，目前需要开发者分别修改多个位置。

## 目标

1. 建立统一的 AI 能力注册表，让“Agent 会什么”可查询、可测试、可解释。
2. 建立统一的 AI 工具注册表，让工具的读写风险、schema、适用范围、健康状态可管理。
3. 用策略引擎替代硬编码 planner，使每个能力的工具调用可配置、可版本化、可回滚。
4. 为客户提供业务化执行反馈，为管理员提供一屏排障视图。
5. 支持新工具和新能力按标准流程开发、验证、发布。
6. 保持现有 LangGraph Runtime、Java Tool Gateway 和一张图业务链路稳定，不一次性重写主流程。

## 非目标

- 不在第一阶段引入无限 ReAct 循环或自主多步任务执行。
- 不允许 AI 自动执行写操作、保存方案或派单；写工具仍需人工确认。
- 不把所有配置一开始就做成数据库动态热更新；先用版本化配置文件落地。
- 不把客户侧 AI 面板改造成管理员控制台。
- 不改变现有业务工具的数据表和查询口径，只把调用规则治理起来。

## 方案对比

### 方案 A：继续在 intent/planner 中补规则

优点：

- 改动最小。
- 单个 bug 修复快。

缺点：

- 能力和工具关系仍不可见。
- 每次误判都要改代码。
- 管理员无法理解或控制工具调用。
- 新工具接入仍然分散。

结论：只适合紧急 bugfix，不适合作为长期治理方案。

### 方案 B：数据库驱动的完整能力治理平台

优点：

- 能力、工具、策略全部可视化配置。
- 支持发布、回滚、权限、审计。

缺点：

- 初始改动大，涉及表结构、接口、前端、缓存、灰度。
- 如果没有先稳定配置模型，容易把错误抽象固化到数据库。

结论：适合作为最终形态，但不适合作为第一步。

### 方案 C：配置文件优先，逐步产品化

第一阶段新增版本化能力配置文件，orchestrator 读取配置生成工具计划；管理页面先做只读和模拟器。第二阶段再把配置落库，支持可视化编辑、发布和回滚。

优点：

- 能快速把硬编码 planner 收敛到统一模型。
- 配置可以随代码评审、测试和回滚。
- 管理页面可以先只读展示，降低风险。
- 后续落库时已有成熟 schema 和测试样本。

缺点：

- 第一阶段不能直接在页面编辑生效。
- 修改配置仍需发版或重启 orchestrator。

结论：采用方案 C。它能在控制风险的前提下建立正确治理边界。

## 总体架构

```text
用户问题 + 地图上下文
        |
        v
请求归一化
        |
        v
能力匹配 Capability Resolver
        |
        v
能力策略 Capability Policy
        |
        v
工具规划 Policy Planner
        |
        v
工具执行 Java Tool Gateway
        |
        v
证据融合 Evidence Fusion
        |
        v
回答生成 / 方案生成
        |
        v
客户反馈 + 管理员执行过程
```

核心新增模块：

- `Capability Registry`：定义能力、触发条件、上下文策略、输出契约。
- `Tool Registry Metadata`：补全工具 schema、风险、适用范围、健康状态。
- `Policy Planner`：根据能力策略规划工具，替代散落 if/else。
- `Governance UI`：能力矩阵、工具目录、编排策略、计划模拟器、追踪诊断。

## 能力注册表

能力是用户可理解的业务动作，不等同于底层 intent。一个能力可以映射到现有 intent/action，也可以细分为更明确的子能力。

### 能力字段

```yaml
id: knowledge.metric_explain
name: 指标解释
category: KNOWLEDGE
enabled: true
priority: 90
description: 解释 MQI、PQI、PCI、RQI、RDI、SCI、BCI、TCI 等评价指标含义和计算逻辑。
intent: KNOWLEDGE_QA
triggers:
  actions: []
  modes: []
  objectTypes: []
  includeKeywords:
    - 指标
    - PCI
    - MQI
    - PQI
    - RQI
    - RDI
    - SCI
    - BCI
    - TCI
  questionKeywords:
    - 解释
    - 说明
    - 含义
    - 定义
    - 是什么
    - 什么意思
    - 怎么计算
contextPolicy:
  mapContextUsage: OPTIONAL
  ignoreBusinessScopeByDefault: true
  businessScopeRequiresExplicitPhrase:
    - 当前
    - 这条
    - 这个对象
    - 结合地图
    - 为什么低
toolPolicy:
  required:
    - knowledge.retrieve
  optional: []
  adaptive: []
  prohibited:
    - gis.queryRegionSummary
    - gis.queryDiseases
    - gis.queryAssessmentResults
    - gis.queryDiseasesByStakeRange
    - gis.queryNearbyObjects
outputPolicy:
  answerType: KNOWLEDGE_EXPLANATION
  customerTone: BUSINESS
  adminDiagnostics: true
```

### 初始能力清单

| 能力 ID | 客户名称 | 触发来源 | 必须工具 | 可选工具 | 禁用重点 |
| --- | --- | --- | --- | --- | --- |
| `knowledge.metric_explain` | 指标解释 | “解释 PCI 指标”等 | `knowledge.retrieve` | 无 | 默认禁用 GIS |
| `knowledge.maintenance_qa` | 养护知识问答 | 规范、工艺、依据、怎么处理 | `knowledge.retrieve` | `template.match` | 默认禁用 GIS |
| `map.disease_analysis` | 病害分析 | 点击病害、分析病害 | `gis.queryNearbyObjects` | `knowledge.retrieve` | 禁用区域统计 |
| `map.assessment_analysis` | 评定结果分析 | 点击评定结果 | `gis.queryAssessmentResults`, `gis.queryDiseasesByStakeRange` | `knowledge.retrieve` | 不默认叫低分 |
| `map.section_analysis` | 路段分析 | 点击路段 | `gis.queryAssessmentResults`, `gis.queryDiseases`, `gis.queryDiseasesByStakeRange` | `knowledge.retrieve` | 无 |
| `map.route_analysis` | 路线分析 | 点击路线、分析路线 | `gis.queryRegionSummary`, `gis.queryAssessmentResults`, `gis.queryDiseases` | `knowledge.retrieve` | 无 |
| `map.region_analysis` | 区域分析 | 框选区域 | `gis.queryRegionSummary` | `knowledge.retrieve` | 必须携带 geometry 或明确降级 |
| `solution.object_generate` | 对象方案生成 | 生成对象方案 | 对应对象分析工具、`solution.generateDraft` | `knowledge.retrieve` | 写工具禁用 |
| `solution.route_report` | 路线报告生成 | 生成路线报告 | 路线分析工具、`solution.generateDraft` | `knowledge.retrieve` | 写工具禁用 |
| `solution.region_generate` | 区域方案生成 | 生成区域建议 | 区域分析工具、`solution.generateDraft` | `knowledge.retrieve` | 写工具禁用 |

## 工具注册表

Java `AiTool` 继续负责工具实现，新增工具治理元数据。第一阶段可以由 Java 接口聚合已有工具信息和配置文件中的补充元数据；第二阶段落库。

### 工具元数据字段

```yaml
name: gis.queryDiseasesByStakeRange
label: 查询桩号范围病害
category: GIS
readOnly: true
writeRisk: false
enabled: true
description: 按路线、方向和起止桩号查询病害记录。
owner: road-maintenance-ai
inputSchema:
  required:
    - routeCode
    - stakeStart
    - stakeEnd
  optional:
    - tenantId
    - projectId
    - year
    - direction
    - sectionTier
    - limit
scopeSupport:
  objectTypes:
    - ASSESSMENT_RESULT
    - ROAD_SECTION
  contextScopes:
    - OBJECT
    - ROUTE
resultContract:
  countField: totalCount
  itemField: items
  scopeField: queryScope
  warningField: scopeWarnings
governance:
  adaptiveAllowed: true
  customerVisible: false
  adminVisible: true
```

### 工具分类

- GIS 只读工具：查询病害、评定、区域统计、周边对象、桩号范围病害。
- 知识工具：知识库检索、未来的 query rewrite、hybrid search。
- 模板工具：模板匹配、模板变量校验。
- 方案工具：草稿生成、方案结构化。
- 写操作工具：保存草稿、派单、更新任务，默认不进入自动规划。

## 策略规划

### 规划原则

1. 显式 action 优先，例如 `ANALYZE_ROUTE` 一定走路线分析。
2. 明确知识问题优先于地图 mode，例如“解释 PCI 指标”不因为 `mode=ROUTE` 调路线工具。
3. 对象分析优先使用对象自身范围，不能被工具栏筛选路线覆盖。
4. 区域分析必须优先使用 geometry；缺少 geometry 时输出降级原因。
5. adaptive planner 只能追加当前能力允许的工具，不能突破 prohibited。
6. 写工具必须要求人工确认，不能被自动规划或 adaptive 追加。

### 能力匹配输出

```json
{
  "capabilityId": "knowledge.metric_explain",
  "intent": "KNOWLEDGE_QA",
  "confidence": 0.92,
  "matchedRules": [
    "keyword:PCI",
    "questionKeyword:解释",
    "businessScopeRequiresExplicitPhrase:not_found"
  ],
  "contextUsage": "KNOWLEDGE_ONLY"
}
```

### 工具规划输出

```json
{
  "capabilityId": "knowledge.metric_explain",
  "plannedTools": [
    {
      "toolName": "knowledge.retrieve",
      "reason": "指标解释类问题默认只检索知识库"
    }
  ],
  "prohibitedTools": [
    {
      "toolName": "gis.queryRegionSummary",
      "reason": "当前能力为知识解释，未要求结合当前路线"
    }
  ]
}
```

## 客户侧体验

客户侧只显示业务语言：

- 正在检索养护知识。
- 正在分析当前路线的评定结果和病害。
- 已结合当前评定单元内病害记录。
- 未查到当前范围内病害，建议检查筛选条件或扩大范围。

客户侧不显示：

- `answerMeta`
- `no embedded chunks`
- `旧接口`
- `LangGraph`
- `phase50`
- 原始工具异常栈

客户可点击“执行过程”查看简化版依据：

- 分析对象。
- 参考数据类型。
- 知识来源数量。
- 是否使用大模型。
- 是否存在业务数据不足。

## 管理员侧体验

新增或扩展 `/agent/ai-governance`，包含四个页签。

### 能力矩阵

展示：

- 能力 ID、名称、分类、启停状态。
- 触发条件和示例问法。
- 上下文要求。
- 工具策略摘要。
- 最近命中次数、误判反馈数、兜底率。

### 工具目录

展示：

- 工具名称、分类、读写风险、启停状态。
- 输入输出 schema。
- 最近成功率、平均耗时、失败原因 TopN。
- 被哪些能力引用。
- 示例执行和只读 dry-run。

### 编排策略

矩阵展示能力与工具关系：

- `required`
- `optional`
- `adaptive`
- `prohibited`
- `disabled`

第一阶段只读；第二阶段支持草稿编辑、校验、发布、回滚。

### Plan 模拟器

输入：

- 用户问题。
- action。
- mode。
- objectType。
- routeCode/year。
- geometry 是否存在。

输出：

- 命中的 capability。
- intent。
- planned tools。
- prohibited tools。
- source hints。
- warnings。
- adaptive 可能追加工具。

## 追踪与排障

`/agent/ai-traces` 和一张图执行过程抽屉复用同一执行快照。

管理员视图必须聚合：

- 请求摘要：问题、action、mode、对象、路线、桩号、区域。
- 能力匹配：capability、confidence、matchedRules。
- 计划工具：工具、原因、入参摘要、是否 required/adaptive。
- 实际工具：成功/失败、耗时、totalCount、returnedCount、scopeWarnings。
- 证据融合：业务证据、知识证据、模板证据。
- 模型状态：provider、model、llmStatus、fallbackReason。
- 偏差分析：计划缺失、额外工具、adaptive 追加、禁止工具是否被拦截。

常见问题要有明确归因：

- 知识库无结果：显示 query、topK、collection、embedding 状态。
- 业务数据为空：显示业务范围和过滤条件。
- 路线串线：显示上下文 routeCode、对象 routeCode、最终 queryScope。
- geometry 缺失：显示区域分析降级。
- LLM 空返回：显示模型状态和 fallback 内容来源。

## 后端接口设计

### 能力查询

```http
GET /api/agent/governance/capabilities
```

返回能力列表、触发摘要、工具策略摘要和启停状态。

### 工具查询

```http
GET /api/agent/governance/tools
```

聚合 Java Tool Gateway 的工具清单、配置元数据和运行统计。

### 计划模拟

```http
POST /api/agent/governance/plan-simulate
```

内部复用 `/api/srmp/langgraph/debug/plan`，但返回 governance 视图：

- capability。
- matchedRules。
- plannedTools。
- prohibitedTools。
- warnings。
- sourceHints。

### 策略校验

```http
POST /api/agent/governance/policies/validate
```

校验能力配置：

- 工具名必须存在。
- 写工具不能出现在 required/adaptive 中，除非明确 `requiresConfirmation=true`。
- 每个 enabled 能力至少有一个 required 或 optional 工具。
- prohibited 不能同时出现在 required。
- 示例问法必须能命中当前能力。

## 配置文件设计

第一阶段新增：

```text
srmp-ai-orchestrator/app/governance/capabilities.yaml
srmp-ai-orchestrator/app/governance/tools.yaml
```

加载规则：

1. 启动时加载默认配置。
2. 配置错误时服务启动失败，避免使用半配置状态。
3. 环境变量可指定配置路径，支持部署覆盖。
4. debug/health 暴露配置版本和校验结果。

第二阶段新增数据库表：

- `ai_capability_config`
- `ai_tool_metadata`
- `ai_policy_version`
- `ai_policy_publish_log`

发布规则：

- 草稿修改不影响运行。
- 发布后生成不可变版本。
- orchestrator 读取当前 ACTIVE 版本。
- 回滚本质上是把旧版本重新标记为 ACTIVE。

## 新工具开发流程

1. Java 实现 `AiTool`。
2. 补充工具单元测试，验证成功、空结果、参数缺失、异常。
3. 在工具元数据中声明 schema、范围、读写风险、结果契约。
4. 加入 `SRMP_LANGGRAPH_ALLOWED_TOOLS` 或默认白名单。
5. 在能力策略中选择 required、optional 或 adaptive。
6. 补充 orchestrator plan 测试，验证哪些问题会调用该工具。
7. 补充 trace 展示标签和管理员排障字段。
8. 通过 plan 模拟器和至少一条真实工具执行 smoke test。

## 新能力开发流程

1. 定义能力 ID、名称、分类和用户故事。
2. 定义触发条件和排除条件。
3. 定义上下文策略：是否需要对象、路线、桩号、区域、知识库。
4. 定义工具策略：required、optional、adaptive、prohibited。
5. 定义输出契约：回答、报告、方案、任务草稿。
6. 编写示例问法和反例问法。
7. 添加 plan 测试和端到端 smoke test。
8. 在治理页面展示并纳入 trace 统计。

## 迁移计划

### P0：已完成的紧急修复

- 指标解释类问题优先识别为 `KNOWLEDGE_QA`。
- 在路线上下文下，“解释 PCI 指标”只规划 `knowledge.retrieve`。
- 加回归测试覆盖该行为。

### P1：能力配置文件

- 新增 `capabilities.yaml` 和 `tools.yaml`。
- 新增 capability resolver，输出 capabilityId、confidence、matchedRules。
- planner 读取能力工具策略生成计划。
- adaptive planner 遵守当前能力的 prohibited tools。
- 保留旧 planner fallback，配置缺失时可回退。

### P2：只读治理页面

- `/agent/ai-governance` 增加能力矩阵、工具目录、编排策略和 plan 模拟器。
- `/agent/ai-traces` 增加 capability、matchedRules、prohibitedTools、计划原因。
- 客户侧隐藏技术诊断词。

### P3：策略校验与评测集

- 增加 policy validate 接口。
- 增加能力级回归评测集。
- 每个能力至少覆盖正例、反例、上下文冲突、工具禁用。
- CI 或脚本可运行治理策略测试。

### P4：配置落库与发布

- 能力和工具策略落库。
- 支持草稿、发布、回滚、审计。
- 支持按租户或项目灰度，默认仍使用全局 ACTIVE 版本。

## 验收标准

### 功能验收

- 管理员能查看 Agent 当前所有能力。
- 管理员能查看所有工具及其被哪些能力使用。
- Plan 模拟器能解释“为什么调用这些工具”和“为什么没有调用某些工具”。
- “解释 PCI 指标”在 ROUTE/OBJECT/REGION 上下文下默认只走知识库。
- “结合当前路线解释 PCI 为什么低”会走知识库和路线业务证据。
- 病害、评定、路段、路线、区域分析仍调用各自业务工具。
- 方案生成复用对应分析工具链和 `solution.generateDraft`。

### 安全验收

- 写工具默认不会出现在自动计划中。
- adaptive planner 不能突破 prohibited tools。
- 配置校验能拦截不存在的工具、冲突策略和缺少 required 工具的能力。

### 可观测验收

- 每次执行记录 capabilityId、matchedRules、plannedTools、actualTools。
- trace 能显示计划与实际差异。
- 工具失败、知识库无结果、业务数据为空、LLM 兜底有明确归因。

### 用户体验验收

- 客户侧不展示 `answerMeta`、`no embedded chunks`、`LangGraph`、`phase` 等技术诊断。
- 客户侧执行过程用业务语言说明当前在分析什么和参考了什么。
- 管理员侧保留完整诊断信息。

## 风险与缓解

### 风险：能力匹配配置过宽导致误判

缓解：

- 每个能力必须有反例测试。
- 高优先级知识能力需要排除“分析当前路线”等业务动作。
- plan 模拟器显示 matchedRules，便于快速定位误判。

### 风险：配置文件和代码行为不一致

缓解：

- 启动时校验配置。
- 单元测试直接读取配置。
- health 输出配置版本。
- 保留旧 planner fallback，但生产默认必须配置校验通过。

### 风险：管理员误配导致工具被错误调用

缓解：

- 第一阶段只读。
- 第二阶段使用草稿、校验、发布、回滚。
- 写工具必须额外确认和权限控制。

### 风险：治理页面过重影响当前业务迭代

缓解：

- 先做只读矩阵和模拟器，复用现有 `/agent/ai-traces`、plan preview 和 tool list。
- 编辑发布能力放到 P4。

## 推荐下一步

先实施 P1 和 P2 的只读治理闭环：

1. 把现有 intent/planner 行为搬到配置文件。
2. 保持现有 API 响应兼容。
3. 在 plan 和 trace 中新增 capability 信息。
4. 做只读治理页面和 plan 模拟器。

这样能先解决“看不见、说不清、不可测”的根问题，再进入数据库配置和可视化发布阶段。
