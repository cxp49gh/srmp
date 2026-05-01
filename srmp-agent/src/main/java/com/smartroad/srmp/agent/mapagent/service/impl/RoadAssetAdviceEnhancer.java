package com.smartroad.srmp.agent.mapagent.service.impl;

import com.smartroad.srmp.agent.knowledge.vo.AiKnowledgeSearchHit;
import com.smartroad.srmp.agent.mapagent.dto.MapAiContext;
import com.smartroad.srmp.agent.tool.AiToolResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase37.4.6：路线/路段对象专项回答增强。
 *
 * 支持：
 * - ROAD_ROUTE：路线级技术状况分析、病害/评定结果汇总、路线养护建议；
 * - ROAD_SECTION：路段级主要问题、成因判断、病害联动、养护处置建议。
 */
@Component
public class RoadAssetAdviceEnhancer {

    public String enhance(String answer,
                          String userQuestion,
                          MapAiContext context,
                          List<AiKnowledgeSearchHit> sources,
                          List<AiToolResult> toolResults) {
        String value = answer == null ? "" : answer.trim();
        if (value.contains("当前路线专项分析") || value.contains("当前路段专项分析")) {
            return value;
        }
        if (context == null || context.getMapObject() == null || context.getMapObject().isEmpty()) {
            return value;
        }

        Map<String, Object> obj = context.getMapObject();
        RoadAsset asset = RoadAsset.from(this, context, obj);
        if (!asset.isRoadRoute() && !asset.isRoadSection()) {
            return value;
        }

        if (!shouldEnhance(value, asset)) {
            return value;
        }

        StringBuilder sb = new StringBuilder(value);
        if (sb.length() > 0) {
            sb.append("\n\n");
        }
        if (asset.isRoadRoute()) {
            sb.append(buildRouteAdvice(asset, toolResults));
        } else {
            sb.append(buildSectionAdvice(asset, toolResults));
        }
        return sb.toString();
    }

    private boolean shouldEnhance(String answer, RoadAsset asset) {
        if (answer == null || answer.trim().length() < 500) {
            return true;
        }
        if (answer.contains("建议结合当前对象或区域的病害、评定结果和知识库资料开展现场复核")) {
            return true;
        }
        if (asset.isRoadRoute() && !containsAny(answer, "路线专项", "路线技术状况", "全线", "路线养护")) {
            return true;
        }
        if (asset.isRoadSection() && !containsAny(answer, "路段专项", "当前路段", "路段范围", "路段养护")) {
            return true;
        }
        return false;
    }

    private String buildRouteAdvice(RoadAsset a, List<AiToolResult> toolResults) {
        ToolSummary summary = ToolSummary.from(toolResults);
        StringBuilder sb = new StringBuilder();
        sb.append("### 当前路线专项分析\n");
        sb.append("- 当前对象：").append(emptyToDash(a.routeCode));
        if (a.routeName.length() > 0) {
            sb.append("（").append(a.routeName).append("）");
        }
        if (a.lengthKm != null) {
            sb.append("，路线长度约 ").append(format(a.lengthKm)).append(" km");
        }
        if (a.adminGrade.length() > 0) {
            sb.append("，行政等级：").append(a.adminGrade);
        }
        if (a.technicalGrade.length() > 0) {
            sb.append("，技术等级：").append(a.technicalGrade);
        }
        sb.append("。\n");
        sb.append("- 建议优先级：").append(routePriority(summary)).append("。\n\n");

        sb.append("一、主要问题\n");
        if (summary.assessmentCount > 0) {
            sb.append("- 系统已查询到该路线相关评定结果 ").append(summary.assessmentCount).append(" 条，应优先关注 MQI/PQI/PCI 较低的连续区间；\n");
        } else {
            sb.append("- 当前回答未获得路线评定结果明细，建议先按年度评定结果筛查低分单元；\n");
        }
        if (summary.diseaseCount > 0) {
            sb.append("- 系统已查询到该路线病害记录 ").append(summary.diseaseCount).append(" 条");
            if (!summary.diseaseTypes.isEmpty()) {
                sb.append("，主要病害类型包括 ").append(joinCounts(summary.diseaseTypes));
            }
            if (!summary.severities.isEmpty()) {
                sb.append("，严重程度分布为 ").append(joinCounts(summary.severities));
            }
            sb.append("；\n");
        } else {
            sb.append("- 当前回答未获得路线病害明细，建议联动病害图层核查病害集中分布情况；\n");
        }
        sb.append("- 路线级分析应重点识别低分单元、病害热点、连续破损区间和重复修补区间。\n\n");

        sb.append("二、成因判断\n");
        sb.append("- 若低分单元与裂缝、坑槽、沉陷、修补损坏等病害集中区重合，说明路面破损是技术状况下降的主要原因；\n");
        sb.append("- 若多个相邻单元 PQI/PCI 偏低，应考虑路面结构层衰减、排水不良、重载交通或养护滞后造成的连续性问题；\n");
        sb.append("- 若病害零散但评定指标偏低，应进一步核查平整度、车辙、抗滑等使用性能指标。\n\n");

        sb.append("三、养护处置建议\n");
        sb.append("- 先按 MQI/PQI/PCI 对路线进行分段排序，识别 P1/P2 优先处置区间；\n");
        sb.append("- 对病害点状分布区间，采用局部修补、裂缝灌缝、坑槽修补等日常/小修措施；\n");
        sb.append("- 对连续低分或病害聚集区间，建议采用封层、薄层罩面、局部铣刨重铺或中修方案；\n");
        sb.append("- 对沉陷、基层松散、排水不良明显的区间，应先处理基层和排水，再恢复面层；\n");
        sb.append("- 建议形成路线级养护清单：低分单元清单、病害热点清单、近期处置清单和年度计划建议。\n\n");

        sb.append("四、后续操作建议\n");
        sb.append("- 在一张图中叠加评定结果、病害、路段资产图层，核查低分与病害是否空间重合；\n");
        sb.append("- 对重点区间生成路段养护计划或路线技术状况报告草稿；\n");
        sb.append("- 对 P1/P2 区间安排现场复核，确认工程量和处置边界。\n");
        return sb.toString();
    }

    private String buildSectionAdvice(RoadAsset a, List<AiToolResult> toolResults) {
        ToolSummary summary = ToolSummary.from(toolResults);
        StringBuilder sb = new StringBuilder();
        sb.append("### 当前路段专项分析\n");
        sb.append("- 当前对象：").append(emptyToDash(a.routeCode));
        if (a.sectionName.length() > 0) {
            sb.append(" / ").append(a.sectionName);
        }
        if (a.stakeRange.length() > 0) {
            sb.append("，范围：").append(a.stakeRange);
        }
        if (a.lengthKm != null) {
            sb.append("，长度约 ").append(format(a.lengthKm)).append(" km");
        }
        if (a.pavementType.length() > 0) {
            sb.append("，路面类型：").append(a.pavementType);
        }
        if (a.laneCount.length() > 0) {
            sb.append("，车道数：").append(a.laneCount);
        }
        sb.append("。\n");
        sb.append("- 建议优先级：").append(sectionPriority(summary)).append("。\n\n");

        sb.append("一、主要问题\n");
        if (summary.assessmentCount > 0) {
            sb.append("- 系统已查询到相关评定结果 ").append(summary.assessmentCount).append(" 条，应重点查看该路段范围内 MQI/PQI/PCI 是否偏低；\n");
        } else {
            sb.append("- 当前回答未获得路段内评定结果明细，建议联动评定图层核查该路段技术状况；\n");
        }
        if (summary.diseaseCount > 0) {
            sb.append("- 系统已查询到路段内/路线相关病害 ").append(summary.diseaseCount).append(" 条");
            if (!summary.diseaseTypes.isEmpty()) {
                sb.append("，主要类型包括 ").append(joinCounts(summary.diseaseTypes));
            }
            if (!summary.severities.isEmpty()) {
                sb.append("，严重程度分布为 ").append(joinCounts(summary.severities));
            }
            sb.append("；\n");
        } else {
            sb.append("- 当前回答未获得路段内病害明细，建议查询该桩号范围内病害记录；\n");
        }
        sb.append("- 路段级分析应关注病害是否沿桩号连续分布、是否与排水/基层/重复修补有关。\n\n");

        sb.append("二、成因判断\n");
        sb.append("- 若路段内坑槽、沉陷、修补损坏较多，可能与基层松散、排水不畅或局部结构层退化有关；\n");
        sb.append("- 若裂缝较多，应重点关注雨水渗水、下渗和基层水损害风险；\n");
        sb.append("- 若评定指标连续偏低，说明该路段可能已从单点病害发展为区间性养护问题。\n\n");

        sb.append("三、现场复核重点\n");
        sb.append("- 复核路段 ").append(emptyToDash(a.stakeRange)).append(" 内病害类型、数量、面积、严重程度和连续性；\n");
        sb.append("- 检查排水条件、基层稳定性、重复修补区域和重载交通影响；\n");
        sb.append("- 对比相邻路段，判断是否需要扩大处置范围。\n\n");

        sb.append("四、养护处置建议\n");
        sb.append("- 病害点状分布时，可采用局部修补、坑槽修补、裂缝灌缝、修补损坏处置；\n");
        sb.append("- 病害连续分布或评定指标偏低时，建议采用封层、薄层罩面、局部铣刨重铺或中修；\n");
        sb.append("- 若存在沉陷、基层松散或排水问题，应先处理基层和排水，再恢复面层；\n");
        sb.append("- 建议以该路段为单位形成养护计划草稿，明确处置桩号、工艺、工程量和优先级。\n");
        return sb.toString();
    }

    private String routePriority(ToolSummary summary) {
        if (summary.heavyDiseaseCount > 0) return "P1（存在重度病害，建议优先复核重点区间）";
        if (summary.assessmentCount > 0 || summary.diseaseCount >= 10) return "P2（建议形成近期路线养护计划）";
        return "P3（日常巡查与年度计划跟踪）";
    }

    private String sectionPriority(ToolSummary summary) {
        if (summary.heavyDiseaseCount > 0) return "P1（路段内存在重度病害，建议优先处置）";
        if (summary.diseaseCount >= 5 || summary.assessmentCount > 0) return "P2（近期计划处置）";
        return "P3（日常养护跟踪）";
    }

    private boolean containsAny(String text, String... words) {
        String value = text == null ? "" : text;
        for (String word : words) {
            if (word != null && value.contains(word)) return true;
        }
        return false;
    }

    private String joinCounts(Map<String, Integer> map) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            if (i++ > 0) sb.append("、");
            sb.append(e.getKey()).append(" ").append(e.getValue()).append(" 条");
            if (i >= 5) break;
        }
        return sb.toString();
    }

    private String emptyToDash(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value.trim();
    }

    private String format(BigDecimal value) {
        if (value == null) return "-";
        return value.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private BigDecimal decimal(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try { return new BigDecimal(value.trim()); } catch (Exception e) { return null; }
    }

    private String formatStake(String value) {
        BigDecimal d = decimal(value);
        if (d == null) return "";
        return "K" + d.stripTrailingZeros().toPlainString();
    }

    private String firstString(Map<String, Object> map, String... keys) {
        if (map == null) return null;
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && String.valueOf(value).trim().length() > 0) return String.valueOf(value).trim();
        }
        Object raw = map.get("raw");
        if (raw instanceof Map) {
            Map<?, ?> rawMap = (Map<?, ?>) raw;
            for (String key : keys) {
                Object value = rawMap.get(key);
                if (value != null && String.valueOf(value).trim().length() > 0) return String.valueOf(value).trim();
            }
        }
        return null;
    }

    private static class ToolSummary {
        private int assessmentCount;
        private int diseaseCount;
        private int heavyDiseaseCount;
        private Map<String, Integer> diseaseTypes = new LinkedHashMap<>();
        private Map<String, Integer> severities = new LinkedHashMap<>();

        private static ToolSummary from(List<AiToolResult> tools) {
            ToolSummary s = new ToolSummary();
            if (tools == null) return s;
            for (AiToolResult tool : tools) {
                if (tool == null) continue;
                if ("gis.queryAssessmentResults".equals(tool.getToolName())) {
                    s.assessmentCount += Math.max(0, tool.getCount());
                }
                if ("gis.queryDiseases".equals(tool.getToolName()) || "gis.queryDiseasesByStakeRange".equals(tool.getToolName())) {
                    s.diseaseCount += Math.max(0, tool.getCount());
                    Object data = tool.getData();
                    if (data instanceof Map) {
                        Map<?, ?> dataMap = (Map<?, ?>) data;
                        Object summary = dataMap.get("summary");
                        if (summary instanceof Map) {
                            Map<?, ?> sm = (Map<?, ?>) summary;
                            copyCounts(sm.get("diseaseTypes"), s.diseaseTypes);
                            copyCounts(sm.get("severities"), s.severities);
                        }
                    }
                }
            }
            Integer heavy = s.severities.get("HEAVY");
            s.heavyDiseaseCount = heavy == null ? 0 : heavy;
            return s;
        }

        private static void copyCounts(Object source, Map<String, Integer> target) {
            if (!(source instanceof Map)) return;
            Map<?, ?> m = (Map<?, ?>) source;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                try {
                    String key = String.valueOf(e.getKey());
                    int value = Integer.parseInt(String.valueOf(e.getValue()));
                    target.put(key, target.getOrDefault(key, 0) + value);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static class RoadAsset {
        private String objectType = "";
        private String routeCode = "";
        private String routeName = "";
        private String sectionName = "";
        private String sectionCode = "";
        private String adminGrade = "";
        private String technicalGrade = "";
        private String pavementType = "";
        private String laneCount = "";
        private String roadWidth = "";
        private BigDecimal lengthKm;
        private String stakeRange = "";

        private boolean isRoadRoute() {
            return staticContainsAny(objectType, "ROAD_ROUTE", "ROUTE");
        }

        private boolean isRoadSection() {
            return staticContainsAny(objectType, "ROAD_SECTION", "SECTION", "ROAD_SEGMENT", "SEGMENT");
        }

        private static boolean staticContainsAny(String text, String... words) {
            String value = text == null ? "" : text;
            for (String word : words) {
                if (word != null && value.contains(word)) return true;
            }
            return false;
        }

        private static RoadAsset from(RoadAssetAdviceEnhancer h, MapAiContext context, Map<String, Object> obj) {
            RoadAsset a = new RoadAsset();
            a.objectType = h.firstNonBlank(h.firstString(obj, "objectType", "object_type", "type", "layerType"), context == null ? null : context.getMode());
            a.routeCode = h.firstNonBlank(h.firstString(obj, "routeCode", "route_code"), context == null ? null : context.getRouteCode());
            a.routeName = h.empty(h.firstString(obj, "routeName", "route_name"));
            a.sectionName = h.empty(h.firstString(obj, "sectionName", "section_name"));
            a.sectionCode = h.empty(h.firstString(obj, "sectionCode", "section_code"));
            a.adminGrade = h.empty(h.firstString(obj, "adminGrade", "admin_grade"));
            a.technicalGrade = h.empty(h.firstString(obj, "technicalGrade", "technical_grade"));
            a.pavementType = h.empty(h.firstString(obj, "pavementType", "pavement_type"));
            a.laneCount = h.empty(h.firstString(obj, "laneCount", "lane_count"));
            a.roadWidth = h.empty(h.firstString(obj, "roadWidth", "road_width"));
            a.lengthKm = h.decimal(h.firstString(obj, "lengthKm", "length_km", "length"));

            String start = h.firstString(obj, "startStake", "start_stake", "startMileage", "start_mileage");
            String end = h.firstString(obj, "endStake", "end_stake", "endMileage", "end_mileage");
            if (start != null && end != null) a.stakeRange = h.formatStake(start) + "-" + h.formatStake(end);
            else if (start != null) a.stakeRange = h.formatStake(start);
            return a;
        }
    }

    private String firstNonBlank(String a, String b) {
        return a != null && a.trim().length() > 0 ? a.trim() : empty(b);
    }

    private String empty(String value) {
        return value == null ? "" : value.trim();
    }
}
