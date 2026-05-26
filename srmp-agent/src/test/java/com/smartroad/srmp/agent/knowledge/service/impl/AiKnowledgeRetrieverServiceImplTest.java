package com.smartroad.srmp.agent.knowledge.service.impl;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AiKnowledgeRetrieverServiceImplTest {
    @Test
    public void vectorFallbackReasonDistinguishesEmptyKnowledgeFromMissingVectors() {
        assertEquals("no knowledge chunks", AiKnowledgeRetrieverServiceImpl.vectorFallbackReason(0, 0));
        assertEquals("no embedded chunks", AiKnowledgeRetrieverServiceImpl.vectorFallbackReason(12, 0));
        assertEquals("", AiKnowledgeRetrieverServiceImpl.vectorFallbackReason(12, 4));
    }
}
