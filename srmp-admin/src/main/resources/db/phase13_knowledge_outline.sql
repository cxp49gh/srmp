-- 阶段十三：一期 AI 知识库增强与 Outline 接入
-- 执行：
-- psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/phase13_knowledge_outline.sql

CREATE TABLE IF NOT EXISTS knowledge_document (
    id              VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    source_type     VARCHAR(50) NOT NULL,
    source_id       VARCHAR(200),
    title           VARCHAR(500) NOT NULL,
    doc_type        VARCHAR(50),
    category        VARCHAR(100),
    content_hash    VARCHAR(128),
    version         VARCHAR(50),
    url             VARCHAR(1000),
    status          VARCHAR(30) DEFAULT 'ENABLED',
    synced_at       TIMESTAMP,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id              VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    document_id     VARCHAR(64) NOT NULL,
    chunk_no        INTEGER NOT NULL,
    heading         VARCHAR(500),
    content         TEXT NOT NULL,
    content_tokens  INTEGER,
    source_type     VARCHAR(50),
    source_url      VARCHAR(1000),
    metadata        JSONB DEFAULT '{}'::jsonb,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_knowledge_document_tenant
ON knowledge_document(tenant_id, source_type, category);

CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_doc
ON knowledge_chunk(tenant_id, document_id);

CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_text
ON knowledge_chunk USING gin(to_tsvector('simple', content));

-- 演示知识库：系统操作与评定指标说明
INSERT INTO knowledge_document(
    id, tenant_id, source_type, source_id, title, doc_type, category, content_hash, version, url, status, created_at, updated_at, deleted
)
VALUES
('demo-knowledge-import-template', 'default', 'LOCAL', NULL, '智路养护平台数据导入说明', 'MARKDOWN', 'IMPORT_TEMPLATE', 'demo-import', 'v1', NULL, 'ENABLED', now(), now(), false),
('demo-knowledge-assessment-index', 'default', 'LOCAL', NULL, '公路技术状况评定指标说明', 'MARKDOWN', 'ROAD_STANDARD', 'demo-assessment', 'v1', NULL, 'ENABLED', now(), now(), false)
ON CONFLICT (id) DO NOTHING;

DELETE FROM knowledge_chunk WHERE tenant_id='default' AND document_id IN ('demo-knowledge-import-template', 'demo-knowledge-assessment-index');

INSERT INTO knowledge_chunk(
    id, tenant_id, document_id, chunk_no, heading, content, content_tokens, source_type, source_url, metadata, created_at, updated_at, deleted
)
VALUES
(
    'demo-chunk-import-template-1',
    'default',
    'demo-knowledge-import-template',
    1,
    '数据导入基本流程',
    '# 数据导入基本流程

智路养护平台支持道路资产、病害、评定结果和指标结果数据导入。推荐流程为：下载模板、填写数据、上传文件、查看导入任务、检查错误日志。导入时必须携带租户请求头 X-Tenant-Id。路线数据应先于路段和评定单元导入，评定单元应先于病害和评定结果导入。',
    120,
    'LOCAL',
    NULL,
    '{}'::jsonb,
    now(),
    now(),
    false
),
(
    'demo-chunk-assessment-index-1',
    'default',
    'demo-knowledge-assessment-index',
    1,
    'MQI/PQI/PCI 指标说明',
    '# MQI/PQI/PCI 指标说明

MQI 表示公路技术状况指数，用于综合反映公路整体技术状况。PQI 表示路面技术状况指数，PCI 表示路面损坏状况指数。PCI 偏低通常说明路面裂缝、坑槽、沉陷、车辙等损坏类病害较多，应结合病害位置、严重程度和交通量制定养护建议。',
    130,
    'LOCAL',
    NULL,
    '{}'::jsonb,
    now(),
    now(),
    false
);
