package com.smartroad.srmp.agent.map.solution.service.impl;

import com.smartroad.srmp.agent.map.MapObjectContext;
import com.smartroad.srmp.agent.map.MapObjectContextService;
import com.smartroad.srmp.agent.map.solution.dto.MapObjectSolutionRequest;
import com.smartroad.srmp.agent.map.solution.dto.MapObjectSolutionResponse;
import com.smartroad.srmp.agent.map.solution.dto.MapObjectSolutionType;
import com.smartroad.srmp.agent.map.solution.service.MapObjectSolutionQualityChecker;
import com.smartroad.srmp.agent.map.solution.service.MapObjectSolutionService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class MapObjectSolutionServiceImpl implements MapObjectSolutionService {

    @Resource
    private MapObjectContextService mapObjectContextService;

    @Resource
    private MapObjectSolutionQualityChecker qualityChecker;

    @Override
    public MapObjectSolutionResponse generate(MapObjectSolutionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }

        MapObjectContext ctx = resolveContext(request);
        if (ctx == null || !ctx.isPresent()) {
            throw new IllegalArgumentException("未识别到可生成方案的地图对象");
        }

        Map detail = ctx.getDetail() == null ? new LinkedHashMap() : ctx.getDetail();
        String objectType = normalizeType(firstString(detail, "objectType", "object_type", "type", "layerType", "assessment_object_type"));
        if (objectType == null || objectType.length() == 0) {
            objectType = normalizeType(request.getObjectType());
        }
        MapObjectSolutionType solutionType = MapObjectSolutionType.of(request.getSolutionType(), objectType);
        Map<String, Object> summary = buildObjectSummary(ctx, detail, objectType);
        String title = buildTitle(solutionType, summary);
        String markdown = buildMarkdown(solutionType, objectType, summary);

        MapObjectSolutionResponse response = new MapObjectSolutionResponse();
        response.setSolutionType(solutionType.name());
        response.setTitle(title);
        response.setMarkdown(markdown);
        response.setObjectSummary(summary);
        response.setQualityCheck(qualityChecker.check(solutionType, objectType, summary, markdown));
        return response;
    }

    private MapObjectContext resolveContext(MapObjectSolutionRequest request) {
        Map<String, Object> mapObject = new LinkedHashMap<>();
        if (request.getMapObject() != null) {
            mapObject.putAll(request.getMapObject());
        }
        putIfPresent(mapObject, "objectType", request.getObjectType());
        putIfPresent(mapObject, "objectId", request.getObjectId());
        putIfPresent(mapObject, "id", request.getObjectId());
        putIfPresent(mapObject, "routeCode", request.getRouteCode());
        if (request.getYear() != null) {
            mapObject.put("year", request.getYear());
        }

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("mapObject", mapObject);
        MapObjectContext resolved = mapObjectContextService.resolve(context);
        if ((resolved == null || !resolved.isPresent()) && !mapObject.isEmpty()) {
            return MapObjectContext.of(mapObject);
        }
        return resolved;
    }

    private Map<String, Object> buildObjectSummary(MapObjectContext ctx, Map detail, String objectType) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("objectType", objectType);
        summary.put("objectId", firstString(detail, "objectId", "object_id", "id"));
        summary.put("routeCode", firstString(detail, "routeCode", "route_code", "route"));
        summary.put("year", firstObject(detail, "year"));
        summary.put("stakeRange", formatStake(firstObject(detail, "startStake", "start_stake"), firstObject(detail, "endStake", "end_stake")));
        summary.put("routeName", firstString(detail, "routeName", "route_name"));
        summary.put("sectionName", firstString(detail, "sectionName", "section_name"));
        summary.put("sectionCode", firstString(detail, "sectionCode", "section_code"));
        summary.put("unitCode", firstString(detail, "unitCode", "unit_code"));
        summary.put("diseaseName", firstString(detail, "diseaseName", "disease_name", "diseaseType", "disease_type"));
        summary.put("severity", firstObject(detail, "severity"));
        summary.put("quantity", firstObject(detail, "quantity"));
        summary.put("measureUnit", firstString(detail, "measureUnit", "measure_unit"));
        summary.put("mqi", firstObject(detail, "mqi"));
        summary.put("pqi", firstObject(detail, "pqi"));
        summary.put("pci", firstObject(detail, "pci"));
        summary.put("grade", firstObject(detail, "grade"));
        summary.put("contextSummary", firstString(detail, "contextSummary", "context_summary"));
        summary.put("raw", detail);
        if (summary.get("year") == null && ctx != null) {
            summary.put("year", ctx.getYear());
        }
        return summary;
    }

    private String buildTitle(MapObjectSolutionType solutionType, Map<String, Object> summary) {
        String route = stringValue(summary.get("routeCode"), "当前对象");
        String stake = stringValue(summary.get("stakeRange"), "");
        String disease = stringValue(summary.get("diseaseName"), "");
        StringBuilder title = new StringBuilder();
        title.append(route);
        if (stake.length() > 0) {
            title.append(" ").append(stake);
        }
        if (disease.length() > 0) {
            title.append(" ").append(disease);
        }
        title.append(solutionType.getLabel());
        return title.toString();
    }

    private String buildMarkdown(MapObjectSolutionType solutionType, String objectType, Map<String, Object> summary) {
        if (solutionType == MapObjectSolutionType.DISEASE_REVIEW) {
            return diseaseReview(summary);
        }
        if (solutionType == MapObjectSolutionType.DISEASE_TREATMENT) {
            return diseaseTreatment(summary);
        }
        if (solutionType == MapObjectSolutionType.LOW_SCORE_TREATMENT) {
            return lowScoreTreatment(summary);
        }
        if (solutionType == MapObjectSolutionType.EVALUATION_UNIT_ADVICE) {
            return evaluationUnitAdvice(summary);
        }
        if (solutionType == MapObjectSolutionType.SECTION_PLAN) {
            return sectionPlan(summary);
        }
        if (solutionType == MapObjectSolutionType.ROUTE_REPORT) {
            return routeReport(summary);
        }
        return generalAdvice(objectType, summary);
    }

    private String diseaseReview(Map<String, Object> s) {
        StringBuilder sb = header("病害复核意见", s);
        sb.append("## 1. 当前病害对象\n\n");
        appendObjectLines(sb, s);
        sb.append("\n## 2. 问题判断\n\n");
        sb.append("当前对象为").append(stringValue(s.get("diseaseName"), "道路病害"))
                .append("，严重程度为").append(stringValue(s.get("severity"), "未标明"))
                .append("。建议重点判断病害边界、深度、是否存在二次损坏，以及对行车安全和路面使用性能的影响。\n\n");
        sb.append("## 3. 现场复核重点\n\n");
        sb.append("1. 复核病害边界和实际工程量；\n");
        sb.append("2. 检查面层与基层结合状态；\n");
        sb.append("3. 复核排水、荷载和既有修补质量；\n");
        sb.append("4. 检查周边是否存在连续同类病害。\n\n");
        appendCauseTreatmentPriorityRisk(sb, "复核后按实际损坏深度选择局部修补、铣刨重铺或基层处理。");
        return sb.toString();
    }

    private String diseaseTreatment(Map<String, Object> s) {
        StringBuilder sb = header("病害处置建议", s);
        sb.append("## 1. 对象概况\n\n");
        appendObjectLines(sb, s);
        sb.append("\n## 2. 成因判断\n\n");
        sb.append("建议结合现场排水、交通荷载、既有修补质量、基层状态和材料老化情况判断主要成因，避免只处理表面损坏。\n\n");
        sb.append("## 3. 病害影响分析\n\n");
        sb.append("该病害可能影响局部平整度、结构耐久性和行车舒适性。若病害继续扩展，后续处置范围和成本可能增加。\n\n");
        sb.append("## 4. 推荐处置工艺\n\n");
        sb.append("建议先现场复核损坏深度和基层状态。表层病害可采用局部铣刨、清理、重新摊铺或热补；若基层松散或含水，应先处理基层和排水后恢复面层。\n\n");
        sb.append("## 5. 工程量估算\n\n");
        sb.append("当前记录工程量为 ").append(stringValue(s.get("quantity"), "待复核"))
                .append(stringValue(s.get("measureUnit"), ""))
                .append("，最终工程量以现场复核和设计计量为准。\n\n");
        sb.append("## 6. 实施优先级\n\n");
        sb.append(priorityText(s)).append("\n\n");
        sb.append("## 7. 后续跟踪建议\n\n");
        sb.append("处置后建议在下一轮巡检中复核修补边界、平整度和周边扩展情况，必要时纳入小段集中养护。\n\n");
        sb.append("## 8. 风险提示\n\n");
        sb.append("本建议为 AI 生成草稿，需由养护技术人员结合现场复核、预算和交通组织条件审核确认。\n");
        return sb.toString();
    }

    private String lowScoreTreatment(Map<String, Object> s) {
        StringBuilder sb = header("低分单元处置建议", s);
        sb.append("## 1. 评定对象概况\n\n");
        appendObjectLines(sb, s);
        sb.append("\n## 2. 低分原因判断\n\n");
        sb.append("当前对象指标为 MQI=").append(stringValue(s.get("mqi"), "-"))
                .append("，PQI=").append(stringValue(s.get("pqi"), "-"))
                .append("，PCI=").append(stringValue(s.get("pci"), "-"))
                .append("，等级=").append(stringValue(s.get("grade"), "-"))
                .append("。应优先核查 PCI/PQI 偏低对应的裂缝、坑槽、沉陷、车辙等病害贡献。\n\n");
        sb.append("## 3. 周边病害分析\n\n");
        sb.append("建议结合相邻桩号病害记录，判断是否存在连续分布、集中损坏或排水不良诱发的成片劣化。\n\n");
        sb.append("## 4. 处置策略\n\n");
        sb.append("可按病害类型选择局部修补、裂缝处治、罩面、铣刨重铺或排水修复，并与相邻低分单元合并评估。\n\n");
        sb.append("## 5. 优先级\n\n");
        sb.append(priorityText(s)).append("\n\n");
        sb.append("## 6. 风险提示\n\n");
        sb.append("若低分单元继续劣化，可能扩大处置范围并影响路线整体技术状况评价。本草稿需人工审核。\n");
        return sb.toString();
    }

    private String evaluationUnitAdvice(Map<String, Object> s) {
        StringBuilder sb = header("评定单元养护建议", s);
        sb.append("## 1. 评定单元概况\n\n");
        appendObjectLines(sb, s);
        sb.append("\n## 2. 技术状况判断\n\n");
        sb.append("建议结合该单元历年评定结果、当前病害和交通荷载，判断主要性能短板。\n\n");
        sb.append("## 3. 成因判断\n\n");
        sb.append("重点排查材料老化、结构承载不足、排水不畅、施工接缝或重载交通导致的局部劣化。\n\n");
        sb.append("## 4. 养护建议\n\n");
        sb.append("根据病害类型和指标短板选择预防性养护、局部修补或结构性修复，并与相邻单元统筹安排。\n\n");
        sb.append("## 5. 优先级\n\n");
        sb.append(priorityText(s)).append("\n\n");
        sb.append("## 6. 风险提示\n\n");
        sb.append("本建议为草稿，需结合现场复核、检测资料和年度养护计划确认。\n");
        return sb.toString();
    }

    private String sectionPlan(Map<String, Object> s) {
        StringBuilder sb = header("路段养护计划草稿", s);
        sb.append("## 1. 路段概况\n\n");
        appendObjectLines(sb, s);
        sb.append("\n## 2. 技术状况\n\n建议汇总该路段评定指标、病害记录和交通条件，识别主要短板。\n\n");
        sb.append("## 3. 主要问题与成因判断\n\n重点关注低 PCI/PQI 单元、重度病害和连续损坏区段，并从交通荷载、排水条件、材料老化和结构承载等方面分析成因。\n\n");
        sb.append("## 4. 病害分布\n\n建议按桩号统计病害集中区，形成分段处置清单。\n\n");
        sb.append("## 5. 养护目标\n\n控制病害扩展，恢复路面功能，提升路段技术状况和通行舒适性。\n\n");
        sb.append("## 6. 建议工程措施\n\n按局部修补、预防性养护、罩面或结构性修复分级安排。\n\n");
        sb.append("## 7. 优先级与实施计划\n\n优先处置安全风险高和病害集中区段，其他区段纳入中期计划。\n\n");
        sb.append("## 8. 资源需求\n\n需结合现场复核工程量、交通组织和年度预算进一步细化。\n\n");
        sb.append("## 9. 风险与保障措施\n\n需做好施工质量控制、交通安全组织和处置后跟踪评价。本草稿需人工审核。\n");
        return sb.toString();
    }

    private String routeReport(Map<String, Object> s) {
        StringBuilder sb = header("路线技术状况分析报告草稿", s);
        sb.append("## 1. 路线概况\n\n");
        appendObjectLines(sb, s);
        sb.append("\n## 2. 技术状况总体评价\n\n建议结合年度评定结果判断路线总体技术状况和主要短板。\n\n");
        sb.append("## 3. 指标短板与成因判断\n\n重点分析 MQI、PQI、PCI 等指标的低值区段和变化趋势，并结合交通荷载、排水、结构和病害记录判断成因。\n\n");
        sb.append("## 4. 病害分布分析\n\n按病害类型、严重程度和桩号区间识别集中分布区域。\n\n");
        sb.append("## 5. 低分区段分析\n\n筛选次差等级或关键指标低于阈值的区段，作为优先复核对象。\n\n");
        sb.append("## 6. 养护建议\n\n按轻重缓急安排预防性养护、功能性修复和结构性修复。\n\n");
        sb.append("## 7. 优先级与后续工作建议\n\n优先复核低分区段和病害集中区，完善现场复核、工程量核定、预算测算和实施计划。本报告为 AI 草稿，需人工审核。\n\n");
        sb.append("## 8. 风险提示\n\n若低分区段持续劣化，可能影响路线整体技术状况评价和养护资金使用效率。\n");
        return sb.toString();
    }

    private String generalAdvice(String objectType, Map<String, Object> s) {
        StringBuilder sb = header("通用养护建议", s);
        sb.append("## 1. 对象概况\n\n");
        appendObjectLines(sb, s);
        sb.append("\n## 2. 成因判断\n\n建议结合对象类型 ").append(objectType == null ? "未知" : objectType)
                .append("、现场病害和历史评定资料分析原因。\n\n");
        sb.append("## 3. 处置建议\n\n先开展现场复核，再根据损坏程度选择局部修补、预防性养护或专项处治。\n\n");
        sb.append("## 4. 优先级\n\n建议列为 P2 近期复核对象，若存在安全风险则提升至 P1。\n\n");
        sb.append("## 5. 风险提示\n\n本建议为 AI 草稿，需人工审核确认。\n");
        return sb.toString();
    }

    private StringBuilder header(String title, Map<String, Object> s) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title).append("\n\n");
        sb.append("> 本文为智路养护平台基于当前地图对象生成的 AI 草稿，正式方案需经人工审核确认。\n\n");
        String summary = stringValue(s.get("contextSummary"), "");
        if (summary.length() > 0) {
            sb.append("对象摘要：").append(summary).append("\n\n");
        }
        return sb;
    }

    private void appendObjectLines(StringBuilder sb, Map<String, Object> s) {
        appendLine(sb, "对象类型", s.get("objectType"));
        appendLine(sb, "路线", s.get("routeCode"));
        appendLine(sb, "年度", s.get("year"));
        appendLine(sb, "桩号", s.get("stakeRange"));
        appendLine(sb, "路段", firstPresent(s.get("sectionName"), s.get("sectionCode")));
        appendLine(sb, "评定单元", s.get("unitCode"));
        appendLine(sb, "病害类型", s.get("diseaseName"));
        appendLine(sb, "严重程度", s.get("severity"));
        appendLine(sb, "数量", quantityText(s));
        appendLine(sb, "MQI", s.get("mqi"));
        appendLine(sb, "PQI", s.get("pqi"));
        appendLine(sb, "PCI", s.get("pci"));
        appendLine(sb, "等级", s.get("grade"));
    }

    private void appendCauseTreatmentPriorityRisk(StringBuilder sb, String treatment) {
        sb.append("## 4. 成因分析\n\n");
        sb.append("可从材料老化、结构薄弱、施工质量、交通荷载和排水条件等方面开展复核。若为既有修补区域，还应检查修补层与原路面结合质量。\n\n");
        sb.append("## 5. 处置建议\n\n");
        sb.append(treatment).append("\n\n");
        sb.append("## 6. 优先级判断\n\n");
        sb.append("建议列为 P2 近期处置对象；如现场复核发现快速扩展、深层结构损坏或明显安全风险，应提升至 P1。\n\n");
        sb.append("## 7. 风险提示\n\n");
        sb.append("若不及时处置，可能导致损坏范围扩大、路面服务性能下降和后续修复成本增加。本草稿需人工审核。\n");
    }

    private String priorityText(Map<String, Object> s) {
        String severity = stringValue(s.get("severity"), "").toUpperCase();
        String grade = stringValue(s.get("grade"), "").toUpperCase();
        if ("HEAVY".equals(severity) || "BAD".equals(grade)) {
            return "建议优先级为 P1，应尽快现场复核并安排处置。";
        }
        if ("MEDIUM".equals(severity) || "POOR".equals(grade)) {
            return "建议优先级为 P2，纳入近期养护计划。";
        }
        return "建议优先级为 P3，可结合巡检复核和年度计划统筹安排。";
    }

    private String quantityText(Map<String, Object> s) {
        Object quantity = s.get("quantity");
        Object unit = s.get("measureUnit");
        if (quantity == null && unit == null) {
            return "";
        }
        return stringValue(quantity, "") + stringValue(unit, "");
    }

    private void appendLine(StringBuilder sb, String label, Object value) {
        String text = stringValue(value, "");
        if (text.length() > 0) {
            sb.append("- ").append(label).append("：").append(text).append("\n");
        }
    }

    private Object firstPresent(Object... values) {
        for (Object value : values) {
            if (value != null && String.valueOf(value).trim().length() > 0) {
                return value;
            }
        }
        return null;
    }

    private String formatStake(Object start, Object end) {
        String s = stringValue(start, "");
        String e = stringValue(end, "");
        if (s.length() == 0) {
            return "";
        }
        return e.length() == 0 ? "K" + s : "K" + s + "-K" + e;
    }

    private void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null && String.valueOf(value).trim().length() > 0) {
            map.put(key, value);
        }
    }

    private String normalizeType(String value) {
        if (value == null) {
            return "";
        }
        String type = value.trim().toUpperCase().replace("-", "_");
        if ("ASSESSMENT".equals(type)) {
            return "ASSESSMENT_RESULT";
        }
        if ("DISEASE_RECORD".equals(type)) {
            return "DISEASE";
        }
        return type;
    }

    private String firstString(Map map, String... keys) {
        Object value = firstObject(map, keys);
        return value == null ? null : String.valueOf(value);
    }

    private Object firstObject(Map map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && String.valueOf(value).trim().length() > 0) {
                return value;
            }
        }
        return null;
    }

    private String stringValue(Object value, String fallback) {
        if (value == null || String.valueOf(value).trim().length() == 0) {
            return fallback;
        }
        return String.valueOf(value).trim();
    }
}
