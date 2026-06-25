#!/usr/bin/env python3
"""Initialize the SRMP Outline knowledge base.

The script is intentionally dependency-free. It talks to Outline's HTTP API
directly, disables proxies by default, and upserts documents by exact title.
It is safe to run repeatedly: existing documents are updated unless
--skip-existing is provided.
"""

import argparse
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
from collections import OrderedDict


DEFAULT_COLLECTION_ID = "69b3c2cf-b3ee-41f2-af28-f1156232b0cb"
DEFAULT_CONTAINER = "srmp-backend"


def emit(message):
    text = str(message)
    if hasattr(sys.stdout, "buffer"):
        sys.stdout.buffer.write((text + "\n").encode("utf-8"))
    else:
        sys.stdout.write(text + "\n")
    sys.stdout.flush()


def ordered(items):
    return OrderedDict(items)


def yaml_value(value, indent=0):
    prefix = " " * indent
    if isinstance(value, bool):
        return prefix + ("true" if value else "false")
    if value is None:
        return prefix + "null"
    return prefix + str(value)


def yaml_block(meta):
    lines = []
    for key, value in meta.items():
        if isinstance(value, list):
            if not value:
                lines.append(key + ": []")
            else:
                lines.append(key + ":")
                for item in value:
                    lines.append("  - " + str(item))
        else:
            lines.append(key + ": " + yaml_value(value).strip())
    return "## SRMP 元数据\n\n```yaml\n" + "\n".join(lines) + "\n```\n\n"


def join_values(values, fallback="通用"):
    return "、".join(values) if values else fallback


def bullets(items):
    return "\n".join("- " + item for item in items)


def instance_card(title, instance_id, object_types, capabilities, solution_types, scenario, input_data, judgement, ai_usage, recommended_output, avoid_output, source):
    return """### 实例：{title}

- 实例 ID：`{instance_id}`
- 适用对象：{object_types}
- 适用能力：{capabilities}
- 适用成果：{solution_types}
- 业务场景：{scenario}
- 输入数据示例：{input_data}
- 判断口径：{judgement}
- AI 使用方式：{ai_usage}
- 建议输出：{recommended_output}
- 不建议输出：{avoid_output}
- 来源说明：{source}""".format(
        title=title,
        instance_id=instance_id,
        object_types=join_values(object_types),
        capabilities=join_values(capabilities),
        solution_types=join_values(solution_types),
        scenario=scenario,
        input_data=input_data,
        judgement=judgement,
        ai_usage=ai_usage,
        recommended_output=recommended_output,
        avoid_output=avoid_output,
        source=source,
    )


def structured_sample(key_points, card):
    return """### 知识要点

{key_points}

### 可直接参考的实例卡片

{card}""".format(
        key_points=bullets(key_points),
        card=card,
    )


def default_instance_id(title, knowledge_type):
    domain = knowledge_type.lower().replace("_", "-")
    title_bytes = title.encode("utf-8")
    suffix = title_bytes.hex()[:8]
    return "%s-%s-001" % (domain, suffix)


INSTANCE_FIELD_OVERRIDES = {
    "MQI 指标解释": {
        "input_data": "路线 Y027140727，MQI 82.4，PQI 75.1，PCI 68.5，RQI 91.0，关联病害 15 处。",
        "judgement": "MQI 是综合入口，不能只说“低分”；应继续看 PQI、PCI、RQI 等分项，找出拉低原因。",
        "recommended": "“当前 MQI 为良，主要薄弱项来自 PCI，应结合裂缝、坑槽等病害统计判断处置重点。”",
        "avoid": "不要把 MQI 单独作为处置依据，不要忽略分项指标和病害证据。",
    },
    "PQI 指标解释": {
        "input_data": "评定单元 K0-K1.000，PQI 72.6，PCI 69.8，RQI 82.1，RDI 88.0。",
        "judgement": "PQI 偏低需要拆解 PCI、RQI、RDI，判断是破损、平整度还是车辙因素主导。",
        "recommended": "“PQI 反映路面使用性能，应结合 PCI/RQI/RDI 分项解释，不宜直接给出单一处置措施。”",
        "avoid": "不要把 PQI 直接等同于 PCI，也不要在无分项证据时判断病害类型。",
    },
    "PCI 指标解释": {
        "input_data": "评定结果 PCI 68.5，MQI 82.4；关联裂缝 12 处、坑槽 3 处，集中在 K0.2-K0.8。",
        "judgement": "PCI 主要解释路面破损状况，偏低时应优先关联裂缝、坑槽、松散等病害证据。",
        "recommended": "“PCI 偏低提示路面破损较明显，应按病害类型、严重程度和分布范围提出局部修补或预防性处置。”",
        "avoid": "不要无证据判断基层失效，不要直接给出整段大修结论。",
    },
    "RQI 指标解释": {
        "input_data": "评定单元 RQI 61.2，PCI 83.0，病害数量少，但用户反馈行驶颠簸。",
        "judgement": "RQI 偏低更偏行驶质量和平整度问题，病害少时也可能需要结合 IRI 或现场平整度复核。",
        "recommended": "“RQI 薄弱时应关注行驶舒适性和平整度，建议补充平整度检测或现场复核。”",
        "avoid": "不要只按病害数量解释 RQI，不要把 RQI 问题全部归因于裂缝坑槽。",
    },
    "RDI 指标解释": {
        "input_data": "重载通道局部 RDI 58.0，车辙病害连续长度 320m，雨后车辙槽积水明显。",
        "judgement": "RDI 偏低应关注车辙深度、重载交通、渠化交通和结构层稳定性。",
        "recommended": "“RDI 偏低且车辙连续时，应区分面层流动变形与结构性车辙，必要时安排结构复核。”",
        "avoid": "不要只建议罩面掩盖车辙，不要忽略排水和重载因素。",
    },
    "SCI/BCI/TCI 指标解释": {
        "input_data": "路线 MQI 80.5，PQI 86.0，但 SCI 64.0，边沟淤塞和路肩损坏较集中。",
        "judgement": "SCI/BCI/TCI 用于解释路基、桥隧构造物和沿线设施是否拉低综合评价。",
        "recommended": "“综合指标受非路面分项影响时，应单独说明路基、桥隧或沿线设施问题，避免误判为路面病害。”",
        "avoid": "不要只围绕路面病害写建议，不要忽略构造物和附属设施。",
    },
    "评定等级与判定规则": {
        "input_data": "MQI 85.0，系统等级为良；PCI 72.0，薄弱项为中；病害集中在 K1-K2。",
        "judgement": "等级应按系统口径呈现，分析时同时说明指标值、等级和薄弱项，不自行创造等级名。",
        "recommended": "“当前总体等级为良，但 PCI 分项偏弱，建议针对集中病害区提出处置和复评建议。”",
        "avoid": "不要把良等同于无需养护，也不要把所有评定结果统称低分。",
    },
    "裂缝类病害": {
        "input_data": "病害类型为横向裂缝，长度 18m，宽度 5mm，轻中度，位置 K0.320，雨后存在渗水痕迹。",
        "judgement": "裂缝应结合类型、宽度、长度、密度和是否渗水判断，发展性裂缝需要复核基层和排水。",
        "recommended": "“轻中度裂缝可优先灌缝/封缝；若裂缝持续发展或伴随沉陷，应安排现场复核。”",
        "avoid": "不要看到裂缝就建议铣刨重铺，不要忽略是否渗水和发展趋势。",
    },
    "坑槽类病害": {
        "input_data": "坑槽 3 处，最大面积 0.8m2，深度 4cm，位于弯道外侧，存在行车安全风险。",
        "judgement": "坑槽应优先评估安全风险、深度、边缘破碎和是否需要临时处置。",
        "recommended": "“存在安全风险的坑槽应先临时处置，再安排切边、清底、粘层油和热料永久修补。”",
        "avoid": "不要只写观察跟踪，不要在严重坑槽场景忽略交通安全。",
    },
    "车辙类病害": {
        "input_data": "右幅重车道连续车辙 420m，最大深度 18mm，RDI 62.0，邻近物流园入口。",
        "judgement": "车辙需结合深度、连续长度、车道位置和重载交通判断，区分面层变形和结构性问题。",
        "recommended": "“连续车辙区应结合重载交通和结构复核，浅层可铣刨加铺，结构性问题需补强。”",
        "avoid": "不要只建议表面罩面，不要忽略重载车辆影响。",
    },
    "沉陷/拥包/松散类病害": {
        "input_data": "K1.120 处沉陷 12m2，伴随边沟排水不畅；局部松散 5 处。",
        "judgement": "此类病害可能提示结构层或排水问题，应明确需要现场复核范围和深度。",
        "recommended": "“沉陷伴随排水问题时，应先核查排水和基层稳定性，再确定局部挖补或结构修复。”",
        "avoid": "不要只做表层修补，不要在无复核证据时判断具体结构层损坏。",
    },
    "病害严重程度分级": {
        "input_data": "裂缝宽度 3mm、长度 12m，坑槽深度 5cm，系统严重程度字段为中/重。",
        "judgement": "严重程度应优先采用系统字段和项目口径，缺失时说明需要人工复核。",
        "recommended": "“按系统严重程度字段描述轻/中/重，并说明判级依据和待复核信息。”",
        "avoid": "不要自行创造严重等级，不要混用不同项目的分级口径。",
    },
    "裂缝处置工艺": {
        "input_data": "轻中度裂缝 12 条，宽度 3-6mm，雨季前需处理，基层无明显沉陷。",
        "judgement": "轻中度且基层稳定的裂缝可采用灌缝/封缝；发展性裂缝需复核基层。",
        "recommended": "“建议清缝、烘干、灌缝并设置复查周期；若后续扩展应升级复核。”",
        "avoid": "不要把灌缝作为所有裂缝的固定答案，不要忽略施工季节和干燥条件。",
    },
    "坑槽修补工艺": {
        "input_data": "坑槽面积 0.8m2，深度 4cm，边缘破碎，需在一周内恢复通行安全。",
        "judgement": "坑槽修补应包含切边、清底、粘层油、填料压实和开放交通条件。",
        "recommended": "“先临时保障安全，永久修补按切边清底、粘层、热料填补、压实和验收流程执行。”",
        "avoid": "不要只写填补，不要缺少压实和开放交通要求。",
    },
    "车辙与沉陷处置": {
        "input_data": "连续车辙 420m，沉陷 12m2，重载车辆频繁，排水条件一般。",
        "judgement": "先判断浅层变形还是结构性问题；沉陷应结合基层和排水复核。",
        "recommended": "“浅层车辙可铣刨加铺，结构性车辙或沉陷应结合基层处置和排水改善。”",
        "avoid": "不要在未复核结构层前直接确定单一工法。",
    },
    "铣刨重铺": {
        "input_data": "K2.000-K2.800 连续网裂和坑槽，PCI 62.0，局部修补反复出现。",
        "judgement": "适用于连续病害、面层老化或局部结构修复，需明确范围、厚度、材料和交通组织。",
        "recommended": "“建议对连续病害区进行铣刨重铺，并同步复核基层和排水条件。”",
        "avoid": "不要把零散轻微病害直接升级为铣刨重铺。",
    },
    "灌缝/封层/罩面": {
        "input_data": "PCI 78.0，裂缝早期发展，坑槽少，路面承载基本满足，计划做预防性养护。",
        "judgement": "预防性养护适用于病害早期且结构稳定的场景。",
        "recommended": "“早期裂缝可灌缝，整体老化但结构稳定时可考虑封层或薄层罩面。”",
        "avoid": "不要在结构性损坏明显时只建议预防性养护。",
    },
    "项目背景资料": {
        "input_data": "项目为山区农村公路，雨季集中，部分路线连接学校、乡镇医院和物流点。",
        "judgement": "项目背景用于解释风险和优先级，不能替代业务统计。",
        "recommended": "“生成方案时可将学校、医院、物流通道作为优先级加权因素。”",
        "avoid": "不要把背景描述当成已发生病害事实。",
    },
    "区域养护策略": {
        "input_data": "框选区域内包含学校 2 处、医院 1 处、路线 8 条，病害集中在通学路段。",
        "judgement": "区域策略应结合敏感点、通行功能、病害密度和评定结果排序。",
        "recommended": "“优先保障通学、就医和物流通道，连续病害区集中处置。”",
        "avoid": "不要只按路线长度或病害数量排序。",
    },
    "历史检测资料": {
        "input_data": "同一区间连续两轮 PCI 从 78 降到 68，裂缝数量从 5 处增至 18 处。",
        "judgement": "历史趋势用于判断问题是否发展，应与当前检测数据共同使用。",
        "recommended": "“连续下降区间应提高复核和处置优先级，并关注病害发展原因。”",
        "avoid": "不要只用历史数据覆盖当前检测结果。",
    },
    "数据口径说明": {
        "input_data": "病害数量来自导入台账，评定指标来自当前项目最新评定结果，年度字段不作为主要筛选条件。",
        "judgement": "数据口径用于约束 AI 不自行补造字段或年份。",
        "recommended": "“回答中说明数据来源和口径，缺失字段应提示无法判断。”",
        "avoid": "不要编造年度、路线名称、工程量或检测来源。",
    },
    "优秀方案案例": {
        "input_data": "某路线 K0-K3 PCI 低、裂缝和坑槽集中，采取灌缝、局部铣刨和排水改善组合措施。",
        "judgement": "案例用于参考组合措施和优先级，不应直接复制工程量。",
        "recommended": "“相似场景可参考组合治理思路：先控风险，再集中处置连续病害，最后跟踪复评。”",
        "avoid": "不要把案例措施直接当成当前路线最终方案。",
    },
    "典型病害处置案例": {
        "input_data": "连续坑槽区先冷补保通，随后热料永久修补，并复核基层松散。",
        "judgement": "案例重点在安全优先和临时/永久处置分层。",
        "recommended": "“严重坑槽应先保通，再按永久修补流程和基层复核闭环处理。”",
        "avoid": "不要只做临时修补后结束流程。",
    },
    "问题复盘": {
        "input_data": "某次方案只按病害数量排序，忽略学校周边通行风险，客户反馈优先级不合理。",
        "judgement": "复盘用于提醒 AI 排序时同时考虑业务敏感点和道路功能。",
        "recommended": "“优先级应综合病害、评定、道路功能和敏感点，不只看数量。”",
        "avoid": "不要只按单一指标排序。",
    },
    "指标 FAQ": {
        "input_data": "用户问：PCI 和 PQI 有什么区别？当前对象 PCI 68.5、PQI 74.0。",
        "judgement": "FAQ 应先解释概念差异，再结合当前对象数据说明。",
        "recommended": "“PCI 更关注路面损坏，PQI 是路面使用性能综合表现；当前 PCI 更弱，应查关联病害。”",
        "avoid": "不要只背定义，不要忽略用户当前对象。",
    },
    "养护工艺 FAQ": {
        "input_data": "用户问：裂缝一定要铣刨重铺吗？当前裂缝为轻中度、无明显沉陷。",
        "judgement": "FAQ 应给出条件化回答，避免一刀切。",
        "recommended": "“不一定。轻中度裂缝可灌缝/封缝，结构性裂缝才需要复核后考虑更强处置。”",
        "avoid": "不要直接说必须重铺或完全不用处理。",
    },
    "平台使用 FAQ": {
        "input_data": "用户问：为什么先分析再生成？当前已选评定结果但未查询关联病害。",
        "judgement": "平台说明应解释分析收集业务证据，生成复用证据和模板。",
        "recommended": "“分析用于查询指标、病害、区域统计和知识依据，生成方案时应复用这些证据。”",
        "avoid": "不要让用户以为生成只是套模板。",
    },
}


def ensure_structured_sample(title, knowledge_type, object_types, solution_types, capability_ids, sample):
    if "### 可直接参考的实例卡片" in sample:
        return sample
    summary = " ".join(sample.strip().split())
    if len(summary) > 160:
        summary = summary[:157] + "..."
    override = INSTANCE_FIELD_OVERRIDES.get(title, {})
    scenario = override.get(
        "scenario",
        "维护人员在 `{title}` 页面补充可复用知识，原始业务要点为：{summary}".format(title=title, summary=summary),
    )
    input_data = override.get(
        "input_data",
        "填写真实项目中的对象类型、路线/桩号、指标值、病害类型、数量、严重程度、历史检测或项目背景；没有对应字段时写“无”。",
    )
    judgement = override.get(
        "judgement",
        "先说明这条知识适用的业务条件，再说明不能直接套用的例外情况；涉及方案时必须能回到业务数据或来源依据。",
    )
    recommended_output = override.get(
        "recommended",
        "围绕 `{title}` 给出有条件、有证据的业务表达，并提示需要结合当前对象统计或现场复核。".format(title=title),
    )
    avoid_output = override.get(
        "avoid",
        "不要只写抽象结论，不要脱离地图对象和业务数据直接给处置结论，不要把示例当作项目事实。",
    )
    return """### 知识要点

{sample}

### 可直接参考的实例卡片

{card}""".format(
        sample=sample.strip(),
        card=instance_card(
            title + " 维护实例",
            default_instance_id(title, knowledge_type),
            object_types,
            capability_ids,
            solution_types,
            scenario,
            input_data,
            judgement,
            "检索命中后用于补充 `{title}` 相关解释、限定 AI 输出边界，并为方案或问答提供可追溯依据。".format(title=title),
            recommended_output,
            avoid_output,
            "SRMP 内置种子知识，依据平台业务口径和通用公路养护实践整理；上线后可继续追加项目规范、专家口径和已复盘案例。",
        ),
    )


def instance_format_template(object_types, capability_ids, solution_types):
    return """在本页新增实例时，复制下面卡片并填实内容。实例尽量写“触发条件 + 数据证据 + 判断口径 + AI 输出边界”，不要只写结论。

### 实例：一句话说明业务场景和结论

- 实例 ID：`按目录缩写-主题-三位序号`，例如 `assessment-pci-low-001`
- 适用对象：{object_types}
- 适用能力：{capability_ids}
- 适用成果：{solution_types}
- 业务场景：说明用户在什么地图对象、什么数据状态下会问到这个问题。
- 输入数据示例：列出能支撑判断的业务字段，例如路线、桩号、指标值、病害类型、数量、严重程度、历史检测或项目背景。
- 判断口径：说明为什么这么判断，哪些条件成立时适用，哪些情况需要现场复核。
- AI 使用方式：说明被检索命中后用于解释指标、限定方案边界、补充处置依据，还是用于生成风险提示。
- 建议输出：写出 AI 应该回答或写进方案的关键句式。
- 不建议输出：写出容易误导客户的表达，例如无证据推断、直接下大修结论、把所有评定都叫低分。
- 来源说明：注明来自项目经验、规范摘录、专家口径、历史复盘或待核实资料。""".format(
        object_types=join_values(object_types),
        capability_ids=join_values(capability_ids),
        solution_types=join_values(solution_types),
    )


def category_doc(code, purpose, children):
    body = [
        "## 用途",
        "",
        purpose,
        "",
        "## 下级文档建议",
        "",
    ]
    body.extend("- " + item for item in children)
    body.extend([
        "",
        "## 管理规则",
        "",
        "- 本页作为目录索引使用，具体内容维护在下级文档。",
        "- 标题保持编号前缀，便于排序和人工巡检。",
        "- 参与 AI 检索或方案生成的文档，应在 `SRMP 元数据` 中维护 `srmpKnowledgeType`、`domains`、`objectTypes`、`solutionTypes`、`capabilityIds`、`ragEnabled` 等字段。",
    ])
    meta = ordered([
        ("srmpKnowledgeType", "CATEGORY"),
        ("categoryCode", code),
        ("ragEnabled", code not in ("GOVERNANCE", "TEMPLATE_BLUEPRINT_GROUP")),
        ("status", "active"),
    ])
    return yaml_block(meta) + "\n".join(body) + "\n"


def knowledge_doc(title, knowledge_type, domains, object_types, solution_types, capability_ids, sample, rag=True):
    sample = ensure_structured_sample(title, knowledge_type, object_types, solution_types, capability_ids, sample)
    meta = ordered([
        ("srmpKnowledgeType", knowledge_type),
        ("domains", domains),
        ("objectTypes", object_types),
        ("solutionTypes", solution_types),
        ("capabilityIds", capability_ids),
        ("sourceLevel", "internal_standard"),
        ("ragEnabled", rag),
        ("status", "active"),
    ])
    return yaml_block(meta) + """## 文档定位

用于沉淀 `{title}` 相关的公路养护知识、判定口径、处置依据和常见问答。

## 知识内容

{sample}

## 新增实例格式

{instance_format}

## 维护要求

- 优先写清适用对象、判断条件、指标口径和例外情况。
- 引用规范、项目经验或案例时，应保留来源说明。
- 与方案模板相关的内容，应补充 `solutionTypes` 和 `capabilityIds`，便于生成方案时定向检索。
""".format(
        title=title,
        sample=sample,
        instance_format=instance_format_template(object_types, capability_ids, solution_types),
    ).strip() + "\n"


def template_doc(title, template_code, solution_type, object_types, capability_id, variables, sections, blueprint):
    meta = ordered([
        ("srmpKnowledgeType", "TEMPLATE_BLUEPRINT"),
        ("templateCode", template_code),
        ("templateName", title),
        ("solutionType", solution_type),
        ("objectTypes", object_types),
        ("capabilityIds", [capability_id]),
        ("templateImportEnabled", True),
        ("ragEnabled", False),
        ("status", "active"),
    ])
    return yaml_block(meta) + """## 模板定位

本页维护 `{title}` 的业务写作蓝本。Outline 负责协作维护结构和表达，正式生成时应导入 SRMP 方案模板库，经过变量校验、渲染预览、默认模板设置和回归用例后再使用。

## 推荐章节

{sections}

## 变量清单

{variables}

## 模板正文蓝本

{blueprint}

## 使用规则

- 模板蓝本不参与 RAG 检索，`ragEnabled` 固定为 `false`。
- 生成时由能力策略按 `solutionType`、`objectTypes`、`domains` 定向检索指标标准、病害分级、处置工艺和案例库。
- 新增变量前，先在 `方案模板变量契约` 中登记，再导入 SRMP 模板库做变量检查。
""".format(
        title=title,
        sections="\n".join("- " + item for item in sections),
        variables="\n".join("- `" + item + "`" for item in variables),
        blueprint=blueprint.strip(),
    ).strip() + "\n"


def build_catalog():
    catalog = []

    def add(title, text, parent=None):
        catalog.append({"title": title, "text": text, "parent": parent})

    add("00_知识库治理", category_doc("GOVERNANCE", "维护知识库命名、元数据、模板变量和同步治理规则。", [
        "命名规范", "标签与元数据字典", "方案模板变量契约", "如何新增知识实例",
    ]))
    add("命名规范", knowledge_doc("命名规范", "GOVERNANCE_RULE", ["GOVERNANCE"], [], [], [], """### 标题规则

- 目录页使用两位编号前缀，例如 `10_指标与评定标准`。
- 知识页使用业务名词，例如 `PCI 指标解释`、`裂缝处置工艺`。
- 模板蓝本使用成果名称，例如 `路线养护报告`、`病害复核意见`。

### 编码规则

- `templateCode` 使用小写蛇形命名，例如 `route_report_default`。
- `solutionType` 使用大写蛇形命名，例如 `ROUTE_REPORT`。
- `capabilityIds` 与 AI 能力治理台保持一致，例如 `solution.route_report`。""", rag=False), "00_知识库治理")
    add("标签与元数据字典", knowledge_doc("标签与元数据字典", "GOVERNANCE_RULE", ["GOVERNANCE"], [], [], [], """### 常用字段

| 字段 | 含义 | 示例 |
|---|---|---|
| `srmpKnowledgeType` | 文档类型 | `RULE`、`METHOD`、`TEMPLATE_BLUEPRINT` |
| `domains` | 知识领域 | `ASSESSMENT`、`DISEASE`、`MAINTENANCE` |
| `objectTypes` | 适用地图对象 | `ROAD_ROUTE`、`ROAD_SECTION`、`DISEASE` |
| `solutionTypes` | 适用成果类型 | `ROUTE_REPORT`、`SECTION_PLAN` |
| `ragEnabled` | 是否进入 RAG | `true` 或 `false` |

### 推荐粒度

普通知识只需要最小元数据；模板蓝本必须维护完整元数据。""", rag=False), "00_知识库治理")
    add("方案模板变量契约", knowledge_doc("方案模板变量契约", "TEMPLATE_CONTRACT", ["SOLUTION_TEMPLATE"], ["ROAD_ROUTE", "ROAD_SECTION", "DISEASE", "ASSESSMENT_RESULT", "REGION"], ["ROUTE_REPORT", "SECTION_PLAN", "DISEASE_TREATMENT", "DISEASE_REVIEW", "EVALUATION_UNIT_ADVICE", "REGION_MAINTENANCE_SUGGESTION"], ["solution.route_report", "solution.section_plan", "solution.disease_treatment", "solution.disease_review", "solution.assessment_advice", "solution.region_advice"], """### 公共变量

- `projectName`：项目名称。
- `routeCode`：路线编码。
- `routeName`：路线名称。
- `objectTypeLabel`：对象类型中文名。
- `evidenceSummary`：业务数据和知识依据摘要。

### 方案变量

- `assessmentSummary`：评定摘要。
- `diseaseSummary`：病害摘要。
- `maintenanceAdvice`：养护建议。
- `priorityAdvice`：实施优先级建议。

### 约束

模板中出现的新变量必须先登记，再进入 SRMP 模板库做渲染预览。""", rag=False), "00_知识库治理")
    add("如何新增知识实例", knowledge_doc("如何新增知识实例", "GOVERNANCE_RULE", ["GOVERNANCE"], [], [], [], structured_sample([
        "先判断新内容属于指标解释、病害分级、养护工艺、项目资料、案例库还是 FAQ，不要把所有材料都放进模板蓝本。",
        "在对应知识页复制实例卡片，补齐业务场景、输入数据、判断口径、AI 使用方式、建议输出和不建议输出。",
        "实例 ID 保持稳定，建议使用 `目录域-主题-三位序号`，例如 `disease-crack-review-001`。",
        "同步后抽查检索命中情况，确认 AI 能按能力、对象类型和成果类型拿到这条知识。",
    ], instance_card(
        "新增一条 PCI 偏低解释实例",
        "governance-add-instance-001",
        ["ASSESSMENT_RESULT"],
        ["knowledge.metric_explain", "map.assessment_analysis"],
        ["EVALUATION_UNIT_ADVICE"],
        "维护人员发现 AI 在解释 PCI 时只说“指标偏低”，没有说明应关联裂缝、坑槽等病害证据。",
        "对象为评定结果，PCI 68.5，MQI 82.4，关联裂缝 12 处、坑槽 3 处。",
        "这类内容应维护到 `PCI 指标解释`，不是维护到方案模板蓝本；模板只负责输出结构。",
        "检索命中后用于补充 PCI 解释、提醒查询关联病害，并限制 AI 不能无证据判断大修。",
        "“PCI 偏低通常提示路面破损较明显，应结合裂缝、坑槽等病害类型和分布判断处置措施。”",
        "不要直接写“该路段需要大修”，也不要把所有评定结果都称为低分。",
        "知识库维护示例，适用于培训维护人员。",
    )), rag=False), "00_知识库治理")

    add("10_指标与评定标准", category_doc("ASSESSMENT_STANDARD", "维护 MQI、PQI、PCI、RQI 等指标解释、计算口径和等级判定规则。", [
        "MQI 指标解释", "PQI 指标解释", "PCI 指标解释", "RQI 指标解释", "RDI 指标解释", "SCI/BCI/TCI 指标解释", "评定等级与判定规则",
    ]))
    indicator_samples = {
        "MQI 指标解释": "MQI 是公路技术状况综合指数，可作为路线或路段整体养护优先级判断入口。回答时应说明它是综合指标，并提醒结合 PQI、PCI、RQI 等分项指标定位原因。",
        "PQI 指标解释": "PQI 表示路面使用性能状况。若 PQI 偏低，建议结合 PCI、RQI、RDI 和病害分布判断是破损、平整度、车辙还是其他因素驱动。",
        "PCI 指标解释": "PCI 表示路面损坏状况指数，通常与裂缝、坑槽、松散、沉陷等病害类型和严重程度相关。生成处置建议时，应关联病害数量、位置和严重程度。",
        "RQI 指标解释": "RQI 表示路面行驶质量指数，通常与平整度、颠簸、舒适性相关。分析时可结合 IRI、路段类型和历史检测记录。",
        "RDI 指标解释": "RDI 表示车辙深度指数。若 RDI 偏低，应关注重载交通、渠化交通、结构层变形和车辙修复措施。",
        "SCI/BCI/TCI 指标解释": "SCI、BCI、TCI 分别对应路基、桥隧构造物和沿线设施状况。综合报告中应说明这些指标是否拉低总体 MQI。",
        "评定等级与判定规则": "等级建议以优、良、中、次、差呈现。报告中应同时给出指标值、等级、影响因素和后续复评建议。",
    }
    for title, sample in indicator_samples.items():
        add(title, knowledge_doc(title, "RULE", ["ASSESSMENT"], ["ASSESSMENT_RESULT", "ROAD_ROUTE"], ["ROUTE_REPORT", "EVALUATION_UNIT_ADVICE"], ["knowledge.metric_explain", "map.assessment_analysis", "solution.assessment_advice"], sample), "10_指标与评定标准")

    add("20_病害识别与分级", category_doc("DISEASE_STANDARD", "维护常见路面病害类型、识别标准、严重程度和复核口径。", [
        "裂缝类病害", "坑槽类病害", "车辙类病害", "沉陷/拥包/松散类病害", "病害严重程度分级",
    ]))
    disease_samples = {
        "裂缝类病害": "裂缝类病害可按纵向裂缝、横向裂缝、网裂、龟裂等细分。处置建议需结合宽度、长度、密度、是否渗水和是否发展性病害。",
        "坑槽类病害": "坑槽通常需要关注面积、深度、边缘破碎和安全风险。严重坑槽应优先临时处置并安排永久修复。",
        "车辙类病害": "车辙分析应结合重载交通、车道位置、深度和连续长度。处置措施可能包括铣刨、加铺、结构补强。",
        "沉陷/拥包/松散类病害": "此类病害常提示基层或结构层问题。生成建议时应提醒现场复核范围、深度和排水条件。",
        "病害严重程度分级": "严重程度建议按轻、中、重或系统内等级统一口径，避免 AI 自行创造等级名称。复核意见中应说明判级依据。",
    }
    for title, sample in disease_samples.items():
        add(title, knowledge_doc(title, "RULE", ["DISEASE"], ["DISEASE", "ROAD_SECTION", "REGION"], ["SECTION_PLAN", "DISEASE_TREATMENT", "DISEASE_REVIEW", "REGION_MAINTENANCE_SUGGESTION"], ["map.disease_analysis", "solution.disease_treatment", "solution.disease_review"], sample), "20_病害识别与分级")

    add("30_养护处置工艺", category_doc("MAINTENANCE_METHOD", "维护病害处置、预防性养护和结构性修复的工艺说明。", [
        "裂缝处置工艺", "坑槽修补工艺", "车辙与沉陷处置", "铣刨重铺", "灌缝/封层/罩面",
    ]))
    method_samples = {
        "裂缝处置工艺": "轻微裂缝可考虑灌缝、贴缝或封缝；发展性裂缝需复核基层和排水。建议中应说明适用条件、施工季节和复查周期。",
        "坑槽修补工艺": "坑槽修补应包含切边、清底、喷洒粘层油、填补压实和开放交通条件。严重坑槽需先保障行车安全。",
        "车辙与沉陷处置": "浅层车辙可采用铣刨加铺，结构性车辙或沉陷需结合基层病害处理。建议中应区分临时处置与结构修复。",
        "铣刨重铺": "适用于连续病害、面层老化或局部结构修复。报告中应说明范围、厚度、材料、交通组织和质量验收要点。",
        "灌缝/封层/罩面": "适用于预防性养护场景。需判断病害是否仍处早期、基层是否稳定、路面承载是否满足要求。",
    }
    for title, sample in method_samples.items():
        add(title, knowledge_doc(title, "METHOD", ["MAINTENANCE"], ["DISEASE", "ROAD_SECTION", "ASSESSMENT_RESULT", "REGION"], ["SECTION_PLAN", "DISEASE_TREATMENT", "EVALUATION_UNIT_ADVICE", "REGION_MAINTENANCE_SUGGESTION"], ["knowledge.maintenance_qa", "solution.section_plan", "solution.region_advice"], sample), "30_养护处置工艺")

    add("40_方案模板蓝本", category_doc("TEMPLATE_BLUEPRINT_GROUP", "维护方案、报告、建议和复核意见的业务写作蓝本。", [
        "路线养护报告", "路段养护计划", "病害处置建议", "病害复核意见", "评定养护建议", "区域养护建议",
    ]))
    add("路线养护报告", template_doc(
        "路线养护报告", "route_report_default", "ROUTE_REPORT", ["ROAD_ROUTE"], "solution.route_report",
        ["projectName", "routeCode", "routeName", "assessmentSummary", "diseaseSummary", "maintenanceAdvice", "priorityAdvice", "evidenceSummary"],
        ["路线概况", "路况评定摘要", "病害分布与重点区间", "养护建议", "实施优先级", "数据与知识依据"],
        """# {{routeName}} 养护报告

## 一、路线概况

- 项目：{{projectName}}
- 路线：{{routeCode}} {{routeName}}
- 分析范围：{{analysisScope}}

## 二、路况评定摘要

{{assessmentSummary}}

## 三、病害分布与重点区间

{{diseaseSummary}}

## 四、养护建议

{{maintenanceAdvice}}

## 五、实施优先级

{{priorityAdvice}}

## 六、数据与知识依据

{{evidenceSummary}}"""), "40_方案模板蓝本")
    add("路段养护计划", template_doc(
        "路段养护计划", "map_object_section_plan_default", "SECTION_PLAN", ["ROAD_SECTION"], "solution.section_plan",
        ["projectName", "routeCode", "sectionName", "stakeRange", "assessmentSummary", "diseaseSummary", "maintenanceAdvice", "evidenceSummary"],
        ["路段范围", "关联病害与评定", "处置措施", "工程量估算口径", "实施建议", "风险与复核点"],
        """# {{sectionName}} 路段养护计划

## 一、路段范围

- 路线：{{routeCode}}
- 桩号范围：{{stakeRange}}

## 二、关联病害与评定

{{assessmentSummary}}

{{diseaseSummary}}

## 三、处置措施

{{maintenanceAdvice}}

## 四、实施与复核

{{riskAdvice}}

## 五、依据来源

{{evidenceSummary}}"""), "40_方案模板蓝本")
    add("病害处置建议", template_doc(
        "病害处置建议", "map_object_disease_treatment_default", "DISEASE_TREATMENT", ["DISEASE"], "solution.disease_treatment",
        ["diseaseName", "routeCode", "stakeRange", "diseaseSeverity", "causeAnalysis", "maintenanceAdvice", "evidenceSummary"],
        ["病害识别", "成因判断", "处置建议", "材料与工艺", "复核要求"],
        """# {{diseaseName}} 处置建议

## 一、病害识别

- 路线：{{routeCode}}
- 位置：{{stakeRange}}
- 严重程度：{{diseaseSeverity}}

## 二、成因判断

{{causeAnalysis}}

## 三、处置建议

{{maintenanceAdvice}}

## 四、复核要求

{{reviewAdvice}}

## 五、依据来源

{{evidenceSummary}}"""), "40_方案模板蓝本")
    add("病害复核意见", template_doc(
        "病害复核意见", "map_object_disease_review_default", "DISEASE_REVIEW", ["DISEASE"], "solution.disease_review",
        ["diseaseName", "routeCode", "stakeRange", "reviewConclusion", "riskAdvice", "evidenceSummary"],
        ["复核对象", "疑点说明", "复核结论建议", "需补充证据", "后续处理"],
        """# {{diseaseName}} 复核意见

## 一、复核对象

- 路线：{{routeCode}}
- 位置：{{stakeRange}}

## 二、疑点说明

{{reviewQuestion}}

## 三、复核结论建议

{{reviewConclusion}}

## 四、后续处理

{{riskAdvice}}

## 五、依据来源

{{evidenceSummary}}"""), "40_方案模板蓝本")
    add("评定养护建议", template_doc(
        "评定养护建议", "map_object_evaluation_unit_advice_default", "EVALUATION_UNIT_ADVICE", ["ASSESSMENT_RESULT"], "solution.assessment_advice",
        ["routeCode", "stakeRange", "metricSummary", "weakMetricAnalysis", "diseaseSummary", "maintenanceAdvice", "evidenceSummary"],
        ["评定结果摘要", "薄弱指标分析", "关联病害", "养护建议", "跟踪复评建议"],
        """# {{routeCode}} {{stakeRange}} 评定养护建议

## 一、评定结果摘要

{{metricSummary}}

## 二、薄弱指标分析

{{weakMetricAnalysis}}

## 三、关联病害

{{diseaseSummary}}

## 四、养护建议

{{maintenanceAdvice}}

## 五、依据来源

{{evidenceSummary}}"""), "40_方案模板蓝本")
    add("区域养护建议", template_doc(
        "区域养护建议", "map_region_maintenance_advice_default", "REGION_MAINTENANCE_SUGGESTION", ["REGION"], "solution.region_advice",
        ["projectName", "regionName", "assetSummary", "diseaseSummary", "assessmentSummary", "maintenanceAdvice", "priorityAdvice", "evidenceSummary"],
        ["区域范围", "资产与病害统计", "重点问题", "处置策略", "分期实施建议", "依据来源"],
        """# {{regionName}} 区域养护建议

## 一、区域范围

{{regionSummary}}

## 二、资产与病害统计

{{assetSummary}}

{{diseaseSummary}}

## 三、重点问题

{{assessmentSummary}}

## 四、处置策略

{{maintenanceAdvice}}

## 五、分期实施建议

{{priorityAdvice}}

## 六、依据来源

{{evidenceSummary}}"""), "40_方案模板蓝本")

    add("50_项目资料", category_doc("PROJECT_CONTEXT", "维护项目背景、区域策略、历史检测和数据口径。", [
        "项目背景资料", "区域养护策略", "历史检测资料", "数据口径说明",
    ]))
    project_samples = {
        "项目背景资料": """### 适用场景

用于回答“为什么这个区域/路线优先处置”“报告中如何解释养护背景”等问题。项目背景只用于解释风险和优先级，不能替代当前地图对象的评定、病害和 GIS 数据。

### 业务口径

- 山区、临水、急弯陡坡、学校医院周边、物流出入口等背景因素，可作为优先级加权因素。
- 雨季集中、排水能力不足、重载车比例高时，裂缝渗水、坑槽扩展、车辙连续长度增加的风险更高。
- AI 引用项目背景时，应同时说明“当前对象证据”：路线、桩号、指标值、病害数量、严重程度或区域统计。

### AI 可引用表达

“该路线连接通学、就医和物流通道，且雨季排水压力较高；若当前 GIS 校验显示病害集中，应在养护排序中提高安全风险权重。”

### 来源上下文

来源上下文应保留为“项目背景资料 + 当前地图对象 + 最新评定/病害统计”的组合，禁止只凭背景判断已经发生某类病害。""",
        "区域养护策略": """### 适用场景

用于框选区域、乡镇片区、路线群或养护包生成区域策略时，帮助 AI 把病害密度、评定薄弱项、道路功能和敏感点合并排序。

### 业务口径

- 优先保障学校、医院、客运站、物流园、急弯陡坡、桥隧出入口等敏感位置。
- 病害处置顺序建议采用“安全风险优先、连续病害集中治理、轻微病害预防性养护”的分层思路。
- 当区域内 PCI 偏低且裂缝/坑槽集中，应先定位连续区间，再决定灌缝、局部修补、铣刨重铺或排水改善组合。
- GIS 校验应确认病害点是否落在选区、路线和桩号范围内，避免把选区外证据写入区域建议。

### AI 可引用表达

“区域养护建议应先处理影响通学、就医和重载通行的高风险点；对连续裂缝、连续坑槽或车辙连续长度较大的区间，宜合并为小段集中处置。”""",
        "历史检测资料": """### 适用场景

用于解释同一路线或路段多轮检测变化，判断病害是否发展、指标是否持续恶化，以及是否需要现场复核。

### 业务口径

- 连续两轮 PCI、RQI、RDI 下降，且裂缝、坑槽、车辙数量同步增加时，应提高处置优先级。
- 单轮异常值不能直接判定结构性问题；应结合历史趋势、现场照片、维修记录和排水情况复核。
- 历史检测只能作为趋势依据，不能覆盖当前最新评定结果。

### AI 可引用表达

“该区间 PCI 连续下降且裂缝数量增加，说明破损有发展趋势；建议将当前病害定位到地图对象后，结合现场复核确定是否由表层老化、排水不畅或基层问题导致。”""",
        "数据口径说明": """### 适用场景

用于约束 AI 在问答、地图定位、方案生成和追问来源上下文时，不编造字段、不混用版本、不把缺失数据当成事实。

### 业务口径

- 病害类型、数量、面积、长度、宽度、深度、严重程度以当前导入台账和地图图层为准。
- MQI、PQI、PCI、RQI、RDI 等评定指标以当前项目最新评定结果为准。
- 路线、桩号、行政区和空间范围应经过 GIS 校验；点击定位或地图定位后，回答应说明对象来源。
- 缺失字段应明确提示“当前数据未提供”，不得补造年度、路线名称、工程量、预算或检测机构。

### AI 可引用表达

“本次回答基于当前已同步的 Outline 知识、平台业务数据和 GIS 选中对象；若用户追问来源上下文，应返回命中的知识标题、地图对象、指标/病害字段和需要复核的数据缺口。”""",
    }
    for title, sample in project_samples.items():
        add(title, knowledge_doc(title, "PROJECT_CONTEXT", ["PROJECT"], ["ROAD_ROUTE", "ROAD_SECTION", "REGION"], ["ROUTE_REPORT", "REGION_MAINTENANCE_SUGGESTION"], ["map.route_analysis", "map.region_analysis", "solution.route_report", "solution.region_advice"], sample), "50_项目资料")

    add("60_案例库", category_doc("CASE_LIBRARY", "沉淀典型养护方案、处置案例和问题复盘。", [
        "优秀方案案例", "典型病害处置案例", "问题复盘",
    ]))
    case_samples = {
        "优秀方案案例": """### 案例场景

某农村公路 K0-K3 区间 PCI 偏低，裂缝与坑槽集中，局部排水不畅。平台分析先通过 GIS 定位连续病害区，再按病害类型拆分处置。

### 处置思路

- 轻中度裂缝：雨季前完成清缝、干燥、灌缝或封缝，设置复查周期。
- 零散坑槽：切边、清底、粘层、热料修补和压实，优先处理存在行车安全风险的位置。
- 连续破损区：核实基层和排水条件后，采用局部铣刨重铺与排水改善组合。
- 方案输出：把“业务证据、知识来源、GIS 校验对象、处置边界”写入依据章节。

### AI 可引用表达

“相似场景可采用组合治理：先控制坑槽安全风险，再对连续裂缝和低 PCI 区间集中处置，最后跟踪复评指标变化。”""",
        "典型病害处置案例": """### 案例场景

连续坑槽区位于弯道外侧和重载车辆通行路段，最大坑槽深度 4cm，边缘破碎明显，短期存在行车安全风险。

### 处置思路

- 第一阶段：设置警示和临时冷补，先恢复基本通行安全。
- 第二阶段：安排热料永久修补，流程包括切边、清底、喷洒粘层油、分层填料、压实和开放交通检查。
- 第三阶段：复核基层松散、积水和反复破损原因，必要时合并周边小段处置。

### AI 可引用表达

“严重坑槽不宜只观察跟踪；应先保通，再按永久修补流程闭环，并把基层和排水复核作为后续措施。”""",
        "问题复盘": """### 复盘场景

某次区域方案只按病害数量排序，忽略学校周边、医院通道和急弯陡坡位置，导致客户认为优先级与实际通行风险不匹配。

### 改进口径

- 排序时同时考虑病害严重程度、评定薄弱项、道路功能、敏感点和 GIS 空间关系。
- 对选区外病害不得写入当前区域结论；对缺失的交通量、排水和现场照片，应作为复核项说明。
- AI 回答应允许用户追问“这条建议来自哪些来源”，并返回知识标题、地图对象和业务字段。

### AI 可引用表达

“优先级不能只看病害数量；学校、医院和重载通道周边的中重度病害，应结合 GIS 定位和评定结果提高处置优先级。”""",
    }
    for title, sample in case_samples.items():
        add(title, knowledge_doc(title, "CASE", ["CASE"], ["ROAD_ROUTE", "ROAD_SECTION", "DISEASE", "ASSESSMENT_RESULT", "REGION"], ["ROUTE_REPORT", "SECTION_PLAN", "DISEASE_TREATMENT", "DISEASE_REVIEW", "EVALUATION_UNIT_ADVICE", "REGION_MAINTENANCE_SUGGESTION"], ["solution.route_report", "solution.section_plan", "solution.disease_treatment", "solution.region_advice"], sample), "60_案例库")

    add("70_术语与问答", category_doc("FAQ", "维护指标、工艺和平台使用相关常见问答。", [
        "指标 FAQ", "养护工艺 FAQ", "平台使用 FAQ",
    ]))
    faq_samples = {
        "指标 FAQ": "问：PCI 和 PQI 有什么区别？答：PCI 更偏路面损坏状况，PQI 更偏路面使用性能综合表现，分析时应结合查看。",
        "养护工艺 FAQ": "问：裂缝一定要铣刨重铺吗？答：不一定。轻微裂缝可预防性处置，结构性裂缝需复核基层和排水。",
        "平台使用 FAQ": "问：为什么生成方案前要先分析对象？答：分析会收集业务证据和知识依据，生成方案时可复用这些证据，减少空泛输出。",
    }
    for title, sample in faq_samples.items():
        add(title, knowledge_doc(title, "FAQ", ["FAQ"], [], [], ["knowledge.metric_explain", "knowledge.maintenance_qa"], sample), "70_术语与问答")

    return catalog


def parse_env_file(path):
    result = {}
    if not path or not os.path.exists(path):
        return result
    with open(path, "r", encoding="utf-8") as handle:
        for raw in handle:
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            if "=" in line:
                key, value = line.split("=", 1)
            elif ":" in line:
                key, value = line.split(":", 1)
            else:
                continue
            result[key.strip()] = value.strip().strip('"').strip("'")
    return result


def inspect_container_env(container):
    if not container:
        return {}
    try:
        raw = subprocess.check_output([
            "docker", "inspect", container, "--format", "{{range .Config.Env}}{{println .}}{{end}}"
        ]).decode("utf-8")
    except Exception:
        return {}
    result = {}
    for line in raw.splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            result[key] = value
    return result


class OutlineClient(object):
    def __init__(self, base_url, token, use_proxy=False, retry_wait=20):
        self.base_url = base_url.rstrip("/")
        self.token = token
        self.retry_wait = retry_wait
        if use_proxy:
            self.opener = urllib.request.build_opener()
        else:
            self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

    def post(self, path, payload):
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        request = urllib.request.Request(
            self.base_url + path,
            data=data,
            headers={
                "Authorization": "Bearer " + self.token,
                "Content-Type": "application/json",
            },
            method="POST",
        )
        for attempt in range(5):
            try:
                with self.opener.open(request, timeout=30) as response:
                    raw = response.read().decode("utf-8")
                    return json.loads(raw) if raw else {}
            except urllib.error.HTTPError as exc:
                body = exc.read().decode("utf-8", errors="replace")
                if exc.code == 429 and attempt < 4:
                    wait = self.retry_wait * (attempt + 1)
                    emit("[WARN] Outline rate limited; wait %ss before retrying %s" % (wait, path))
                    time.sleep(wait)
                    continue
                raise RuntimeError("Outline API %s failed: HTTP %s %s" % (path, exc.code, body[:800]))

    def list_documents(self, collection_id):
        result = []
        offset = 0
        limit = 100
        while True:
            payload = {"collectionId": collection_id, "limit": limit, "offset": offset}
            data = self.post("/api/documents.list", payload)
            batch = data.get("data") or []
            result.extend(batch)
            pagination = data.get("pagination") or {}
            total = pagination.get("total")
            if len(batch) < limit:
                break
            offset += limit
            if total is not None and offset >= int(total):
                break
        return result

    def collection_info(self, collection_id):
        return (self.post("/api/collections.info", {"id": collection_id}).get("data") or {})

    def create_document(self, collection_id, title, text, parent_id=None):
        payload = {
            "collectionId": collection_id,
            "title": title,
            "text": text,
            "publish": True,
        }
        if parent_id:
            payload["parentDocumentId"] = parent_id
        return self.post("/api/documents.create", payload).get("data") or {}

    def update_document(self, doc_id, title, text):
        return self.post("/api/documents.update", {
            "id": doc_id,
            "title": title,
            "text": text,
            "publish": True,
        }).get("data") or {}


def resolve_config(args):
    values = {}
    values.update(parse_env_file(args.env_file))
    for key in ("OUTLINE_BASE_URL", "OUTLINE_API_TOKEN", "OUTLINE_DEFAULT_COLLECTION_ID"):
        if os.environ.get(key):
            values[key] = os.environ[key]
    if not values.get("OUTLINE_BASE_URL") or not values.get("OUTLINE_API_TOKEN"):
        values.update(inspect_container_env(args.docker_container))
    base_url = args.base_url or values.get("OUTLINE_BASE_URL")
    token = args.token or values.get("OUTLINE_API_TOKEN")
    collection_id = args.collection_id or values.get("OUTLINE_DEFAULT_COLLECTION_ID") or DEFAULT_COLLECTION_ID
    if not base_url:
        raise SystemExit("Missing OUTLINE_BASE_URL. Pass --base-url or set env.")
    if not token:
        raise SystemExit("Missing OUTLINE_API_TOKEN. Pass --token or set env.")
    return base_url, token, collection_id


def upsert_catalog(client, collection_id, catalog, dry_run=False, skip_existing=False, sleep_seconds=0.8):
    collection = client.collection_info(collection_id)
    emit("[INFO] Collection: %s (%s)" % (collection.get("name"), collection_id))
    existing = client.list_documents(collection_id)
    by_title = {}
    duplicates = []
    for item in existing:
        title = item.get("title")
        if not title:
            continue
        if title in by_title:
            duplicates.append(title)
        else:
            by_title[title] = item

    parent_ids = {}
    created = []
    updated = []
    skipped = []

    for spec in catalog:
        if spec.get("parent"):
            continue
        title = spec["title"]
        current = by_title.get(title)
        if current:
            parent_ids[title] = current.get("id")
            if skip_existing:
                skipped.append(title)
                continue
            if not dry_run:
                client.update_document(current.get("id"), title, spec["text"])
                time.sleep(sleep_seconds)
            updated.append(title)
        else:
            if not dry_run:
                doc = client.create_document(collection_id, title, spec["text"])
                parent_ids[title] = doc.get("id")
                by_title[title] = doc
                time.sleep(sleep_seconds)
            created.append(title)

    for spec in catalog:
        parent_title = spec.get("parent")
        if not parent_title:
            continue
        title = spec["title"]
        current = by_title.get(title)
        parent_id = parent_ids.get(parent_title) or (by_title.get(parent_title) or {}).get("id")
        label = parent_title + "/" + title
        if current:
            if skip_existing:
                skipped.append(label)
                continue
            if not dry_run:
                client.update_document(current.get("id"), title, spec["text"])
                time.sleep(sleep_seconds)
            updated.append(label)
        else:
            if not dry_run:
                doc = client.create_document(collection_id, title, spec["text"], parent_id=parent_id)
                by_title[title] = doc
                time.sleep(sleep_seconds)
            created.append(label)

    return {
        "created": created,
        "updated": updated,
        "skipped": skipped,
        "duplicates": sorted(set(duplicates)),
    }


def main(argv):
    parser = argparse.ArgumentParser(description="Initialize SRMP Outline knowledge base.")
    parser.add_argument("--base-url", default="")
    parser.add_argument("--token", default="")
    parser.add_argument("--collection-id", default="")
    parser.add_argument("--env-file", default=".env.dev")
    parser.add_argument("--docker-container", default=DEFAULT_CONTAINER)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--skip-existing", action="store_true")
    parser.add_argument("--sleep", type=float, default=0.8, help="Seconds to sleep between create/update requests.")
    parser.add_argument("--allow-proxy", action="store_true", help="Use system proxy settings. Disabled by default.")
    args = parser.parse_args(argv)

    base_url, token, collection_id = resolve_config(args)
    catalog = build_catalog()
    emit("[INFO] Outline base: %s" % base_url)
    emit("[INFO] Collection id: %s" % collection_id)
    emit("[INFO] Catalog documents: %s" % len(catalog))
    if not args.allow_proxy:
        emit("[INFO] Proxy disabled for Outline API calls")
    if args.dry_run:
        emit("[INFO] Dry run: no documents will be changed")

    client = OutlineClient(base_url, token, use_proxy=args.allow_proxy)
    result = upsert_catalog(
        client,
        collection_id,
        catalog,
        dry_run=args.dry_run,
        skip_existing=args.skip_existing,
        sleep_seconds=args.sleep,
    )
    emit(json.dumps({
        "createdCount": len(result["created"]),
        "updatedCount": len(result["updated"]),
        "skippedCount": len(result["skipped"]),
        "duplicateTitleCount": len(result["duplicates"]),
        "created": result["created"],
        "updated": result["updated"],
        "skipped": result["skipped"],
        "duplicateTitles": result["duplicates"],
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main(sys.argv[1:])
