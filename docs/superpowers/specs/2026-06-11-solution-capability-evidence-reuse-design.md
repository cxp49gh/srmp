# 方案子能力与分析证据复用设计

## 背景

一张图 AI 助手现在已经支持路线、路段、病害、评定结果、区域的分析，以及路线报告、路段计划、病害建议、评定建议、区域建议等生成动作。当前主要问题有两个：

- 治理能力层只有 `solution.generate` 这类大能力，客户和管理员看到的却是多种具体成果，语义不一致。
- 用户先点“分析”后再点“生成”时，生成链路仍可能重复查询相同业务数据和知识证据，成本、耗时和 trace 解释都不够理想。

这两个问题需要一起设计：能力拆分解决“生成的到底是什么”，证据复用解决“生成基于哪一次分析”。

## 目标

1. 将方案生成从单一大能力拆成具体业务成果能力，让治理、trace、验收、按钮文案一致。
2. 保留共用方案生成管线，避免复制六套生成实现。
3. 分析完成后形成可复用证据快照，生成时优先复用一致范围内的分析证据。
4. 让 trace 能说明生成是否基于某次分析、复用了哪些证据、补查了哪些工具。
5. 保持直接生成可用：用户没有先分析时，生成内部仍应完整构建证据链。

## 非目标

- 不重写 LangGraph 主流程。
- 不把父子能力做成复杂继承树或动态策略覆盖。
- 不让分析结果自动保存为正式方案。
- 不改变 GIS 工具的数据口径。
- 不在第一阶段引入长期持久化证据库；先以短期快照和 trace 关联为主。

## 能力模型

采用“轻量父子”模型。

### 父能力

`solution.generate` 只作为能力族，不作为最终命中能力返回。

用途：

- 统计方案生成类能力；
- 标识共用管线 `solution_generate`；
- 在治理台中分组展示；
- 作为后续新增成果能力的归属。

### 子能力

子能力是实际命中、展示、治理、验收的单位：

| 能力 ID | 名称 | 适用范围 | solutionType | 期望模板 |
| --- | --- | --- | --- | --- |
| `solution.route_report` | 生成路线养护报告 | 路线 | `ROUTE_REPORT` | `route_report_default` |
| `solution.section_plan` | 生成路段养护计划 | 路段 | `SECTION_PLAN` | `map_object_section_plan_default` |
| `solution.disease_treatment` | 生成病害处置建议 | 病害 | `DISEASE_TREATMENT` | 对应病害处置模板 |
| `solution.disease_review` | 生成病害复核意见 | 病害 | `DISEASE_REVIEW` | `map_object_disease_review_default` |
| `solution.assessment_advice` | 生成评定养护建议 | 评定结果 | `EVALUATION_UNIT_ADVICE` | `map_object_evaluation_unit_advice_default` |
| `solution.region_advice` | 生成区域养护建议 | 框选区域 | `REGION_MAINTENANCE_SUGGESTION` | `map_region_maintenance_advice_default` |

每个子能力配置：

- `category: SOLUTION`
- `family: solution.generate`
- `pipeline: solution_generate`
- `solutionType`
- 触发 action、对象类型或区域模式
- 必需工具、可选工具、禁用工具
- 期望模板编码
- 验收样例

## 分析与生成关系

分析能力负责诊断，生成能力负责形成业务成果。

| 分析能力 | 生成能力 |
| --- | --- |
| `map.route_analysis` | `solution.route_report` |
| `map.section_analysis` | `solution.section_plan` |
| `map.disease_analysis` | `solution.disease_treatment` / `solution.disease_review` |
| `map.assessment_analysis` | `solution.assessment_advice` |
| `map.region_analysis` | `solution.region_advice` |

生成不强制要求用户先分析。直接生成时，系统内部先构建生成所需证据，再进入模板和 LLM 生成。

## 证据快照

分析完成后返回短期证据快照。快照可以先放在 orchestrator 内存缓存或 live trace store 扩展中，第一阶段不需要建立业务表。

建议字段：

```json
{
  "evidenceSnapshotId": "evs_section_20260611_001",
  "analysisTraceId": "trace_section_analysis_20260611_001",
  "capabilityId": "map.section_analysis",
  "scopeFingerprint": "sha256:2a4d7f0c6b1e9f13",
  "scope": {
    "projectId": "project-1",
    "mode": "OBJECT",
    "objectType": "ROAD_SECTION",
    "objectId": "section-1",
    "routeCode": "Y016140727",
    "startStake": 0,
    "endStake": 14.072,
    "metric": "MQI"
  },
  "toolResults": [],
  "sources": [],
  "evidence": {},
  "createdAt": "2026-06-11T00:00:00+08:00",
  "ttlSeconds": 1800
}
```

`scopeFingerprint` 至少由以下字段稳定计算：

- projectId
- context mode
- objectType、objectId
- routeCode
- startStake、endStake
- geometry hash
- metric/indexCode
- selectedLayers
- query filters

## 生成复用策略

生成请求可以携带：

```json
{
  "evidenceSnapshotId": "evs_section_20260611_001",
  "basedOnAnalysisTraceId": "trace_section_analysis_20260611_001"
}
```

后端按以下规则处理：

1. 没有快照：完整执行证据工具链。
2. 快照不存在或过期：完整执行证据工具链，并在 trace 标记 `EVIDENCE_EXPIRED`。
3. 快照范围不一致：废弃快照并重查，标记 `SCOPE_MISMATCH`。
4. 快照范围一致：复用已有工具结果和 sources，标记 `REUSED`。
5. 当前子能力需要快照中没有的工具：只补查缺失工具，标记 `PARTIAL_REUSED`。

生成 trace 必须包含：

- `basedOnAnalysisTraceId`
- `evidenceSnapshotId`
- `evidenceReuseStatus`
- `reusedToolNames`
- `supplementalToolNames`
- `scopeFingerprint`

## 前端体验

客户侧按钮只展示具体业务成果：

- 生成路线养护报告
- 生成路段养护计划
- 生成病害处置建议
- 生成病害复核意见
- 生成评定养护建议
- 生成区域养护建议

如果当前对象刚分析过且范围一致，按钮文案或提示可以显示：

- 基于刚才分析生成路段养护计划

如果范围已变化：

- 当前对象已变化，将重新分析后生成

不再在客户侧出现“生成对象方案”。

## 管理员体验

AI 执行过程和治理台需要能看清：

- 实际命中的子能力；
- 所属父能力族；
- 复用的分析 trace；
- 复用/补查/重查状态；
- 子能力期望模板和实际模板；
- 每个子能力的验收结果。

## 验收标准

1. 点击生成路线报告，trace 中 `capabilityId=solution.route_report`。
2. 点击生成路段计划，trace 中 `capabilityId=solution.section_plan`。
3. 点击生成病害复核，trace 中 `capabilityId=solution.disease_review`。
4. 点击生成区域建议，trace 中 `capabilityId=solution.region_advice`。
5. 先分析再生成，范围一致时生成 trace 显示 `evidenceReuseStatus=REUSED` 或 `PARTIAL_REUSED`。
6. 换对象后生成，不得复用旧对象快照。
7. 直接生成仍能完整查询业务证据并命中模板。
8. 一张图 e2e 验收覆盖所有子能力、模板命中、来源定位和追问回放。

## 实施顺序

1. 拆分 governance 子能力，保持共用生成管线。
2. 更新能力识别、answerMeta、trace 和 e2e 期望能力。
3. 增加分析证据快照生成和返回。
4. 生成请求接收快照 ID 并实现复用校验。
5. 前端保存最近一次分析快照，并在生成请求中带上。
6. 更新治理台和 AI 执行过程展示。
7. 补充发布前验收套件。

## 风险与约束

- 快照必须有 TTL，避免业务数据更新后长期复用旧证据。
- 快照只能复用读工具结果，不能隐式复用写操作。
- 范围指纹必须严格，宁可重查也不要串线。
- 子能力拆分后，需要同步模板、e2e、治理样例，否则会出现能力名正确但模板期望缺失。
