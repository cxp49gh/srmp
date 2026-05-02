package com.smartroad.srmp.agent.solution.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AI 方案引用来源守卫。
 *
 * <p>区域养护/方案任务只应展示和使用道路养护相关来源；Outline 初始化自带的产品说明、编辑器、入门、API 文档
 * 即使被同步进知识库，也不能作为道路养护方案依据。</p>
 */
public final class AiSolutionReferenceSourceGuard {

    private static final List<String> OUTLINE_DEFAULT_TITLES = Arrays.asList(
            "our editor",
            "what is outline",
            "getting started",
            "integrations & api",
            "integrations and api",
            "outline api",
            "welcome to outline"
    );

    private static final List<String> OUTLINE_DEFAULT_CONTENT_MARKERS = Arrays.asList(
            "outline is a",
            "collaborative document",
            "slash commands",
            "markdown shortcuts",
            "integrations & api",
            "getting started with outline"
    );

    private static final List<String> ROAD_MAINTENANCE_KEYWORDS = Arrays.asList(
            "区域养护", "框选区域", "公路", "道路", "路线", "路段", "路面", "养护", "巡查",
            "病害", "裂缝", "坑槽", "车辙", "沉陷", "松散", "修补", "处治", "处置",
            "评定", "评定单元", "技术状况", "低分", "预防性", "中修", "大修", "罩面", "封层",
            "灌缝", "铣刨", "重铺", "工单", "mqi", "pqi", "pci", "rqi", "rdi"
    );

    private AiSolutionReferenceSourceGuard() {
    }

    /** 过滤方案引用来源中的 Outline 默认说明类文档。 */
    public static List<Map<String, Object>> filterIrrelevantOutlineSources(Collection<Map<String, Object>> sources) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (sources == null || sources.isEmpty()) {
            return result;
        }
        for (Map<String, Object> source : sources) {
            if (source == null || source.isEmpty() || isIrrelevantOutlineSource(source)) {
                continue;
            }
            result.add(source);
        }
        return result;
    }

    /** 判断一条 Map 结构来源是否为 Outline 默认说明类文档。 */
    public static boolean isIrrelevantOutlineSource(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return false;
        }
        String type = firstString(source, "sourceType", "source_type", "type").toUpperCase(Locale.ROOT);
        String title = firstString(source, "sourceTitle", "source_title", "title", "name");
        String excerpt = firstString(source, "contentExcerpt", "content_excerpt", "excerpt", "content", "text");
        String text = (safe(title) + "\n" + safe(excerpt)).toLowerCase(Locale.ROOT);

        boolean outlineLike = "OUTLINE".equals(type)
                || containsOutlineDefaultTitle(title)
                || text.contains("outline")
                || containsAny(text, OUTLINE_DEFAULT_CONTENT_MARKERS);
        if (!outlineLike) {
            return false;
        }
        // 允许真正的道路养护 Outline 文档继续作为来源。
        if (isRoadMaintenanceText(title, excerpt)) {
            return false;
        }
        return isIrrelevantOutlineDoc(title, excerpt);
    }

    /** 判断文档标题/内容是否为 Outline 默认说明类文档。 */
    public static boolean isIrrelevantOutlineDoc(String title, String content) {
        String normalizedTitle = safe(title).toLowerCase(Locale.ROOT);
        if (containsOutlineDefaultTitle(normalizedTitle)) {
            return true;
        }
        String text = (safe(title) + "\n" + safe(content)).toLowerCase(Locale.ROOT);
        return containsAny(text, OUTLINE_DEFAULT_CONTENT_MARKERS);
    }

    /** 判断文本是否包含道路养护领域关键词。 */
    public static boolean isRoadMaintenanceText(String... values) {
        if (values == null || values.length == 0) {
            return false;
        }
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            if (value != null) {
                sb.append(value).append('\n');
            }
        }
        String text = sb.toString().toLowerCase(Locale.ROOT);
        return containsAny(text, ROAD_MAINTENANCE_KEYWORDS);
    }

    private static boolean containsOutlineDefaultTitle(String title) {
        String value = safe(title).toLowerCase(Locale.ROOT);
        for (String blocked : OUTLINE_DEFAULT_TITLES) {
            if (value.equals(blocked) || value.contains(blocked)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String text, Collection<String> keywords) {
        if (text == null || text.isEmpty() || keywords == null || keywords.isEmpty()) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isEmpty() && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String firstString(Map<String, Object> map, String... keys) {
        if (map == null || keys == null) {
            return "";
        }
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && String.valueOf(value).trim().length() > 0) {
                return String.valueOf(value).trim();
            }
        }
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
