package com.smartroad.srmp.agent.solution.support;

import java.util.*;
import java.util.regex.Pattern;

/** Phase38.6：系统兜底模板来源守卫。 */
public final class AiSolutionFallbackSourceGuard {
    private static final String SYSTEM_FALLBACK_TITLE = "系统兜底模板";
    private static final String SYSTEM_FALLBACK_TYPE = "SYSTEM_TEMPLATE";
    private static final Pattern EMBEDDED_FALLBACK_SOURCE_SECTION = Pattern.compile(
            "(?s)\\n*##\\s*(六、)?引用来源\\s*\\n\\s*[-*]\\s*系统兜底模板\\s*\\n*\\s*$"
    );
    private AiSolutionFallbackSourceGuard() {}
    public static String stripEmbeddedFallbackSourceSection(String content) {
        if (content == null || content.trim().isEmpty()) return content;
        return EMBEDDED_FALLBACK_SOURCE_SECTION.matcher(content).replaceFirst("").trim();
    }
    public static boolean isSystemFallbackSource(Map<String, Object> source) {
        if (source == null) return false;
        String title = firstString(source, "sourceTitle", "source_title", "title", "name");
        String type = firstString(source, "sourceType", "source_type", "type");
        String id = firstString(source, "sourceId", "source_id", "id");
        return SYSTEM_FALLBACK_TYPE.equalsIgnoreCase(safe(type)) || containsFallback(title) || containsFallback(id);
    }
    public static Map<String, Object> normalizeSource(Map<String, Object> source) {
        Map<String, Object> item = source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
        if (isSystemFallbackSource(item)) {
            putBoth(item, "sourceType", "source_type", SYSTEM_FALLBACK_TYPE);
            putBoth(item, "sourceTitle", "source_title", SYSTEM_FALLBACK_TITLE);
            if (!hasAny(item, "contentExcerpt", "content_excerpt", "excerpt", "content")) {
                putBoth(item, "contentExcerpt", "content_excerpt", "系统使用内置兜底模板生成方案草稿。");
            }
        }
        return item;
    }
    public static List<Map<String, Object>> dedupeSources(Collection<Map<String, Object>> sources) {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (sources == null) return result;
        for (Map<String, Object> source : sources) {
            if (source == null || source.isEmpty()) continue;
            Map<String, Object> normalized = normalizeSource(source);
            String key = sourceKey(normalized);
            if (seen.add(key)) result.add(normalized);
        }
        return result;
    }
    private static String sourceKey(Map<String, Object> source) {
        if (isSystemFallbackSource(source)) return "SYSTEM_TEMPLATE|SYSTEM_FALLBACK_TEMPLATE";
        String type = firstString(source, "sourceType", "source_type", "type");
        String id = firstString(source, "sourceId", "source_id", "id");
        String title = firstString(source, "sourceTitle", "source_title", "title", "name");
        String excerpt = firstString(source, "contentExcerpt", "content_excerpt", "excerpt", "content");
        if (!safe(type).isEmpty() || !safe(id).isEmpty()) return safe(type) + "|" + safe(id) + "|" + safe(title);
        return "TITLE|" + safe(title) + "|" + shortText(excerpt, 80);
    }
    private static boolean containsFallback(String value) {
        String v = safe(value);
        return v.contains("系统兜底模板") || v.contains("兜底模板") || v.contains("Fallback Template") || v.contains("SYSTEM_FALLBACK_TEMPLATE");
    }
    private static boolean hasAny(Map<String, Object> map, String... keys) {
        if (map == null) return false;
        for (String key : keys) { Object value = map.get(key); if (value != null && !String.valueOf(value).trim().isEmpty()) return true; }
        return false;
    }
    private static void putBoth(Map<String, Object> map, String camel, String snake, Object value) { map.put(camel, value); map.put(snake, value); }
    private static String firstString(Map<String, Object> map, String... keys) {
        if (map == null) return "";
        for (String key : keys) { Object value = map.get(key); if (value != null && !String.valueOf(value).trim().isEmpty()) return String.valueOf(value).trim(); }
        return "";
    }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static String shortText(String value, int max) { return value == null ? "" : (value.length() <= max ? value : value.substring(0, max)); }
}
