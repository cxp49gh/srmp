package com.smartroad.srmp.agent.mapagent.service.impl;

import com.smartroad.srmp.agent.knowledge.vo.AiKnowledgeSearchHit;
import com.smartroad.srmp.agent.mapagent.dto.MapAiContext;
import com.smartroad.srmp.agent.tool.AiToolResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Phase37.4.2：地图对象专病种回答增强。
 *
 * 目标：
 * 1. 当 RAG 已命中资料，但最终回答仍过于泛化时，补充面向当前地图对象的专项处置建议；
 * 2. 让病害类型、严重程度、桩号、数量、知识库来源和周边对象查询真正进入 answer；
 * 3. 避免用户看到“查到了资料，但建议还是空泛”的体验。
 */
@Component
public class MapObjectDiseaseAdviceEnhancer {

    public String enhance(String answer,
                          String userQuestion,
                          MapAiContext context,
                          List<AiKnowledgeSearchHit> sources,
                          List<AiToolResult> toolResults) {
        String value = answer == null ? "" : answer.trim();
        if (value.contains("当前对象专项处置建议")) {
            return value;
        }
        if (context == null || context.getMapObject() == null || context.getMapObject().isEmpty()) {
            return value;
        }

        Map<String, Object> obj = context.getMapObject();
        String objectType = firstString(obj, "objectType", "object_type", "type", "layerType");
        if (!containsAny(objectType, "DISEASE", "DISEASE_RECORD") && !hasDiseaseFields(obj)) {
            return value;
        }

        DiseaseObject disease = DiseaseObject.from(context, obj);
        if (disease.diseaseName.length() == 0 && disease.diseaseType.length() == 0) {
            return value;
        }

        if (!shouldEnhance(value, disease)) {
            return value;
        }

        StringBuilder sb = new StringBuilder(value);
        if (sb.length() > 0) {
            sb.append("\n\n");
        }
        sb.append(buildDiseaseAdvice(disease, sources, toolResults));
        return sb.toString();
    }

    private boolean shouldEnhance(String answer, DiseaseObject disease) {
        if (answer == null || answer.trim().length() < 450) {
            return true;
        }
        if (answer.contains("建议结合当前对象或区域的病害、评定结果和知识库资料开展现场复核")) {
            return true;
        }
        if (disease.diseaseName.length() > 0 && !answer.contains(disease.diseaseName)) {
            return true;
        }
        if (isSubsidence(disease) && !containsAny(answer, "路基", "基层", "排水", "沉降", "结构修复", "铣刨")) {
            return true;
        }
        if (isPothole(disease) && !containsAny(answer, "切割", "清理", "压实", "热补", "冷补")) {
            return true;
        }
        if (isRepairDamage(disease) && !containsAny(answer, "修补边界", "基层", "排水", "局部铣刨", "重新摊铺")) {
            return true;
        }
        if (isRutting(disease) && !containsAny(answer, "车辙", "铣刨", "罩面", "抗剪", "结构层")) {
            return true;
        }
        if (isCrack(disease) && !containsAny(answer, "灌缝", "封缝", "封层", "渗水")) {
            return true;
        }
        return false;
    }

    private String buildDiseaseAdvice(DiseaseObject d, List<AiKnowledgeSearchHit> sources, List<AiToolResult> toolResults) {
        StringBuilder sb = new StringBuilder();
        sb.append("### 当前对象专项处置建议\n");
        sb.append("- 当前对象：").append(d.routeCode.length() == 0 ? "-" : d.routeCode);
        if (d.stakeRange.length() > 0) {
            sb.append(" ").append(d.stakeRange);
        }
        sb.append("，病害为").append(d.diseaseName.length() == 0 ? d.diseaseType : d.diseaseName);
        if (d.severity.length() > 0) {
            sb.append("，严重程度为 ").append(d.severity);
        }
        if (d.quantity.length() > 0) {
            sb.append("，数量约 ").append(d.quantity);
            if (d.measureUnit.length() > 0) {
                sb.append(d.measureUnit);
            }
        }
        sb.append("。\n");
        sb.append("- 建议优先级：").append(priority(d.severity, d)).append("。\n\n");

        if (isSubsidence(d)) {
            appendSubsidenceAdvice(sb, d);
        } else if (isRutting(d)) {
            appendRuttingAdvice(sb, d);
        } else if (isPothole(d)) {
            appendPotholeAdvice(sb, d);
        } else if (isRepairDamage(d)) {
            appendRepairDamageAdvice(sb, d);
        } else if (isCrack(d)) {
            appendCrackAdvice(sb, d);
        } else {
            appendGenericDiseaseAdvice(sb, d);
        }

        appendNearbySummary(sb, toolResults);
        appendReferenceSummary(sb, sources);
        return sb.toString();
    }

    private void appendSubsidenceAdvice(StringBuilder sb, DiseaseObject d) {
        sb.append("#### 1. 主要问题判断\n");
        sb.append("该对象属于沉陷类病害");
        if (isHeavy(d.severity)) {
            sb.append("，且为重度，存在较高行车安全风险和继续发展的可能，不能仅按表层修补处理");
        }
        sb.append("。沉陷通常与基层松散、路基不均匀变形、含水软化、排水不良或重载反复作用有关。\n\n");

        sb.append("#### 2. 现场复核重点\n");
        sb.append("- 复核沉陷深度、范围、边缘开裂和是否继续发展；\n");
        sb.append("- 检查基层稳定性、路基局部变形、含水情况和排水条件；\n");
        sb.append("- 关注周边是否存在裂缝、坑槽、积水痕迹或同类沉陷聚集。\n\n");

        sb.append("#### 3. 处置建议\n");
        sb.append("- 若仅为面层局部沉陷：可采用局部铣刨、找平、重新摊铺并压实；\n");
        sb.append("- 若基层松散或含水：应先处理基层和排水，再恢复面层；\n");
        sb.append("- 若涉及路基不均匀沉降：建议进行局部结构修复或路基加固，必要时扩大开挖范围；\n");
        if (isHeavy(d.severity)) {
            sb.append("- 若已影响行车安全：建议设置警示、限速或临时处置，并优先纳入近期养护计划。\n");
        }
        sb.append("\n");
    }

    private void appendRuttingAdvice(StringBuilder sb, DiseaseObject d) {
        sb.append("#### 1. 主要问题判断\n");
        sb.append("车辙通常与高温、重载、面层材料抗剪能力不足或结构层变形有关。应区分面层流动变形和结构性变形。\n\n");
        sb.append("#### 2. 现场复核重点\n");
        sb.append("- 复核车辙深度、长度、所在车道和重载交通情况；\n");
        sb.append("- 检查是否伴随泛油、推移、沉陷或裂缝。\n\n");
        sb.append("#### 3. 处置建议\n");
        sb.append("- 轻中度车辙可考虑铣刨罩面、微表处或薄层罩面；\n");
        sb.append("- 重度或结构性车辙应分析结构层原因，必要时铣刨重铺并优化材料抗剪性能。\n\n");
    }

    private void appendPotholeAdvice(StringBuilder sb, DiseaseObject d) {
        sb.append("#### 1. 主要问题判断\n");
        sb.append("坑槽影响行车舒适性和安全性，通常与水损害、松散、裂缝扩展和基层破坏有关。\n\n");
        sb.append("#### 2. 现场复核重点\n");
        sb.append("- 复核坑槽深度、边界松散情况、基层是否破坏和是否有积水；\n");
        sb.append("- 检查周边裂缝或松散是否需要扩大处置范围。\n\n");
        sb.append("#### 3. 处置建议\n");
        sb.append("- 切割规则边界，清理松散材料，保持基面干净干燥；\n");
        sb.append("- 采用热拌料或冷补料填补并充分压实；\n");
        sb.append("- 若基层破坏，应先处理基层，再恢复面层。\n\n");
    }

    private void appendRepairDamageAdvice(StringBuilder sb, DiseaseObject d) {
        sb.append("#### 1. 主要问题判断\n");
        sb.append("修补损坏说明既有修补区域再次出现破损，常见原因包括修补边界处理不彻底、材料结合不足、基层松散或排水不良。\n\n");
        sb.append("#### 2. 现场复核重点\n");
        sb.append("- 复核修补边界、损坏深度、基层稳定性和含水情况；\n");
        sb.append("- 检查周边是否存在连续裂缝、松散或二次破损迹象。\n\n");
        sb.append("#### 3. 处置建议\n");
        sb.append("- 表层损坏可采用局部铣刨、清理、重新摊铺或热补修复；\n");
        sb.append("- 若基层松散或含水，应先处理基层和排水，再恢复面层；\n");
        sb.append("- 建议对修补边界进行规则化处理，避免新旧材料结合不良。\n\n");
    }

    private void appendCrackAdvice(StringBuilder sb, DiseaseObject d) {
        sb.append("#### 1. 主要问题判断\n");
        sb.append("裂缝类病害会导致雨水渗水和下渗，进一步诱发基层水损害、坑槽和技术状况指标下降。\n\n");
        sb.append("#### 2. 现场复核重点\n");
        sb.append("- 复核裂缝宽度、密度、长度、发展方向和是否渗水；\n");
        sb.append("- 判断是否为横向裂缝、纵向裂缝、网裂或块裂。\n\n");
        sb.append("#### 3. 处置建议\n");
        sb.append("- 轻中度裂缝可采用灌缝、开槽灌缝或封缝；\n");
        sb.append("- 裂缝较密集或表层老化时，可结合封层、薄层罩面或雾封层；\n");
        sb.append("- 重度网裂或块裂应进一步判断结构层承载能力，必要时进行结构性处治。\n\n");
    }

    private void appendGenericDiseaseAdvice(StringBuilder sb, DiseaseObject d) {
        sb.append("#### 1. 主要问题判断\n");
        sb.append("该病害建议结合严重程度、影响范围、周边病害分布和评定指标进行综合判断。\n\n");
        sb.append("#### 2. 现场复核重点\n");
        sb.append("- 复核病害范围、深度、发展趋势和排水条件；\n");
        sb.append("- 检查是否与低分单元、同类病害聚集区或结构性问题相关。\n\n");
        sb.append("#### 3. 处置建议\n");
        sb.append("- 轻度病害可纳入日常养护；\n");
        sb.append("- 中度病害建议近期处置；\n");
        sb.append("- 重度病害建议优先复核并制定专项处置方案。\n\n");
    }

    private void appendNearbySummary(StringBuilder sb, List<AiToolResult> toolResults) {
        if (toolResults == null) {
            return;
        }
        for (AiToolResult tool : toolResults) {
            if (tool == null || !"gis.queryNearbyObjects".equals(tool.getToolName())) {
                continue;
            }
            int count = tool.getCount();
            if (count <= 0) {
                return;
            }
            sb.append("#### 4. 周边关联判断\n");
            sb.append("周边对象查询返回约 ").append(count).append(" 条结果。建议进一步核查同类病害、重度病害和低分单元是否在该桩号附近聚集；");
            sb.append("若存在聚集，应从单点修补升级为小区间综合处置。\n\n");
            return;
        }
    }

    private void appendReferenceSummary(StringBuilder sb, List<AiKnowledgeSearchHit> sources) {
        if (sources == null || sources.isEmpty()) {
            return;
        }
        sb.append("#### 参考依据\n");
        int limit = Math.min(3, sources.size());
        for (int i = 0; i < limit; i++) {
            AiKnowledgeSearchHit hit = sources.get(i);
            if (hit == null) {
                continue;
            }
            sb.append("- ").append(emptyToDash(hit.getTitle()));
            if (hit.getSectionTitle() != null && hit.getSectionTitle().trim().length() > 0) {
                sb.append(" / ").append(hit.getSectionTitle());
            }
            sb.append("\n");
        }
    }

    private String priority(String severity, DiseaseObject d) {
        if (isHeavy(severity)) {
            return "P1（优先复核和近期处置）";
        }
        if (containsAny(severity, "MEDIUM", "中")) {
            return "P2（近期计划处置）";
        }
        if (containsAny(severity, "LIGHT", "轻")) {
            return "P3（日常养护跟踪）";
        }
        return "结合现场复核结果确定";
    }

    private boolean hasDiseaseFields(Map<String, Object> obj) {
        return firstString(obj, "diseaseName", "disease_name", "diseaseType", "disease_type", "severity") != null;
    }

    private boolean isHeavy(String severity) {
        return containsAny(severity, "HEAVY", "重", "严重");
    }

    private boolean isSubsidence(DiseaseObject d) {
        return containsAny(d.diseaseName, "沉陷") || containsAny(d.diseaseType, "SUBSIDENCE");
    }

    private boolean isRutting(DiseaseObject d) {
        return containsAny(d.diseaseName, "车辙") || containsAny(d.diseaseType, "RUTTING");
    }

    private boolean isPothole(DiseaseObject d) {
        return containsAny(d.diseaseName, "坑槽") || containsAny(d.diseaseType, "POTHOLE");
    }

    private boolean isRepairDamage(DiseaseObject d) {
        return containsAny(d.diseaseName, "修补损坏", "修补") || containsAny(d.diseaseType, "REPAIR");
    }

    private boolean isCrack(DiseaseObject d) {
        return containsAny(d.diseaseName, "裂缝", "开裂") || containsAny(d.diseaseType, "CRACK");
    }

    private boolean containsAny(String text, String... words) {
        String value = text == null ? "" : text;
        for (String word : words) {
            if (word != null && value.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private String firstString(Map<String, Object> map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && String.valueOf(value).trim().length() > 0) {
                return String.valueOf(value).trim();
            }
        }
        Object raw = map.get("raw");
        if (raw instanceof Map) {
            Map<?, ?> rawMap = (Map<?, ?>) raw;
            for (String key : keys) {
                Object value = rawMap.get(key);
                if (value != null && String.valueOf(value).trim().length() > 0) {
                    return String.valueOf(value).trim();
                }
            }
        }
        return null;
    }

    private String emptyToDash(String value) {
        return value == null || value.trim().length() == 0 ? "-" : value.trim();
    }

    private String formatStake(String value) {
        if (value == null || value.trim().length() == 0) {
            return "";
        }
        try {
            BigDecimal bd = new BigDecimal(value.trim());
            return "K" + bd.stripTrailingZeros().toPlainString();
        } catch (Exception e) {
            return "K" + value.trim();
        }
    }

    private static class DiseaseObject {
        private String routeCode = "";
        private String diseaseName = "";
        private String diseaseType = "";
        private String severity = "";
        private String quantity = "";
        private String measureUnit = "";
        private String stakeRange = "";

        private static DiseaseObject from(MapAiContext context, Map<String, Object> obj) {
            MapObjectDiseaseAdviceEnhancer helper = new MapObjectDiseaseAdviceEnhancer();
            DiseaseObject d = new DiseaseObject();
            d.routeCode = helper.firstNonBlank(
                    helper.firstString(obj, "routeCode", "route_code"),
                    context == null ? null : context.getRouteCode()
            );
            d.diseaseName = helper.firstNonBlank(
                    helper.firstString(obj, "diseaseName", "disease_name"),
                    helper.firstString(obj, "diseaseType", "disease_type")
            );
            d.diseaseType = helper.firstString(obj, "diseaseType", "disease_type");
            d.severity = helper.firstString(obj, "severity", "level", "grade");
            d.quantity = helper.firstString(obj, "quantity", "area", "length");
            d.measureUnit = helper.firstString(obj, "measureUnit", "measure_unit", "unit");

            String start = helper.firstString(obj, "startStake", "start_stake", "startMileage", "start_mileage");
            String end = helper.firstString(obj, "endStake", "end_stake", "endMileage", "end_mileage");
            if (start != null && end != null) {
                d.stakeRange = helper.formatStake(start) + "-" + helper.formatStake(end);
            } else if (start != null) {
                d.stakeRange = helper.formatStake(start);
            }
            return d;
        }
    }

    private String firstNonBlank(String a, String b) {
        return a != null && a.trim().length() > 0 ? a.trim() : (b == null ? "" : b.trim());
    }
}
