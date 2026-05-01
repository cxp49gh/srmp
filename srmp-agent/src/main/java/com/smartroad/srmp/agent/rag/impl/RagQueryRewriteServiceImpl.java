package com.smartroad.srmp.agent.rag.impl;

import com.smartroad.srmp.agent.mapagent.dto.MapAiContext;
import com.smartroad.srmp.agent.rag.RagQueryRewriteService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Service
public class RagQueryRewriteServiceImpl implements RagQueryRewriteService {

    @Override
    public String rewrite(String userQuestion, MapAiContext context) {
        Set<String> tokens = new LinkedHashSet<>();
        append(tokens, userQuestion);

        if (context != null) {
            append(tokens, context.getMode());
            append(tokens, context.getRouteCode());
            append(tokens, context.getYear());

            Map<String, Object> obj = context.getMapObject();
            if (obj != null) {
                String objectType = firstString(obj, "objectType", "object_type", "type", "layerType");
                String diseaseName = firstString(obj, "diseaseName", "disease_name", "diseaseType", "disease_type");
                String severity = firstString(obj, "severity", "level", "grade");
                String routeCode = firstString(obj, "routeCode", "route_code");

                append(tokens, objectType);
                append(tokens, diseaseName);
                append(tokens, severity);
                append(tokens, routeCode);

                appendDomainTerms(tokens, diseaseName, severity, objectType);
            }

            if (context.getRegionSummary() != null && !context.getRegionSummary().isEmpty()) {
                append(tokens, "区域 养护建议 病害集中 低分单元 优先级");
            }
        }

        append(tokens, "道路养护 成因判断 处置建议 现场复核 风险提示");
        return String.join(" ", tokens).trim();
    }

    private void appendDomainTerms(Set<String> tokens, String diseaseName, String severity, String objectType) {
        String name = diseaseName == null ? "" : diseaseName;
        String type = objectType == null ? "" : objectType;
        if (containsAny(name, "修补损坏", "修补")) {
            append(tokens, "修补损坏 修补边界 基层 排水 局部铣刨 重新摊铺 热补 修复");
        }
        if (containsAny(name, "裂缝", "开裂") || containsAny(type, "CRACK")) {
            append(tokens, "裂缝 中度裂缝 灌缝 开槽灌缝 封缝 封层 薄层罩面 渗水 下渗 水损害");
        }
        if (containsAny(name, "坑槽")) {
            append(tokens, "坑槽 切割边界 清理松散材料 冷补 热补 压实 基层破坏");
        }
        if (containsAny(name, "车辙", "沉陷")) {
            append(tokens, "车辙 沉陷 铣刨罩面 微表处 结构层 路基 排水");
        }
        if (containsAny(name, "评定指标", "MQI", "PQI", "PCI")) {
            append(tokens, "MQI PQI PCI 路面技术状况 路面损坏指数 使用性能 低分原因");
        }
        if (severity != null && severity.length() > 0) {
            append(tokens, severity + " 严重程度 处置优先级");
        }
    }

    private void append(Set<String> tokens, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value).trim();
        if (text.length() == 0) {
            return;
        }
        for (String part : text.split("[,，;；\\s]+")) {
            if (part != null && part.trim().length() > 0) {
                tokens.add(part.trim());
            }
        }
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

    private String firstString(Map<String, Object> map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && String.valueOf(value).trim().length() > 0) {
                return String.valueOf(value);
            }
        }
        return null;
    }
}
