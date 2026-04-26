package com.smartroad.srmp.agent.knowledge.splitter;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TextChunkSplitter {

    public List<String> split(String content) {
        List<String> result = new ArrayList<>();
        if (content == null || content.trim().isEmpty()) {
            return result;
        }

        String normalized = content.replace("\r\n", "\n");
        String[] paragraphs = normalized.split("\n\n+");

        StringBuilder current = new StringBuilder();
        int maxLength = 1200;

        for (String paragraph : paragraphs) {
            String text = paragraph.trim();
            if (text.isEmpty()) {
                continue;
            }

            if (current.length() + text.length() > maxLength && current.length() > 0) {
                result.add(current.toString().trim());
                current.setLength(0);
            }

            current.append(text).append("\n\n");
        }

        if (current.length() > 0) {
            result.add(current.toString().trim());
        }

        if (result.isEmpty()) {
            result.add(normalized);
        }

        return result;
    }
}
