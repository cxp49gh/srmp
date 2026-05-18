package com.smartroad.srmp.agent.outline.dto;

import lombok.Data;

@Data
public class OutlineDocumentDTO {
    private String id;
    private String collectionId;
    private String title;
    private String text;
    private String url;
    private String updatedAt;
    /** 本地知识库文档 ID */
    private String knowledgeDocumentId;
    /** NOT_SYNCED | INACTIVE | PENDING_VECTOR | RAG_READY */
    private String kbSyncStatus;
    private Integer chunkCount;
    private Integer embeddedChunkCount;
    /** 是否可用于 RAG */
    private Boolean ragReady;
}
