package com.smartroad.srmp.agent.outline.dto;

import lombok.Data;

import java.util.List;

@Data
public class OutlineSyncRequest {
    private String collectionId;
    private Integer limit;
    private Boolean force;

    /**
     * true 时只计算 CREATE / UPDATE / SKIP，不实际写入知识库。
     */
    private Boolean dryRun;

    /**
     * true 时将本次 Outline 不再返回的本地 OUTLINE 文档标记为 INACTIVE。
     */
    private Boolean cleanupMissing;

    /**
     * 指定文档 ID 同步，用于重试失败文档。
     */
    private List<String> documentIds;

    /**
     * 来源任务 ID，重试失败明细时使用。
     */
    private String retryTaskId;
}
