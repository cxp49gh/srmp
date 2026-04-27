package com.smartroad.srmp.agent.solution.template;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MarkdownTemplateRenderer {

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
}
