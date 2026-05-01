-- Phase37.1: 知识库 Reindex / Re-embedding 能力
-- 用于记录每个 chunk 的 embedding 生成来源，并支持后续判断是否存在 mock/旧模型向量残留。

ALTER TABLE ai_knowledge_chunk
ADD COLUMN IF NOT EXISTS embedding_provider VARCHAR(64);

ALTER TABLE ai_knowledge_chunk
ADD COLUMN IF NOT EXISTS embedding_dimensions INTEGER;

ALTER TABLE ai_knowledge_chunk
ADD COLUMN IF NOT EXISTS embedded_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_ai_knowledge_chunk_embedding_meta
ON ai_knowledge_chunk(tenant_id, embedding_provider, embedding_model, embedding_dimensions);

-- 尽量为历史数据补一个保守的默认标识，避免 stats 中全部显示 UNKNOWN。
-- 注意：这只是历史标记，不代表这些向量一定由当前 provider 生成。
UPDATE ai_knowledge_chunk
SET embedding_provider = COALESCE(embedding_provider, 'UNKNOWN'),
    embedding_dimensions = COALESCE(embedding_dimensions, 1536),
    embedded_at = COALESCE(embedded_at, updated_at)
WHERE embedding IS NOT NULL
  AND (embedding_provider IS NULL OR embedding_dimensions IS NULL OR embedded_at IS NULL);
