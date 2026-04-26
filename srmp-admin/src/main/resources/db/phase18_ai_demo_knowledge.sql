-- 阶段十八：AI 知识库演示数据
-- 执行前建议先执行：
-- psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/phase13_knowledge_outline.sql
--
-- 执行：
-- psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/phase18_ai_demo_knowledge.sql

INSERT INTO knowledge_document(
    id,
    tenant_id,
    source_type,
    source_id,
    title,
    doc_type,
    category,
    content_hash,
    version,
    url,
    status,
    synced_at,
    created_at,
    updated_at,
    deleted
)
VALUES
(
    'demo-knowledge-disease-review-flow',
    'default',
    'LOCAL',
    NULL,
    '病害复核与处置建议流程',
    'MARKDOWN',
    'MAINTENANCE_FLOW',
    'phase18-disease-review-flow',
    'v1',
    NULL,
    'ENABLED',
    now(),
    now(),
    now(),
    false
),
(
    'demo-knowledge-g210-ai-report-guide',
    'default',
    'LOCAL',
    NULL,
    'G210 技术状况评定报告生成说明',
    'MARKDOWN',
    'SYSTEM_MANUAL',
    'phase18-g210-report-guide',
    'v1',
    NULL,
    'ENABLED',
    now(),
    now(),
    now(),
    false
)
ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title,
    content_hash = EXCLUDED.content_hash,
    updated_at = now(),
    deleted = false;

DELETE FROM knowledge_chunk
WHERE tenant_id = 'default'
  AND document_id IN (
    'demo-knowledge-disease-review-flow',
    'demo-knowledge-g210-ai-report-guide'
  );

INSERT INTO knowledge_chunk(
    id,
    tenant_id,
    document_id,
    chunk_no,
    heading,
    content,
    content_tokens,
    source_type,
    source_url,
    metadata,
    created_at,
    updated_at,
    deleted
)
VALUES
(
    'demo-chunk-disease-review-flow-1',
    'default',
    'demo-knowledge-disease-review-flow',
    1,
    '病害复核流程',
    '# 病害复核流程

病害数据导入平台后，应先进入复核环节。复核内容包括：路线编号、起终点桩号、病害类型、严重程度、面积或长度、几何位置、现场图片和关联评定单元。复核通过后，病害可以进入统计分析、AI 研判和后续养护计划。复核不通过时，应退回数据来源进行修正。',
    160,
    'LOCAL',
    NULL,
    '{}'::jsonb,
    now(),
    now(),
    false
),
(
    'demo-chunk-disease-review-flow-2',
    'default',
    'demo-knowledge-disease-review-flow',
    2,
    '病害处置建议原则',
    '# 病害处置建议原则

对影响 PCI 的裂缝、坑槽、沉陷、车辙等病害，应结合严重程度、分布密度、路线等级和交通量确定处置优先级。重度坑槽、沉陷和连续裂缝应优先处置。轻度裂缝可结合预防性养护措施进行处置。AI 生成的处置建议仅作为草稿，正式计划和工单应由管理人员审核确认。',
    170,
    'LOCAL',
    NULL,
    '{}'::jsonb,
    now(),
    now(),
    false
),
(
    'demo-chunk-g210-ai-report-guide-1',
    'default',
    'demo-knowledge-g210-ai-report-guide',
    1,
    'G210 评定报告生成说明',
    '# G210 评定报告生成说明

生成 G210 技术状况评定报告时，应包含路线概况、评定年度、评定单元数量、平均 MQI/PQI/PCI、等级分布、低分路段、主要病害类型、处置建议和后续养护重点。报告中的统计数据应优先来自 assessment_result、disease_record 和 road_route 等业务表。',
    180,
    'LOCAL',
    NULL,
    '{}'::jsonb,
    now(),
    now(),
    false
);
