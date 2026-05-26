package com.smartroad.srmp.agent.execution.service.impl;

import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class AiExecutionServiceImplTest {
    @Test
    public void refreshDetailSummaryCountsUsesLoadedToolsAndSources() {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("toolTotalCount", 0);
        detail.put("toolSuccessCount", 0);
        detail.put("toolFailedCount", 0);
        detail.put("sourceCount", 0);

        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(mapOf("toolName", "solution.generateDraft", "status", "SUCCESS"));
        tools.add(mapOf("toolName", "knowledge.retrieve", "success", false));

        List<Map<String, Object>> sources = new ArrayList<>();
        sources.add(mapOf("sourceType", "TEMPLATE"));
        sources.add(mapOf("sourceType", "TEMPLATE_VARIABLE"));

        AiExecutionServiceImpl.refreshDetailSummaryCounts(detail, tools, sources);

        assertEquals(2, detail.get("toolTotalCount"));
        assertEquals(1, detail.get("toolSuccessCount"));
        assertEquals(1, detail.get("toolFailedCount"));
        assertEquals(2, detail.get("sourceCount"));
    }

    private Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }
}
