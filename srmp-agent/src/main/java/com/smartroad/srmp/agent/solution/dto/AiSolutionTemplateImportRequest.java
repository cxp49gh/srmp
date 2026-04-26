package com.smartroad.srmp.agent.solution.dto;

import lombok.Data;

@Data
public class AiSolutionTemplateImportRequest {
    /**
     * knowledge_document.id
     */
    private String knowledgeDocumentId;

    /**
     * 模板编码，可为空，为空时按标题生成。
     */
    private String templateCode;

    /**
     * 模板名称，可为空，为空时使用知识库文档标题。
     */
    private String templateName;

    /**
     * 方案类型，例如 ROAD_ASSESSMENT_REPORT。
     */
    private String solutionType;

    /**
     * 是否强制生成新版本。
     */
    private Boolean force;
}
