-- Phase 36: 一张图 AI Agent 与向量知识库增强
-- 说明：需要 PostgreSQL 安装 pgvector 扩展；若当前环境暂不能安装，可先使用关键词检索兜底。

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS ai_knowledge_document (
    id              VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    source_type     VARCHAR(64) NOT NULL,
    source_id       VARCHAR(128),
    title           VARCHAR(300) NOT NULL,
    status          VARCHAR(32) DEFAULT 'ACTIVE',
    metadata        JSONB DEFAULT '{}'::jsonb,
    content_hash    VARCHAR(64),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_knowledge_chunk (
    id              VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    document_id     VARCHAR(64) NOT NULL,
    source_type     VARCHAR(64),
    source_id       VARCHAR(128),
    title           VARCHAR(300),
    section_title   VARCHAR(300),
    chunk_index     INTEGER,
    content         TEXT NOT NULL,
    metadata        JSONB DEFAULT '{}'::jsonb,
    embedding       VECTOR(1536),
    embedding_model VARCHAR(100),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_knowledge_document_tenant
ON ai_knowledge_document(tenant_id, source_type, source_id);

CREATE INDEX IF NOT EXISTS idx_ai_knowledge_chunk_tenant
ON ai_knowledge_chunk(tenant_id, source_type, document_id);

CREATE INDEX IF NOT EXISTS idx_ai_knowledge_chunk_embedding
ON ai_knowledge_chunk
USING ivfflat (embedding vector_cosine_ops);
