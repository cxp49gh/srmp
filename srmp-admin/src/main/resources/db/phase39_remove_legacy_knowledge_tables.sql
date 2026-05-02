-- Phase39: 移除旧知识库表，并清理 Outline 默认文档污染
-- 旧 knowledge_document / knowledge_chunk 已被 ai_knowledge_document / ai_knowledge_chunk 替代。
-- 注意：执行前请确认旧表数据已无需保留，或已完成迁移/备份。

-- 清理已经保存到方案任务引用表里的 Outline 产品默认文档。
DELETE FROM ai_solution_source
WHERE lower(coalesce(source_title, '')) IN (
    'our editor',
    'what is outline',
    'getting started',
    'integrations & api',
    'integrations and api'
);

-- 如果之前误同步到了新向量知识库，也一并清理。
DELETE FROM ai_knowledge_chunk c
USING ai_knowledge_document d
WHERE c.tenant_id = d.tenant_id
  AND c.document_id = d.id
  AND d.source_type = 'OUTLINE'
  AND lower(coalesce(d.title, '')) IN (
      'our editor',
      'what is outline',
      'getting started',
      'integrations & api',
      'integrations and api'
  );

DELETE FROM ai_knowledge_document
WHERE source_type = 'OUTLINE'
  AND lower(coalesce(title, '')) IN (
      'our editor',
      'what is outline',
      'getting started',
      'integrations & api',
      'integrations and api'
  );

DROP TABLE IF EXISTS knowledge_chunk;
DROP TABLE IF EXISTS knowledge_document;
