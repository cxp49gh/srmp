package com.smartroad.srmp.agent.solution.template;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MarkdownTemplateRenderer {

    @Data
    public static class RenderResult {
        private String renderedMarkdown;
        private Map<String, Object> variables = new LinkedHashMap<>();
        private List<String> missingVariables = new ArrayList<>();
        private List<String> unusedVariables = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
    }

    public String render(String content, Map<String, Object> variables) {
        String result = content == null ? "" : content;
        if (variables == null || variables.isEmpty()) {
            return result;
        }

        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String text = value == null ? "" : String.valueOf(value);
            result = result.replace("{{" + key + "}}", text);
            result = result.replace("{{ " + key + " }}", text);
        }
        return result;
    }

    public RenderResult renderWithCheck(String content, Map<String, Object> variables) {
        RenderResult result = new RenderResult();
        String rendered = content == null ? "" : content;
        Map<String, Object> safeVariables = variables == null ? new LinkedHashMap<>() : variables;
        List<String> declared = new TemplateVariableParser().parse(rendered);
        Set<String> declaredSet = new LinkedHashSet<>(declared);

        for (String key : declared) {
            Object value = safeVariables.get(key);
            if (isBlankValue(value)) {
                result.getMissingVariables().add(key);
                result.getWarnings().add("变量缺失：" + key);
                continue;
            }
            String text = String.valueOf(value);
            rendered = Pattern.compile("\\{\\{\\s*" + Pattern.quote(key) + "\\s*}}")
                    .matcher(rendered)
                    .replaceAll(Matcher.quoteReplacement(text));
            result.getVariables().put(key, value);
        }

        for (Map.Entry<String, Object> entry : safeVariables.entrySet()) {
            if (!declaredSet.contains(entry.getKey()) && !isBlankValue(entry.getValue())) {
                result.getUnusedVariables().add(entry.getKey());
            }
        }

        result.setRenderedMarkdown(rendered);
        return result;
    }

    private boolean isBlankValue(Object value) {
        return value == null || String.valueOf(value).trim().isEmpty();
    }
}
