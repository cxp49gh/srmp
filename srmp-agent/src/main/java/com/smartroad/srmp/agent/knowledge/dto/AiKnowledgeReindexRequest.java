package com.smartroad.srmp.agent.knowledge.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiKnowledgeReindexRequest {
    private String tenantId;

    /**
     * 可选：MANUAL / OUTLINE / TEMPLATE / SOLUTION_TASK 等。
     */
    private String sourceType;

    /**
     * 可选：指定 ai_knowledge_document.id。
     */
    private List<String> documentIds;

    /**
     * 可选：指定业务来源 ID，例如 Outline documentId。
     */
    private List<String> sourceIds;

    /**
     * true：强制重算所有符合条件的 chunk；
     * false：只补齐 embedding 为空的 chunk。
     */
    private Boolean force = false;

    /**
     * true：只统计候选 chunk，不写入 embedding。
     */
    private Boolean dryRun = false;

    /**
     * 可选：本次最多处理多少 chunk，防止一次性重建过多数据。默认 200，上限 2000。
     */
    private Integer limit;
}
