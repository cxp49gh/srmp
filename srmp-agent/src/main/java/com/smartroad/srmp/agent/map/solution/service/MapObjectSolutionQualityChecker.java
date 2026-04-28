package com.smartroad.srmp.agent.map.solution.service;

import com.smartroad.srmp.agent.map.solution.dto.MapObjectSolutionQualityCheck;
import com.smartroad.srmp.agent.map.solution.dto.MapObjectSolutionQualityItem;
import com.smartroad.srmp.agent.map.solution.dto.MapObjectSolutionType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MapObjectSolutionQualityChecker {

    public MapObjectSolutionQualityCheck check(MapObjectSolutionType solutionType,
                                               String objectType,
                                               Map<String, Object> summary,
                                               String markdown) {
        MapObjectSolutionQualityCheck check = new MapObjectSolutionQualityCheck();
        String text = markdown == null ? "" : markdown;
        String normalizedType = objectType == null ? "" : objectType.toUpperCase();

        add(check, "对象位置", hasAny(summary, "routeCode", "stakeRange") || containsAny(text, "路线", "桩号"),
                "应包含路线、桩号或对象位置");

        if ("DISEASE".equals(normalizedType) || "DISEASE_RECORD".equals(normalizedType)) {
            add(check, "病害信息", hasAny(summary, "diseaseName", "severity") || containsAny(text, "病害", "严重程度"),
                    "应包含病害类型和严重程度");
        } else if ("ASSESSMENT_RESULT".equals(normalizedType) || "ASSESSMENT".equals(normalizedType)) {
            add(check, "指标信息", hasAny(summary, "mqi", "pqi", "pci", "grade") || containsAny(text, "MQI", "PQI", "PCI", "等级"),
                    "应包含评定指标和等级");
        } else {
            add(check, "对象信息", containsAny(text, "对象", "概况", "路线", "路段", "评定单元"),
                    "应包含当前对象概况");
        }

        add(check, "成因判断", containsAny(text, "成因", "原因"),
                "应包含成因判断或低分原因");
        add(check, "处置建议", containsAny(text, "处置建议", "处置策略", "养护建议", "推荐处置"),
                "应包含处置建议或养护策略");
        add(check, "优先级", containsAny(text, "优先级", "近期", "中期", "P1", "P2", "P3"),
                "应包含优先级判断");
        add(check, "风险提示", containsAny(text, "风险提示", "风险"),
                "应包含风险提示");

        boolean passed = true;
        for (MapObjectSolutionQualityItem item : check.getItems()) {
            if (!item.isPassed()) {
                passed = false;
                check.getWarnings().add(item.getMessage());
            }
        }
        check.setPassed(passed);
        return check;
    }

    private void add(MapObjectSolutionQualityCheck check, String name, boolean passed, String message) {
        MapObjectSolutionQualityItem item = new MapObjectSolutionQualityItem();
        item.setName(name);
        item.setPassed(passed);
        item.setMessage(passed ? "已包含：" + name : message);
        item.setLevel(passed ? "OK" : "WARN");
        check.getItems().add(item);
    }

    private boolean hasAny(Map<String, Object> map, String... keys) {
        if (map == null) {
            return false;
        }
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && String.valueOf(value).trim().length() > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(String text, String... words) {
        String value = text == null ? "" : text;
        for (String word : words) {
            if (value.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
