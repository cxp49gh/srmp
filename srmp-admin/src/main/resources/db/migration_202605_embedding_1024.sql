-- Align ai_knowledge_chunk.embedding with 1024-dimensional embedding models.
-- Existing vectors with other dimensions cannot be safely cast; clear them and reindex afterward.

DROP INDEX IF EXISTS idx_ai_knowledge_chunk_embedding;

ALTER TABLE ai_knowledge_chunk
    ALTER COLUMN embedding TYPE VECTOR(1024)
    USING NULL;

UPDATE ai_knowledge_chunk
SET embedding = NULL,
    embedding_provider = NULL,
    embedding_model = NULL,
    embedding_dimensions = NULL,
    embedded_at = NULL,
    updated_at = now();

CREATE INDEX IF NOT EXISTS idx_ai_knowledge_chunk_embedding
ON ai_knowledge_chunk
USING ivfflat (embedding vector_cosine_ops);
