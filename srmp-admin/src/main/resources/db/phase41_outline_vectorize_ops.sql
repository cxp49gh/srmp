-- Phase41: Outline 向量补齐运维辅助索引
-- 说明：Outline 同步已统一落到 ai_knowledge_document / ai_knowledge_chunk。
-- 本脚本只增加补向量和统计查询用索引，不再创建旧 knowledge_document / knowledge_chunk。

create index if not exists idx_ai_knowledge_chunk_source_embedding
    on ai_knowledge_chunk(tenant_id, source_type, document_id, chunk_index)
    where embedding is null;

create index if not exists idx_ai_knowledge_document_source_active
    on ai_knowledge_document(tenant_id, source_type, status, updated_at desc);

comment on index idx_ai_knowledge_chunk_source_embedding is '用于 /api/outline/vectorize 快速查找 OUTLINE 未向量化 chunk';
comment on index idx_ai_knowledge_document_source_active is '用于 /api/outline/knowledge-stats 统计 OUTLINE 新知识库文档';
