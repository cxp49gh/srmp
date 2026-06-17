package com.smartroad.srmp.agent.outline.support;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OutlineKnowledgeDocumentPreprocessorTest {

    @Test
    public void parsesSrmpMetadataAndRemovesMetadataBlockFromSearchText() {
        String markdown = "## SRMP 元数据\n\n" +
                "```yaml\n" +
                "srmpKnowledgeType: TEMPLATE_BLUEPRINT\n" +
                "templateCode: route_report_default\n" +
                "objectTypes:\n" +
                "  - ROAD_ROUTE\n" +
                "capabilityIds:\n" +
                "  - solution.route_report\n" +
                "ragEnabled: false\n" +
                "```\n\n" +
                "## 模板正文蓝本\n\n" +
                "# {{routeName}} 养护报告\n";

        OutlineKnowledgeDocumentPreprocessor.PreparedDocument prepared =
                OutlineKnowledgeDocumentPreprocessor.prepare(markdown);

        assertFalse(prepared.isRagEnabled());
        assertEquals("TEMPLATE_BLUEPRINT", prepared.getMetadata().get("srmpKnowledgeType"));
        assertEquals("route_report_default", prepared.getMetadata().get("templateCode"));
        assertEquals("ROAD_ROUTE", ((List<?>) prepared.getMetadata().get("objectTypes")).get(0));
        assertEquals("solution.route_report", ((List<?>) prepared.getMetadata().get("capabilityIds")).get(0));
        assertFalse(prepared.getSearchableText().contains("SRMP 元数据"));
        assertFalse(prepared.getSearchableText().contains("templateCode"));
        assertTrue(prepared.getSearchableText().contains("模板正文蓝本"));
    }

    @Test
    public void keepsPlainOutlineDocumentsSearchableWhenMetadataIsAbsent() {
        String markdown = "# 公路养护知识\n\nPCI 与裂缝、坑槽有关。";

        OutlineKnowledgeDocumentPreprocessor.PreparedDocument prepared =
                OutlineKnowledgeDocumentPreprocessor.prepare(markdown);

        assertTrue(prepared.isRagEnabled());
        assertTrue(prepared.getMetadata().isEmpty());
        assertEquals(markdown, prepared.getSearchableText());
    }

    @Test
    public void treatsTemplateBlueprintAsNonRagEvenWhenRagEnabledIsMissing() {
        String markdown = "## SRMP 元数据\n\n" +
                "```yaml\n" +
                "srmpKnowledgeType: TEMPLATE_BLUEPRINT\n" +
                "templateCode: disease_treatment\n" +
                "```\n\n" +
                "## 模板正文蓝本\n";

        OutlineKnowledgeDocumentPreprocessor.PreparedDocument prepared =
                OutlineKnowledgeDocumentPreprocessor.prepare(markdown);

        assertFalse(prepared.isRagEnabled());
    }
}
