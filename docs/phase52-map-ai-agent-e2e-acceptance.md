# Phase52 一张图 AI 助手端到端验收

本文档用于回归一张图 AI 助手从地图入口、上下文构造、能力识别、工具调用、知识证据、方案模板到管理员排障信息的完整链路。

## 自动验收

前置条件：

- 后端 `http://127.0.0.1:8080` 可访问；
- `srmp-ai-orchestrator` 已接入后端；
- 至少存在一个可用的数据管理项目，且包含路线、路段、病害、评定数据；
- 需要验证大模型时，当前环境应启用可用 LLM 配置。

运行：

```bash
./scripts/check-map-agent-e2e.sh
```

常用参数：

```bash
# 指定项目
./scripts/check-map-agent-e2e.sh --project-id d20a49cdee904299a4967e196f676c9e

# 只跑分析入口，不跑方案生成
./scripts/check-map-agent-e2e.sh --no-generation

# 跳过参考资料追问自动回放
./scripts/check-map-agent-e2e.sh --no-followup-replay

# 只跑 PCI 指标解释，确认不会误查 GIS 业务工具
./scripts/check-map-agent-e2e.sh --case knowledge.metric_explain

# 输出 JSON，便于 CI 或排障记录保存
./scripts/check-map-agent-e2e.sh --json
```

脚本会自动抽样项目、路线、路段、病害、评定对象，并调用 `/api/agent/map-agent/run`。失败时会指出具体 case、缺失工具、误用工具、模板变量、兜底、answerMeta、业务来源不可定位或 LLM 状态问题。

方案生成类 case 现在按具体子能力验收。直接生成必须完整查询证据；先分析再生成的 UI 链路应携带 `evidenceSnapshotId`，生成 trace 中应展示 `evidenceReuseStatus`、`basedOnAnalysisTraceId` 和 `scopeFingerprint`。

## 自动检查矩阵

| Case | 客户入口 | 期望能力 | 必须调用 | 禁止/重点 |
| --- | --- | --- | --- | --- |
| `knowledge.metric_explain` | 解释 PCI 指标 | `knowledge.metric_explain` | `knowledge.retrieve` | 不应调用任何 GIS 业务工具 |
| `map.route_analysis` | 分析路线 | `map.route_analysis` | `gis.queryRegionSummary`, `gis.queryAssessmentResults`, `gis.queryDiseases`, `knowledge.retrieve` | 业务统计、评定、病害都要进上下文 |
| `map.section_analysis` | 分析路段 | `map.section_analysis` | `gis.queryAssessmentResults`, `gis.queryDiseases`, `gis.queryDiseasesByStakeRange`, `knowledge.retrieve` | 不应查区域统计；路线和桩号必须来自当前路段 |
| `map.disease_analysis` | 分析病害 | `map.disease_analysis` | `gis.queryNearbyObjects`, `knowledge.retrieve` | 不应查区域统计 |
| `map.assessment_analysis` | 分析评定 | `map.assessment_analysis` | `gis.queryAssessmentResults`, `gis.queryDiseasesByStakeRange`, `knowledge.retrieve` | 不应泛化为“低分”；路线和桩号必须来自当前评定 |
| `map.region_analysis` | 分析框选区域 | `map.region_analysis` | `gis.queryRegionSummary`, `knowledge.retrieve` | `geometry` 必须进入后端工具参数 |
| `solution.route_report` | 生成路线养护报告 | `solution.route_report` | 路线统计、评定、病害、知识库、`solution.generateDraft` | 模板 `route_report_default`，不得兜底 |
| `solution.section_plan` | 生成路段养护计划 | `solution.section_plan` | 路段评定、病害、桩号病害、知识库、`solution.generateDraft` | 模板 `map_object_section_plan_default`，不得兜底 |
| `solution.disease_review` | 生成病害复核意见 | `solution.disease_review` | 周边对象、知识库、`solution.generateDraft` | 模板 `map_object_disease_review_default`，不得兜底 |
| `solution.assessment_advice` | 生成评定处置建议 | `solution.assessment_advice` | 评定、桩号病害、知识库、`solution.generateDraft` | 模板 `map_object_evaluation_unit_advice_default`，不得兜底 |
| `solution.region_advice` | 生成区域养护建议 | `solution.region_advice` | 区域统计、知识库、`solution.generateDraft` | 模板 `map_region_maintenance_advice_default`，不得出现 `routeCode` 变量缺失 |
| `followup.source_replay` | 参考资料来源追问 | 任意匹配能力 | 不固定工具 | 必须继承并回显可定位 `followupSource.mapTarget` |

## 人工验收清单

客户使用视角：

- 一张图顶部筛选项目、路网、评定、指标、等级后，地图图层和左侧统计同步刷新；
- 点击路线、路段、病害、评定、框选区域后，AI 助手标题和当前对象描述清晰，不混用“低分”等不准确术语；
- 提问“解释 PCI 指标”只表现为知识解释，不出现区域统计、路线报告、对象方案等业务动作；
- 生成路线报告、路段计划、病害复核、评定建议、区域建议时，标题不出现 `{{变量}}`，正文不出现“变量缺失”；
- 参考资料如果来自地图对象，应能看出对象类型、路线、桩号或地图定位入口；
- 追问时应继承当前对象或区域上下文，切换对象后不串线。

管理员排障视角：

- `/agent/ai-traces` 能通过 traceId 找到本次执行；
- 执行过程应展示能力识别、工具计划、实际工具结果、answerMeta、知识证据、模板命中和降级原因；
- `answerMeta` 不能为空，页面不应在执行中展示“未返回 answerMeta”这类中间态误导信息；
- 方案生成的 `solution.generateDraft` 结果应包含模板编码、缺失变量、兜底标记、LLM 状态；
- 业务来源应包含 `mapTarget` 与 `followupContext`，便于定位、追问和 trace 复盘；
- 如果知识库无向量命中，应能区分“知识证据不足”和“业务工具失败”，不能混成通用失败。

## 后续扩展

- 将本脚本接入治理台评测用例集，作为发布前 smoke；
- 给知识库 metadata 建立导入规范，让更多知识来源也能自动带路线、桩号或对象定位。
