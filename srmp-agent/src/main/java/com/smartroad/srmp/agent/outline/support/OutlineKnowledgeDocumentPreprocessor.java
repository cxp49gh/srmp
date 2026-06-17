package com.smartroad.srmp.agent.outline.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses the lightweight SRMP metadata block maintained in Outline documents.
 */
public final class OutlineKnowledgeDocumentPreprocessor {

    private static final String METADATA_HEADING = "## SRMP 元数据";
    private static final String TEMPLATE_BLUEPRINT = "TEMPLATE_BLUEPRINT";

    private OutlineKnowledgeDocumentPreprocessor() {
    }

    public static PreparedDocument prepare(String markdown) {
        String text = markdown == null ? "" : markdown;
        MetadataBlock block = extractMetadataBlock(text);
        Map<String, Object> metadata = block == null ? Collections.<String, Object>emptyMap() : parseYamlBlock(block.yamlText);
        String searchableText = block == null ? text : removeBlock(text, block.start, block.end);
        return new PreparedDocument(metadata, searchableText, resolveRagEnabled(metadata));
    }

    private static boolean resolveRagEnabled(Map<String, Object> metadata) {
        Object explicit = metadata.get("ragEnabled");
        if (explicit instanceof Boolean) {
            return (Boolean) explicit;
        }
        Object type = metadata.get("srmpKnowledgeType");
        if (type != null && TEMPLATE_BLUEPRINT.equalsIgnoreCase(String.valueOf(type).trim())) {
            return false;
        }
        return true;
    }

    private static MetadataBlock extractMetadataBlock(String text) {
        int heading = text.indexOf(METADATA_HEADING);
        if (heading < 0) {
            return null;
        }
        int firstFence = text.indexOf("```", heading + METADATA_HEADING.length());
        if (firstFence < 0) {
            return null;
        }
        int firstFenceLineEnd = text.indexOf('\n', firstFence);
        if (firstFenceLineEnd < 0) {
            return null;
        }
        int closingFence = text.indexOf("```", firstFenceLineEnd + 1);
        if (closingFence < 0) {
            return null;
        }
        int end = closingFence + 3;
        while (end < text.length() && (text.charAt(end) == '\r' || text.charAt(end) == '\n')) {
            end++;
        }
        MetadataBlock block = new MetadataBlock();
        block.start = heading;
        block.end = end;
        block.yamlText = text.substring(firstFenceLineEnd + 1, closingFence);
        return block;
    }

    private static String removeBlock(String text, int start, int end) {
        String cleaned = (text.substring(0, start) + text.substring(end)).trim();
        return cleaned.length() == 0 ? text.trim() : cleaned;
    }

    private static Map<String, Object> parseYamlBlock(String yamlText) {
        Map<String, Object> result = new LinkedHashMap<>();
        String currentListKey = null;
        String[] lines = (yamlText == null ? "" : yamlText).split("\\r?\\n");
        for (String raw : lines) {
            String line = raw == null ? "" : raw;
            String trimmed = line.trim();
            if (trimmed.length() == 0 || trimmed.startsWith("#")) {
                continue;
            }
            if (trimmed.startsWith("- ") && currentListKey != null) {
                Object existing = result.get(currentListKey);
                if (!(existing instanceof List)) {
                    existing = new ArrayList<String>();
                    result.put(currentListKey, existing);
                }
                ((List<Object>) existing).add(parseScalar(trimmed.substring(2).trim()));
                continue;
            }
            int colon = trimmed.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = trimmed.substring(0, colon).trim();
            String value = trimmed.substring(colon + 1).trim();
            if (value.length() == 0) {
                List<Object> list = new ArrayList<>();
                result.put(key, list);
                currentListKey = key;
            } else {
                result.put(key, parseScalar(value));
                currentListKey = null;
            }
        }
        return result;
    }

    private static Object parseScalar(String value) {
        String text = stripQuotes(value == null ? "" : value.trim());
        String lower = text.toLowerCase(Locale.ROOT);
        if ("true".equals(lower)) {
            return Boolean.TRUE;
        }
        if ("false".equals(lower)) {
            return Boolean.FALSE;
        }
        if ("[]".equals(text)) {
            return new ArrayList<Object>();
        }
        return text;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static class MetadataBlock {
        private int start;
        private int end;
        private String yamlText;
    }

    public static final class PreparedDocument {
        private final Map<String, Object> metadata;
        private final String searchableText;
        private final boolean ragEnabled;

        private PreparedDocument(Map<String, Object> metadata, String searchableText, boolean ragEnabled) {
            this.metadata = metadata;
            this.searchableText = searchableText == null ? "" : searchableText;
            this.ragEnabled = ragEnabled;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public String getSearchableText() {
            return searchableText;
        }

        public boolean isRagEnabled() {
            return ragEnabled;
        }
    }
}
