package com.smartroad.srmp.agent.rag;

import java.util.Map;

public class RagOptions {
    private boolean useBusinessData = true;
    private boolean useKnowledge = true;
    private boolean useOutline = false;
    private int topK = 5;

    public static RagOptions from(Map options) {
        RagOptions result = new RagOptions();
        if (options == null) {
            return result;
        }
        result.useBusinessData = readBoolean(options.get("useBusinessData"), true);
        result.useKnowledge = readBoolean(options.get("useKnowledge"), true);
        result.useOutline = readBoolean(options.get("useOutline"), false);
        result.topK = readInt(options.get("topK"), 5);
        if (result.topK <= 0) {
            result.topK = 5;
        }
        if (result.topK > 20) {
            result.topK = 20;
        }
        return result;
    }

    public static RagOptions autoByQuestion(String question, Map options) {
        RagOptions result = from(options);
        String text = question == null ? "" : question;
        if (containsAny(text, "Outline", "outline", "内部文档", "团队文档")) {
            result.useOutline = true;
        }
        if (containsAny(text, "标准", "规范", "流程", "文档", "手册", "模板", "怎么操作", "如何导入")) {
            result.useKnowledge = true;
        }
        return result;
    }

    private static boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private static boolean readBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static int readInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public boolean isUseBusinessData() {
        return useBusinessData;
    }

    public boolean isUseKnowledge() {
        return useKnowledge;
    }

    public boolean isUseOutline() {
        return useOutline;
    }

    public int getTopK() {
        return topK;
    }
}
