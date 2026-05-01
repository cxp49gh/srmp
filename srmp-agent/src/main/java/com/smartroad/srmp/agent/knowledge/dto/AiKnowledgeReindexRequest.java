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
     * 可选：指定文档 ID。
     */
    private List<String> documentIds;

    /**
     * true：强制重算所有符合条件的 chunk；
     * false：只补齐 embedding 为空的 chunk。
     */
    private Boolean force = false;

    /**
     * 可选：本次最多处理多少 chunk，防止一次性重建过多数据。
     */
    private Integer limit;
}
