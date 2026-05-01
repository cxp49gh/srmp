package com.smartroad.srmp.agent.mapagent.enhance;

import com.smartroad.srmp.agent.mapagent.service.impl.AssessmentResultAdviceEnhancer;
import com.smartroad.srmp.agent.mapagent.service.impl.MapAiAnswerPolisher;
import com.smartroad.srmp.agent.mapagent.service.impl.MapObjectDiseaseAdviceEnhancer;
import com.smartroad.srmp.agent.mapagent.service.impl.RoadAssetAdviceEnhancer;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Phase37.5.1：一张图 AI 回答增强策略注册器。
 *
 * 修复 disease-pothole 回归失败：
 * 当当前对象是 DISEASE，但前置增强器没有生成“当前对象专项处置建议”时，
 * 由 registry 兜底追加病害专项建议，避免策略链调整导致病害回答退化。
 */
@Component
public class MapAiAnswerEnhancerRegistry {

    @Resource
    private MapObjectDiseaseAdviceEnhancer mapObjectDiseaseAdviceEnhancer;

    @Resource
    private AssessmentResultAdviceEnhancer assessmentResultAdviceEnhancer;

    @Resource
    private RoadAssetAdviceEnhancer roadAssetAdviceEnhancer;

    @Resource
    private MapAiAnswerPolisher mapAiAnswerPolisher;

    public String enhance(String answer, MapAiAnswerEnhanceContext context) {
        String value = answer == null ? "" : answer;

        value = mapObjectDiseaseAdviceEnhancer.enhance(
                value,
                context.getUserQuestion(),
                context.getMapContext(),
                context.getKnowledgeSources(),
                context.getToolResults()
        );

        value = assessmentResultAdviceEnhancer.enhance(
                value,
                context.getUserQuestion(),
                context.getMapContext(),
                context.getKnowledgeSources(),
                context.getToolResults()
        );

        value = roadAssetAdviceEnhancer.enhance(
                value,
                context.getUserQuestion(),
                context.getMapContext(),
                context.getKnowledgeSources(),
                context.getToolResults()
        );

        value = ensureDiseaseAdvice(value, context);

        return mapAiAnswerPolisher.polish(value);
    }

    private String ensureDiseaseAdvice(String answer, MapAiAnswerEnhanceContext context) {
        String value = answer == null ? "" : answer;
        if (!isDiseaseObject(context)) {
            return value;
        }
        if (value.contains("当前对象专项处置建议")) {
            return value;
        }

        DiseaseInfo d = DiseaseInfo.from(context);
        StringBuilder sb = new StringBuilder(value.trim());
        if (sb.length() > 0) {
            sb.append("\n\n");
        }

        sb.append("### 当前对象专项处置建议\n");
        sb.append("- 当前对象：").append(emptyToDash(d.routeCode));
        if (d.stakeRange.length() > 0) {
            sb.append(" ").append(d.stakeRange);
        }
        sb.append("，病害为").append(emptyToDash(d.diseaseName));
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
        sb.append("- 建议优先级：").append(priority(d)).append("。\n\n");

        appendDiseaseAdvice(sb, d);
        return sb.toString();
    }

    private void appendDiseaseAdvice(StringBuilder sb, DiseaseInfo d) {
        if (isPothole(d)) {
            sb.append("一、主要问题\n");
            sb.append("该对象属于坑槽类病害，易影响行车安全和舒适性，通常与水损害、松散、裂缝发展、基层破坏或局部材料脱落有关。\n\n");
            sb.append("二、现场复核重点\n");
            sb.append("- 复核坑槽深度、边界松散范围、基层是否破坏和是否存在积水；\n");
            sb.append("- 检查周边是否有裂缝、松散、沉陷或重复修补痕迹，判断是否需要扩大处置边界。\n\n");
            sb.append("三、养护处置建议\n");
            sb.append("- 规则切割病害边界，清理松散材料，保持基面干净干燥；\n");
            sb.append("- 采用热拌料或冷补料填补，并分层摊铺、充分压实；\n");
            sb.append("- 若基层已破坏，应先处理基层和排水，再恢复面层，避免再次形成坑槽。\n");
            return;
        }

        if (isSubsidence(d)) {
            sb.append("一、主要问题\n");
            sb.append("该对象属于沉陷类病害，可能涉及基层松散、路基不均匀变形、含水软化或排水不良。\n\n");
            sb.append("二、现场复核重点\n");
            sb.append("- 复核沉陷深度、范围、发展趋势、周边裂缝和积水痕迹；\n");
            sb.append("- 检查基层稳定性、路基局部变形、含水情况和排水条件。\n\n");
            sb.append("三、养护处置建议\n");
            sb.append("- 若仅为面层局部沉陷，可局部铣刨、找平、重新摊铺并压实；\n");
            sb.append("- 若基层松散或含水，应先处理基层和排水；\n");
            sb.append("- 若涉及路基不均匀沉降，应进行局部结构修复或路基加固。\n");
            return;
        }

        if (isCrack(d)) {
            sb.append("一、主要问题\n");
            sb.append("该对象属于裂缝类病害，雨水渗水和下渗可能诱发基层水损害、坑槽和技术状况下降。\n\n");
            sb.append("二、现场复核重点\n");
            sb.append("- 复核裂缝宽度、长度、密度、发展方向和是否渗水；\n");
            sb.append("- 判断是否为横向裂缝、纵向裂缝、网裂或块裂。\n\n");
            sb.append("三、养护处置建议\n");
            sb.append("- 轻中度裂缝可采用灌缝、开槽灌缝或封缝；\n");
            sb.append("- 裂缝较密集或表层老化时，可结合封层、薄层罩面或雾封层；\n");
            sb.append("- 重度网裂或块裂应进一步判断结构层承载能力。\n");
            return;
        }

        if (isRepairDamage(d)) {
            sb.append("一、主要问题\n");
            sb.append("该对象属于修补损坏，说明既有修补区域再次出现破损，可能与修补边界、基层状态、排水条件或新旧材料结合有关。\n\n");
            sb.append("二、现场复核重点\n");
            sb.append("- 复核修补边界、损坏深度、基层稳定性和含水情况；\n");
            sb.append("- 检查周边是否存在连续裂缝、松散或二次破损迹象。\n\n");
            sb.append("三、养护处置建议\n");
            sb.append("- 表层损坏可采用局部铣刨、清理、重新摊铺或热补修复；\n");
            sb.append("- 若基层松散或含水，应先处理基层和排水，再恢复面层；\n");
            sb.append("- 建议规则化处理修补边界，避免新旧材料结合不良。\n");
            return;
        }

        sb.append("一、主要问题\n");
        sb.append("该对象属于道路病害，应结合严重程度、范围、周边病害和评定指标综合判断。\n\n");
        sb.append("二、现场复核重点\n");
        sb.append("- 复核病害范围、深度、面积、发展趋势和排水条件；\n");
        sb.append("- 检查是否与低分单元、同类病害聚集区或结构性问题相关。\n\n");
        sb.append("三、养护处置建议\n");
        sb.append("- 轻度病害可纳入日常养护；\n");
        sb.append("- 中度病害建议近期处置；\n");
        sb.append("- 重度病害建议优先复核并制定专项处置方案。\n");
    }

    private boolean isDiseaseObject(MapAiAnswerEnhanceContext context) {
        if (context == null || context.getMapContext() == null || context.getMapContext().getMapObject() == null) {
            return false;
        }
        Map<String, Object> obj = context.getMapContext().getMapObject();
        String objectType = firstString(obj, "objectType", "object_type", "type", "layerType");
        if (equalsAny(objectType, "DISEASE", "DISEASE_RECORD")) {
            return true;
        }
        return firstString(obj, "diseaseName", "disease_name", "diseaseType", "disease_type") != null;
    }

    private String priority(DiseaseInfo d) {
        if (containsAny(d.severity, "HEAVY", "重", "严重")) {
            return "P1（优先复核和近期处置）";
        }
        if (containsAny(d.severity, "MEDIUM", "中")) {
            return "P2（近期计划处置）";
        }
        if (containsAny(d.severity, "LIGHT", "轻")) {
            return "P3（日常养护跟踪）";
        }
        return "结合现场复核结果确定";
    }

    private boolean isPothole(DiseaseInfo d) {
        return containsAny(d.diseaseName, "坑槽") || containsAny(d.diseaseType, "POTHOLE");
    }

    private boolean isSubsidence(DiseaseInfo d) {
        return containsAny(d.diseaseName, "沉陷") || containsAny(d.diseaseType, "SUBSIDENCE");
    }

    private boolean isCrack(DiseaseInfo d) {
        return containsAny(d.diseaseName, "裂缝", "开裂") || containsAny(d.diseaseType, "CRACK");
    }

    private boolean isRepairDamage(DiseaseInfo d) {
        return containsAny(d.diseaseName, "修补损坏", "修补") || containsAny(d.diseaseType, "REPAIR");
    }

    private boolean equalsAny(String value, String... candidates) {
        if (value == null) {
            return false;
        }
        for (String c : candidates) {
            if (value.equalsIgnoreCase(c)) {
                return true;
            }
        }
        return false;
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
            return "K" + new BigDecimal(value.trim()).stripTrailingZeros().toPlainString();
        } catch (Exception e) {
            return "K" + value.trim();
        }
    }

    private static class DiseaseInfo {
        private String routeCode = "";
        private String diseaseName = "";
        private String diseaseType = "";
        private String severity = "";
        private String quantity = "";
        private String measureUnit = "";
        private String stakeRange = "";

        private static DiseaseInfo from(MapAiAnswerEnhanceContext context) {
            MapAiAnswerEnhancerRegistry helper = new MapAiAnswerEnhancerRegistry();
            DiseaseInfo d = new DiseaseInfo();
            if (context == null || context.getMapContext() == null || context.getMapContext().getMapObject() == null) {
                return d;
            }
            Map<String, Object> obj = context.getMapContext().getMapObject();
            d.routeCode = firstNonBlank(helper.firstString(obj, "routeCode", "route_code"), context.getMapContext().getRouteCode());
            d.diseaseName = firstNonBlank(helper.firstString(obj, "diseaseName", "disease_name"), helper.firstString(obj, "diseaseType", "disease_type"));
            d.diseaseType = empty(helper.firstString(obj, "diseaseType", "disease_type"));
            d.severity = empty(helper.firstString(obj, "severity", "level", "grade"));
            d.quantity = empty(helper.firstString(obj, "quantity", "area", "length"));
            d.measureUnit = empty(helper.firstString(obj, "measureUnit", "measure_unit", "unit"));

            String start = helper.firstString(obj, "startStake", "start_stake", "startMileage", "start_mileage");
            String end = helper.firstString(obj, "endStake", "end_stake", "endMileage", "end_mileage");
            if (start != null && end != null) {
                d.stakeRange = helper.formatStake(start) + "-" + helper.formatStake(end);
            } else if (start != null) {
                d.stakeRange = helper.formatStake(start);
            }
            return d;
        }

        private static String firstNonBlank(String a, String b) {
            return a != null && a.trim().length() > 0 ? a.trim() : empty(b);
        }

        private static String empty(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
