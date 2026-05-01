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
 * Phase37.4.4：评定结果对象专项回答增强 + 单元内病害联动。
 */
@Component
public class AssessmentResultAdviceEnhancer {

    public String enhance(String answer,
                          String userQuestion,
                          MapAiContext context,
                          List<AiKnowledgeSearchHit> sources,
                          List<AiToolResult> toolResults) {
        String value = answer == null ? "" : answer.trim();
        if (value.contains("当前评定单元专项分析")) {
            return value;
        }
        if (context == null || context.getMapObject() == null || context.getMapObject().isEmpty()) {
            return value;
        }

        Map<String, Object> obj = context.getMapObject();
        if (!isAssessmentResult(obj)) {
            return value;
        }

        AssessmentObject assessment = AssessmentObject.from(context, obj);
        if (!shouldEnhance(value, assessment)) {
            return value;
        }

        StringBuilder sb = new StringBuilder(value);
        if (sb.length() > 0) {
            sb.append("\n\n");
        }
        sb.append(buildAssessmentAdvice(assessment, toolResults));
        return sb.toString();
    }

    private boolean shouldEnhance(String answer, AssessmentObject a) {
        if (answer == null || answer.trim().length() < 500) {
            return true;
        }
        if (answer.contains("建议结合当前对象或区域的病害、评定结果和知识库资料开展现场复核")) {
            return true;
        }
        if (!containsAny(answer, "MQI", "PQI", "PCI", "评定单元", "路面使用性能", "路面损坏状况")) {
            return true;
        }
        return false;
    }

    private String buildAssessmentAdvice(AssessmentObject a, List<AiToolResult> toolResults) {
        StringBuilder sb = new StringBuilder();
        DiseaseSummary diseaseSummary = extractDiseaseSummary(toolResults);

        sb.append("### 当前评定单元专项分析\n");
        sb.append("- 当前对象：").append(emptyToDash(a.routeCode));
        if (a.stakeRange.length() > 0) {
            sb.append(" ").append(a.stakeRange);
        }
        if (a.year.length() > 0) {
            sb.append("，年度：").append(a.year);
        }
        sb.append("。\n");
        sb.append("- 评定等级：").append(emptyToDash(a.grade)).append("。\n");
        sb.append("- 指标值：MQI=").append(format(a.mqi))
                .append("，PQI=").append(format(a.pqi))
                .append("，PCI=").append(format(a.pci));
        if (a.rqi != null) sb.append("，RQI=").append(format(a.rqi));
        if (a.rdi != null) sb.append("，RDI=").append(format(a.rdi));
        sb.append("。\n");
        sb.append("- 建议优先级：").append(priority(a, diseaseSummary)).append("。\n\n");

        appendProblemAnalysis(sb, a, diseaseSummary);
        appendCauseAnalysis(sb, a, diseaseSummary);
        appendFieldCheck(sb, a, diseaseSummary);
        appendTreatmentAdvice(sb, a, diseaseSummary);
        appendNearbySummary(sb, toolResults);
        return sb.toString();
    }

    private void appendProblemAnalysis(StringBuilder sb, AssessmentObject a, DiseaseSummary diseaseSummary) {
        sb.append("一、主要问题\n");
        sb.append("该对象为道路技术状况评定结果单元。");
        if (a.mqi != null) {
            sb.append("MQI=").append(format(a.mqi)).append(" 反映综合技术状况");
            if (a.mqi < 75) sb.append("，处于中等偏低水平");
            sb.append("。");
        }
        sb.append("\n");

        if (a.pqi != null) {
            sb.append("- PQI=").append(format(a.pqi)).append("，反映路面使用性能");
            if (a.pqi < 75) sb.append("，是当前单元的重点短板之一");
            sb.append("；\n");
        }
        if (a.pci != null) {
            sb.append("- PCI=").append(format(a.pci)).append("，反映路面损坏状况");
            if (a.pci < 75) sb.append("，说明应重点关注裂缝、坑槽、修补损坏、沉陷、松散等路面破损类病害");
            sb.append("；\n");
        }

        if (diseaseSummary.count > 0) {
            sb.append("- 系统在该评定单元范围内查询到病害 ").append(diseaseSummary.count).append(" 条");
            if (!diseaseSummary.diseaseTypes.isEmpty()) {
                sb.append("，主要类型包括 ").append(joinCounts(diseaseSummary.diseaseTypes));
            }
            if (!diseaseSummary.severities.isEmpty()) {
                sb.append("，严重程度分布为 ").append(joinCounts(diseaseSummary.severities));
            }
            sb.append("。\n");
        }

        if (a.pqi != null && a.pci != null && a.pqi < 75 && a.pci < 75) {
            sb.append("综合判断，该单元主要问题可能集中在路面使用性能下降和路面破损状况偏弱两个方面。\n");
        }
        sb.append("\n");
    }

    private void appendCauseAnalysis(StringBuilder sb, AssessmentObject a, DiseaseSummary diseaseSummary) {
        sb.append("二、成因判断\n");
        if (a.pci != null && a.pci < 75) {
            sb.append("- PCI 偏低通常与路面破损类病害的数量、面积、严重程度或连续分布有关；\n");
        }
        if (a.pqi != null && a.pqi < 75) {
            sb.append("- PQI 偏低可能由路面破损、平整度下降、车辙、抗滑不足或多类病害共同影响；\n");
        }
        if (a.rqi != null && a.rqi < 80) {
            sb.append("- RQI 偏低提示平整度可能存在问题，应关注沉陷、波浪、错台或局部修补不平顺；\n");
        }
        if (a.rdi != null && a.rdi < 85) {
            sb.append("- RDI 偏低提示车辙问题可能参与拉低路面使用性能；\n");
        }
        if (diseaseSummary.count > 0) {
            sb.append("- 结合单元内病害查询结果，应优先判断病害是否呈连续分布，是否由排水不良、基层松散、重复修补或结构层退化引起。\n");
        } else {
            sb.append("- 当前回答未获得单元内病害明细，建议联动病害图层进一步核实低分成因。\n");
        }
        sb.append("\n");
    }

    private void appendFieldCheck(StringBuilder sb, AssessmentObject a, DiseaseSummary diseaseSummary) {
        sb.append("三、现场复核重点\n");
        sb.append("- 复核 ").append(emptyToDash(a.stakeRange)).append(" 范围内的病害类型、数量、面积和严重程度；\n");
        sb.append("- 重点排查裂缝、坑槽、修补损坏、沉陷、松散等是否集中分布；\n");
        sb.append("- 检查是否存在排水不良、基层松散、重复修补区域或结构层退化迹象；\n");
        sb.append("- 对比相邻评定单元，判断低分是否为局部异常还是连续区间问题。\n");
        if (diseaseSummary.count > 0) {
            sb.append("- 对已查询到的单元内病害进行现场抽核，确认其桩号、面积、严重程度和处置边界是否准确。\n");
        }
        sb.append("\n");
    }

    private void appendTreatmentAdvice(StringBuilder sb, AssessmentObject a, DiseaseSummary diseaseSummary) {
        sb.append("四、养护处置建议\n");
        if (a.pci != null && a.pci < 75) {
            sb.append("- 若病害以点状为主，可优先采用局部修补、坑槽修补、裂缝灌缝、修补损坏处置等措施；\n");
        }
        if (a.pqi != null && a.pqi < 75) {
            sb.append("- 若路面使用性能整体偏低，且病害连续分布，可考虑封层、薄层罩面、局部铣刨重铺或中修方案；\n");
        }
        if (diseaseSummary.count > 0) {
            sb.append("- 若单元内病害在桩号上连续或同类病害集中，建议从单点处置升级为小区间综合处置；\n");
        }
        sb.append("- 若发现沉陷、基层松散、含水软化或排水不良，应先处理基层和排水，再恢复面层；\n");
        sb.append("- 建议将该评定单元纳入近期养护计划，并结合病害明细确定工程量和处置边界。\n\n");
    }

    private void appendNearbySummary(StringBuilder sb, List<AiToolResult> toolResults) {
        if (toolResults == null) return;
        for (AiToolResult tool : toolResults) {
            if (tool == null) continue;
            if ("gis.queryAssessmentResults".equals(tool.getToolName()) && tool.getCount() > 0) {
                sb.append("五、周边/同类评定结果参考\n");
                sb.append("系统已查询到 ").append(tool.getCount()).append(" 条评定结果。建议对比相邻单元 MQI/PQI/PCI，判断该问题是否具有连续性；");
                sb.append("如相邻单元也偏低，应按区间整体养护思路统筹处置。\n\n");
                return;
            }
        }
    }

    private DiseaseSummary extractDiseaseSummary(List<AiToolResult> toolResults) {
        DiseaseSummary summary = new DiseaseSummary();
        if (toolResults == null) return summary;

        for (AiToolResult tool : toolResults) {
            if (tool == null || !"gis.queryDiseasesByStakeRange".equals(tool.getToolName())) continue;
            summary.count = tool.getCount();

            Object data = tool.getData();
            if (!(data instanceof Map)) {
                return summary;
            }
            Map<?, ?> dataMap = (Map<?, ?>) data;
            Object summaryObj = dataMap.get("summary");
            if (summaryObj instanceof Map) {
                Map<?, ?> sm = (Map<?, ?>) summaryObj;
                copyCounts(sm.get("diseaseTypes"), summary.diseaseTypes);
                copyCounts(sm.get("severities"), summary.severities);
            }
            return summary;
        }
        return summary;
    }

    private void copyCounts(Object source, Map<String, Integer> target) {
        if (!(source instanceof Map)) return;
        Map<?, ?> map = (Map<?, ?>) source;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) continue;
            try {
                target.put(String.valueOf(e.getKey()), Integer.parseInt(String.valueOf(e.getValue())));
            } catch (Exception ignored) {
            }
        }
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

    private String priority(AssessmentObject a, DiseaseSummary summary) {
        String grade = a.grade == null ? "" : a.grade.toUpperCase();
        if (containsAny(grade, "POOR", "差", "重", "HEAVY")) return "P1（优先复核和近期处置）";
        if ((a.mqi != null && a.mqi < 70) || (a.pqi != null && a.pqi < 70) || (a.pci != null && a.pci < 70)) return "P1（指标接近或低于较差阈值，建议优先处置）";
        if (summary.count >= 10) return "P2（单元内病害数量较多，建议近期计划处置）";
        if (containsAny(grade, "MEDIUM", "中") || (a.mqi != null && a.mqi < 80) || (a.pqi != null && a.pqi < 80) || (a.pci != null && a.pci < 80)) return "P2（近期计划处置）";
        return "P3（日常跟踪养护）";
    }

    private boolean isAssessmentResult(Map<String, Object> obj) {
        String objectType = firstString(obj, "objectType", "object_type", "type", "layerType");
        if (containsAny(objectType, "ASSESSMENT_RESULT", "ASSESSMENT", "EVALUATION")) return true;
        return firstString(obj, "mqi", "pqi", "pci", "grade") != null;
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

    private Double firstDouble(Map<String, Object> map, String... keys) {
        String value = firstString(map, keys);
        if (value == null) return null;
        try { return Double.valueOf(value); } catch (Exception e) { return null; }
    }

    private boolean containsAny(String text, String... words) {
        String value = text == null ? "" : text;
        for (String word : words) {
            if (word != null && value.contains(word)) return true;
        }
        return false;
    }

    private String emptyToDash(String value) {
        return value == null || value.trim().length() == 0 ? "-" : value.trim();
    }

    private String format(Double value) {
        if (value == null) return "-";
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String formatStake(String value) {
        if (value == null || value.trim().length() == 0) return "";
        try { return "K" + new BigDecimal(value.trim()).stripTrailingZeros().toPlainString(); }
        catch (Exception e) { return "K" + value.trim(); }
    }

    private static class DiseaseSummary {
        private int count = 0;
        private Map<String, Integer> diseaseTypes = new LinkedHashMap<>();
        private Map<String, Integer> severities = new LinkedHashMap<>();
    }

    private static class AssessmentObject {
        private String routeCode = "";
        private String year = "";
        private String grade = "";
        private String stakeRange = "";
        private Double mqi;
        private Double pqi;
        private Double pci;
        private Double rqi;
        private Double rdi;

        private static AssessmentObject from(MapAiContext context, Map<String, Object> obj) {
            AssessmentResultAdviceEnhancer helper = new AssessmentResultAdviceEnhancer();
            AssessmentObject a = new AssessmentObject();
            a.routeCode = firstNonBlank(helper.firstString(obj, "routeCode", "route_code"), context == null ? null : context.getRouteCode());
            a.year = firstNonBlank(helper.firstString(obj, "year"), context == null || context.getYear() == null ? null : String.valueOf(context.getYear()));
            a.grade = empty(helper.firstString(obj, "grade", "level"));
            a.mqi = helper.firstDouble(obj, "mqi", "MQI");
            a.pqi = helper.firstDouble(obj, "pqi", "PQI");
            a.pci = helper.firstDouble(obj, "pci", "PCI");
            a.rqi = helper.firstDouble(obj, "rqi", "RQI");
            a.rdi = helper.firstDouble(obj, "rdi", "RDI");
            String start = helper.firstString(obj, "startStake", "start_stake", "startMileage", "start_mileage");
            String end = helper.firstString(obj, "endStake", "end_stake", "endMileage", "end_mileage");
            if (start != null && end != null) a.stakeRange = helper.formatStake(start) + "-" + helper.formatStake(end);
            else if (start != null) a.stakeRange = helper.formatStake(start);
            return a;
        }

        private static String firstNonBlank(String a, String b) {
            return a != null && a.trim().length() > 0 ? a.trim() : empty(b);
        }

        private static String empty(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
