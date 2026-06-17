package com.smartroad.srmp.agent.knowledge.service.impl;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AiKnowledgeRetrieverServiceImplTest {
    @Test
    public void vectorFallbackReasonDistinguishesEmptyKnowledgeFromMissingVectors() {
        assertEquals("no knowledge chunks", AiKnowledgeRetrieverServiceImpl.vectorFallbackReason(0, 0));
        assertEquals("no embedded chunks", AiKnowledgeRetrieverServiceImpl.vectorFallbackReason(12, 0));
        assertEquals("", AiKnowledgeRetrieverServiceImpl.vectorFallbackReason(12, 4));
    }

    @Test
    public void metadataFilterSqlMatchesCapabilityAndSolutionTypeArrays() {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("capabilityIds", Arrays.asList("solution.route_report"));
        filters.put("solutionTypes", Arrays.asList("ROUTE_REPORT"));

        String sql = AiKnowledgeRetrieverServiceImpl.metadataFilterSqlForTest(filters);

        assertTrue(sql.contains("capabilityIds"));
        assertTrue(sql.contains(":capabilityIds"));
        assertTrue(sql.contains("solutionTypes"));
        assertTrue(sql.contains(":solutionTypes"));
    }

    @Test
    public void metadataFilterSqlIgnoresBlankFilters() {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("capabilityIds", Arrays.asList("", null));

        assertFalse(AiKnowledgeRetrieverServiceImpl.metadataFilterSqlForTest(filters).contains("capabilityIds"));
    }
}
