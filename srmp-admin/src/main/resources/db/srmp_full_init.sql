-- =============================================================================
-- SRMP 全量初始化 SQL（单一入口；历史分片已删除，请在此文件内维护 DDL/种子）
-- 大段调整建议保留 -- >>> BEGIN/END 分段注释便于审阅；小改动可直接修改对应段落
-- scripts/rebuild-srmp-full-init.ps1 仅打印说明，不再合并分片
-- =============================================================================


-- >>> BEGIN: srmp-admin/src/main/resources/db/schema.sql

CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS tenant (
    id              VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    tenant_code     VARCHAR(100) NOT NULL,
    tenant_name     VARCHAR(200) NOT NULL,
    tenant_type     VARCHAR(50),
    contact_name    VARCHAR(100),
    contact_phone   VARCHAR(50),
    status          VARCHAR(30) DEFAULT 'ENABLED',
    remark          TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN DEFAULT FALSE,
    CONSTRAINT uk_tenant_code UNIQUE(tenant_code)
);

CREATE TABLE IF NOT EXISTS sys_org (
    id              VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    tenant_id       VARCHAR(64) NOT NULL,
    org_code        VARCHAR(100) NOT NULL,
    org_name        VARCHAR(200) NOT NULL,
    parent_id       VARCHAR(64),
    org_type        VARCHAR(50),
    adcode          VARCHAR(20),
    sort_no         INTEGER DEFAULT 0,
    enabled         BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN DEFAULT FALSE,
    CONSTRAINT uk_sys_org_code UNIQUE(tenant_id, org_code)
);

CREATE TABLE IF NOT EXISTS sys_user (
    id              VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    tenant_id       VARCHAR(64) NOT NULL,
    org_id          VARCHAR(64),
    username        VARCHAR(100) NOT NULL,
    password        VARCHAR(255) NOT NULL,
    real_name       VARCHAR(100),
    phone           VARCHAR(50),
    email           VARCHAR(200),
    status          VARCHAR(30) DEFAULT 'ENABLED',
    last_login_at   TIMESTAMP,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN DEFAULT FALSE,
    CONSTRAINT uk_sys_user_username UNIQUE(tenant_id, username)
);

CREATE TABLE IF NOT EXISTS sys_dict_type (
    id              VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    tenant_id       VARCHAR(64) NOT NULL,
    dict_type       VARCHAR(100) NOT NULL,
    dict_name       VARCHAR(200) NOT NULL,
    description     TEXT,
    enabled         BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN DEFAULT FALSE,
    CONSTRAINT uk_dict_type UNIQUE(tenant_id, dict_type)
);

CREATE TABLE IF NOT EXISTS sys_dict_item (
    id              VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    tenant_id       VARCHAR(64) NOT NULL,
    dict_type       VARCHAR(100) NOT NULL,
    item_code       VARCHAR(100) NOT NULL,
    item_name       VARCHAR(200) NOT NULL,
    item_value      VARCHAR(200),
    sort_no         INTEGER DEFAULT 0,
    enabled         BOOLEAN DEFAULT TRUE,
    remark          TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN DEFAULT FALSE,
    CONSTRAINT uk_dict_item UNIQUE(tenant_id, dict_type, item_code)
);

CREATE TABLE IF NOT EXISTS road_route (
    id                  VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    tenant_id           VARCHAR(64) NOT NULL,
    route_code          VARCHAR(50) NOT NULL,
    route_name          VARCHAR(200) NOT NULL,
    route_type          VARCHAR(50) NOT NULL,
    admin_grade         VARCHAR(50),
    technical_grade     VARCHAR(50),
    start_stake         NUMERIC(12,3),
    end_stake           NUMERIC(12,3),
    length_km           NUMERIC(12,3),
    adcode              VARCHAR(20),
    manage_org_id       VARCHAR(64),
    geom                GEOMETRY(LineString, 4326),
    remark              TEXT,
    created_by          VARCHAR(64),
    updated_by          VARCHAR(64),
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted             BOOLEAN DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_route_geom ON road_route USING GIST(geom);
CREATE INDEX IF NOT EXISTS idx_route_code ON road_route(tenant_id, route_code);
CREATE UNIQUE INDEX IF NOT EXISTS uk_road_route_active_code ON road_route(tenant_id, route_code) WHERE COALESCE(deleted, false) = false;

-- 路段四级分表：线路 / 台账 / 公里 / 百米（与 .feature/导入路段数据-需求说明.md 一致）
CREATE TABLE IF NOT EXISTS road_section_line (
    id                    VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    tenant_id             VARCHAR(64) NOT NULL,
    route_id              VARCHAR(64),
    route_code            VARCHAR(50) NOT NULL,
    line_code             VARCHAR(100) NOT NULL,
    line_name             VARCHAR(200),
    direction             VARCHAR(20) DEFAULT 'BOTH',
    start_stake           NUMERIC(12,3) NOT NULL,
    end_stake             NUMERIC(12,3) NOT NULL,
    length_km             NUMERIC(12,3),
    pavement_type         VARCHAR(50),
    technical_grade       VARCHAR(50),
    geom                  GEOMETRY(LineString, 4326),
    project_id            VARCHAR(64),
    lane_count            INTEGER,
    road_width            NUMERIC(8,2),
    traffic_volume_level  VARCHAR(50),
    adcode                VARCHAR(20),
    manage_org_id         VARCHAR(64),
    remark                TEXT,
    created_by            VARCHAR(64),
    updated_by            VARCHAR(64),
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted               BOOLEAN DEFAULT FALSE,
    CONSTRAINT uk_road_section_line_code UNIQUE(tenant_id, line_code)
);

CREATE INDEX IF NOT EXISTS idx_section_line_geom ON road_section_line USING GIST(geom);
CREATE INDEX IF NOT EXISTS idx_section_line_stake ON road_section_line(tenant_id, route_code, direction, start_stake, end_stake);
CREATE INDEX IF NOT EXISTS idx_section_line_tenant_project ON road_section_line(tenant_id, project_id) WHERE deleted = false;

CREATE TABLE IF NOT EXISTS road_section_ledger (
    id                    VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    tenant_id             VARCHAR(64) NOT NULL,
    route_id              VARCHAR(64),
    line_id               VARCHAR(64),
    route_code            VARCHAR(50) NOT NULL,
    ledger_code           VARCHAR(100) NOT NULL,
    ledger_name           VARCHAR(200),
    direction             VARCHAR(20) DEFAULT 'BOTH',
    lane_no               INTEGER,
    start_stake           NUMERIC(12,3) NOT NULL,
    end_stake             NUMERIC(12,3) NOT NULL,
    length_m              INTEGER DEFAULT 1000,
    geom                  GEOMETRY(LineString, 4326),
    center_point          GEOMETRY(Point, 4326),
    pavement_type         VARCHAR(50),
    technical_grade       VARCHAR(50),
    road_width            NUMERIC(8,2),
    adcode                VARCHAR(20),
    manage_org_id         VARCHAR(64),
    project_id            VARCHAR(64),
    created_by            VARCHAR(64),
    updated_by            VARCHAR(64),
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted               BOOLEAN DEFAULT FALSE,
    CONSTRAINT uk_road_section_ledger_code UNIQUE(tenant_id, ledger_code)
);

CREATE INDEX IF NOT EXISTS idx_section_ledger_geom ON road_section_ledger USING GIST(geom);
CREATE INDEX IF NOT EXISTS idx_section_ledger_center ON road_section_ledger USING GIST(center_point);
CREATE INDEX IF NOT EXISTS idx_section_ledger_route_stake ON road_section_ledger(tenant_id, route_code, direction, start_stake, end_stake);
CREATE INDEX IF NOT EXISTS idx_section_ledger_tenant_project ON road_section_ledger(tenant_id, project_id) WHERE deleted = false;

CREATE TABLE IF NOT EXISTS road_section_km (
    id                    VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    tenant_id             VARCHAR(64) NOT NULL,
    route_id              VARCHAR(64),
    route_code            VARCHAR(50) NOT NULL,
    line_id               VARCHAR(64),
    km_code               VARCHAR(120) NOT NULL,
    direction             VARCHAR(20) DEFAULT 'BOTH',
    start_stake           NUMERIC(12,3) NOT NULL,
    end_stake             NUMERIC(12,3) NOT NULL,
    length_m              INTEGER,
    label                 VARCHAR(200),
    pavement_type         VARCHAR(50),
    technical_grade       VARCHAR(50),
    road_width            NUMERIC(8,2),
    geom                  GEOMETRY(LineString, 4326),
    project_id            VARCHAR(64),
    remark                TEXT,
    created_by            VARCHAR(64),
    updated_by            VARCHAR(64),
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted               BOOLEAN DEFAULT FALSE,
    CONSTRAINT uk_road_section_km_code UNIQUE(tenant_id, km_code)
);

CREATE INDEX IF NOT EXISTS idx_section_km_geom ON road_section_km USING GIST(geom);
CREATE INDEX IF NOT EXISTS idx_section_km_tenant_project ON road_section_km(tenant_id, project_id) WHERE deleted = false;

CREATE TABLE IF NOT EXISTS road_section_hm (
    id                    VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    tenant_id             VARCHAR(64) NOT NULL,
    route_id              VARCHAR(64),
    route_code            VARCHAR(50) NOT NULL,
    line_id               VARCHAR(64),
    km_id                 VARCHAR(64),
    hm_code               VARCHAR(120) NOT NULL,
    direction             VARCHAR(20) DEFAULT 'BOTH',
    start_stake           NUMERIC(12,3) NOT NULL,
    end_stake             NUMERIC(12,3) NOT NULL,
    length_m              INTEGER,
    label                 VARCHAR(200),
    geom                  GEOMETRY(LineString, 4326),
    project_id            VARCHAR(64),
    remark                TEXT,
    created_by            VARCHAR(64),
    updated_by            VARCHAR(64),
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted               BOOLEAN DEFAULT FALSE,
    CONSTRAINT uk_road_section_hm_code UNIQUE(tenant_id, hm_code)
);

CREATE INDEX IF NOT EXISTS idx_section_hm_geom ON road_section_hm USING GIST(geom);
CREATE INDEX IF NOT EXISTS idx_section_hm_tenant_project ON road_section_hm(tenant_id, project_id) WHERE deleted = false;

-- ===== Phase 2 Continued: disease and assessment GIS layers =====
CREATE TABLE IF NOT EXISTS disease_type_dict (
    id                  VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    tenant_id           VARCHAR(64) NOT NULL,
    disease_code        VARCHAR(100) NOT NULL,
    disease_name        VARCHAR(200) NOT NULL,
    disease_category    VARCHAR(50) NOT NULL,
    measure_unit        VARCHAR(20),
    related_index       VARCHAR(50),
    severity_enabled    BOOLEAN DEFAULT TRUE,
    enabled             BOOLEAN DEFAULT TRUE,
    sort_no             INTEGER DEFAULT 0,
    remark              TEXT,
    created_by          VARCHAR(64),
    updated_by          VARCHAR(64),
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted             BOOLEAN DEFAULT FALSE,
    CONSTRAINT uk_disease_type_code UNIQUE(tenant_id, disease_code)
);
CREATE INDEX IF NOT EXISTS idx_disease_type_category ON disease_type_dict(tenant_id, disease_category);
ALTER TABLE disease_type_dict ADD COLUMN IF NOT EXISTS created_by VARCHAR(64);
ALTER TABLE disease_type_dict ADD COLUMN IF NOT EXISTS updated_by VARCHAR(64);

CREATE TABLE IF NOT EXISTS disease_record (
    id                  VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    tenant_id           VARCHAR(64) NOT NULL,
    task_id             VARCHAR(64),
    route_id            VARCHAR(64),
    section_id          VARCHAR(64),
    unit_id             VARCHAR(64),
    route_code          VARCHAR(50) NOT NULL,
    direction           VARCHAR(20),
    lane_no             INTEGER,
    start_stake         NUMERIC(12,3),
    end_stake           NUMERIC(12,3),
    disease_category    VARCHAR(50) NOT NULL,
    disease_type        VARCHAR(100) NOT NULL,
    disease_name        VARCHAR(200),
    severity            VARCHAR(30),
    quantity            NUMERIC(14,4),
    measure_unit        VARCHAR(20),
    damage_area         NUMERIC(14,4),
    damage_length       NUMERIC(14,4),
    damage_width        NUMERIC(14,4),
    damage_depth        NUMERIC(14,4),
    source              VARCHAR(50),
    confidence          NUMERIC(5,4),
    geom                GEOMETRY(Geometry, 4326),
    status              VARCHAR(50) DEFAULT 'UNPROCESSED',
    verified            BOOLEAN DEFAULT FALSE,
    verified_by         VARCHAR(64),
    verified_at         TIMESTAMP,
    remark              TEXT,
    created_by          VARCHAR(64),
    updated_by          VARCHAR(64),
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted             BOOLEAN DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_disease_task ON disease_record(tenant_id, task_id);
CREATE INDEX IF NOT EXISTS idx_disease_unit ON disease_record(tenant_id, unit_id);
CREATE INDEX IF NOT EXISTS idx_disease_route_stake ON disease_record(tenant_id, route_code, direction, start_stake, end_stake);
CREATE INDEX IF NOT EXISTS idx_disease_type ON disease_record(tenant_id, disease_category, disease_type, severity);
CREATE INDEX IF NOT EXISTS idx_disease_status ON disease_record(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_disease_geom ON disease_record USING GIST(geom);

CREATE TABLE IF NOT EXISTS assessment_result (
    id                  VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    tenant_id           VARCHAR(64) NOT NULL,
    task_id             VARCHAR(64),
    object_type         VARCHAR(50) NOT NULL,
    object_id           VARCHAR(64) NOT NULL,
    route_id            VARCHAR(64),
    section_id          VARCHAR(64),
    unit_id             VARCHAR(64),
    route_code          VARCHAR(50),
    direction           VARCHAR(20),
    start_stake         NUMERIC(12,3),
    end_stake           NUMERIC(12,3),
    year                INTEGER NOT NULL,
    standard_code       VARCHAR(50) DEFAULT 'JTG_5210_2018',
    mqi                 NUMERIC(8,3),
    sci                 NUMERIC(8,3),
    pqi                 NUMERIC(8,3),
    bci                 NUMERIC(8,3),
    tci                 NUMERIC(8,3),
    pci                 NUMERIC(8,3),
    rqi                 NUMERIC(8,3),
    rdi                 NUMERIC(8,3),
    pbi                 NUMERIC(8,3),
    pwi                 NUMERIC(8,3),
    sri                 NUMERIC(8,3),
    pssi                NUMERIC(8,3),
    grade               VARCHAR(30),
    zero_reason         TEXT,
    assessed_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(64),
    updated_by          VARCHAR(64),
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted             BOOLEAN DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_assessment_task ON assessment_result(tenant_id, task_id);
CREATE INDEX IF NOT EXISTS idx_assessment_object ON assessment_result(tenant_id, object_type, object_id);
CREATE INDEX IF NOT EXISTS idx_assessment_unit ON assessment_result(tenant_id, unit_id);
CREATE INDEX IF NOT EXISTS idx_assessment_route_year ON assessment_result(tenant_id, route_code, year);
CREATE INDEX IF NOT EXISTS idx_assessment_grade ON assessment_result(tenant_id, grade);

CREATE TABLE IF NOT EXISTS index_result (
    id                  VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    tenant_id           VARCHAR(64) NOT NULL,
    task_id             VARCHAR(64),
    assessment_id       VARCHAR(64),
    unit_id             VARCHAR(64),
    route_id            VARCHAR(64),
    section_id          VARCHAR(64),
    route_code          VARCHAR(50) NOT NULL,
    direction           VARCHAR(20),
    start_stake         NUMERIC(12,3),
    end_stake           NUMERIC(12,3),
    year                INTEGER NOT NULL,
    index_code          VARCHAR(50) NOT NULL,
    index_name          VARCHAR(100),
    index_value         NUMERIC(8,3),
    grade               VARCHAR(30),
    raw_metrics         JSONB,
    calculation_version VARCHAR(50),
    calculated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted             BOOLEAN DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_index_task ON index_result(tenant_id, task_id);
CREATE INDEX IF NOT EXISTS idx_index_assessment ON index_result(tenant_id, assessment_id);
CREATE INDEX IF NOT EXISTS idx_index_unit ON index_result(tenant_id, unit_id);
CREATE INDEX IF NOT EXISTS idx_index_route_year ON index_result(tenant_id, route_code, year);
CREATE INDEX IF NOT EXISTS idx_index_code ON index_result(tenant_id, index_code);
CREATE INDEX IF NOT EXISTS idx_index_grade ON index_result(tenant_id, grade);
CREATE INDEX IF NOT EXISTS idx_index_raw_metrics_gin ON index_result USING GIN(raw_metrics);


-- ===== Phase 3: data import =====
CREATE TABLE IF NOT EXISTS data_import_task (
    id                  VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    tenant_id           VARCHAR(64) NOT NULL,
    import_code         VARCHAR(100) NOT NULL,
    import_name         VARCHAR(200) NOT NULL,
    data_type           VARCHAR(50) NOT NULL,
    file_id             VARCHAR(64),
    status              VARCHAR(50) DEFAULT 'CREATED',
    total_count         INTEGER DEFAULT 0,
    success_count       INTEGER DEFAULT 0,
    failed_count        INTEGER DEFAULT 0,
    field_mapping       JSONB,
    import_params       JSONB,
    started_at          TIMESTAMP,
    finished_at         TIMESTAMP,
    error_message       TEXT,
    created_by          VARCHAR(64),
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted             BOOLEAN DEFAULT FALSE,
    CONSTRAINT uk_import_code UNIQUE(tenant_id, import_code)
);
CREATE INDEX IF NOT EXISTS idx_import_task_tenant ON data_import_task(tenant_id);
CREATE INDEX IF NOT EXISTS idx_import_task_type ON data_import_task(tenant_id, data_type);
CREATE INDEX IF NOT EXISTS idx_import_task_status ON data_import_task(tenant_id, status);

CREATE TABLE IF NOT EXISTS data_import_error_log (
    id                  VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    tenant_id           VARCHAR(64) NOT NULL,
    import_task_id      VARCHAR(64) NOT NULL,
    row_no              INTEGER,
    field_name          VARCHAR(100),
    field_value         TEXT,
    error_type          VARCHAR(50),
    error_message       TEXT,
    raw_data            JSONB,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_import_error_task ON data_import_error_log(tenant_id, import_task_id);
CREATE INDEX IF NOT EXISTS idx_import_error_type ON data_import_error_log(tenant_id, error_type);

-- <<< END: srmp-admin/src/main/resources/db/schema.sql


-- >>> BEGIN: srmp-admin/src/main/resources/db/init_dict.sql

INSERT INTO tenant(id, tenant_code, tenant_name, tenant_type, status, created_at, updated_at, deleted)
VALUES ('default', 'default', '默认租户', 'PLATFORM', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)
ON CONFLICT (tenant_code) DO UPDATE
SET tenant_name = EXCLUDED.tenant_name,
    tenant_type = EXCLUDED.tenant_type,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP,
    deleted = FALSE;

INSERT INTO sys_dict_type(tenant_id, dict_type, dict_name, description)
VALUES
('default', 'route_type', '路线类型', '公路路线类型'),
('default', 'technical_grade', '技术等级', '公路技术等级'),
('default', 'pavement_type', '路面类型', '路面结构类型'),
('default', 'assessment_grade', '评定等级', '技术状况评定等级')
ON CONFLICT (tenant_id, dict_type) DO NOTHING;

INSERT INTO sys_dict_item(tenant_id, dict_type, item_code, item_name, sort_no)
VALUES ('default', 'route_type', 'OTHER', '外部路网导入', 99)
ON CONFLICT (tenant_id, dict_type, item_code) DO NOTHING;

INSERT INTO sys_dict_type(tenant_id, dict_type, dict_name, description)
VALUES
('default', 'disease_category', '病害大类', '病害所属业务大类'),
('default', 'disease_severity', '病害严重程度', '轻中重'),
('default', 'road_index_code', '技术状况指标', 'MQI/PQI/PCI等'),
('default', 'disease_status', '病害状态', '病害处理状态')
ON CONFLICT (tenant_id, dict_type) DO NOTHING;

INSERT INTO sys_dict_item(tenant_id, dict_type, item_code, item_name, sort_no)
VALUES
('default', 'disease_category', 'PAVEMENT', '路面', 1),
('default', 'disease_category', 'SUBGRADE', '路基', 2),
('default', 'disease_category', 'BRIDGE_TUNNEL_CULVERT', '桥隧涵', 3),
('default', 'disease_category', 'TRAFFIC_FACILITY', '沿线设施', 4),
('default', 'disease_severity', 'LIGHT', '轻度', 1),
('default', 'disease_severity', 'MEDIUM', '中度', 2),
('default', 'disease_severity', 'HEAVY', '重度', 3),
('default', 'road_index_code', 'MQI', '公路技术状况指数', 1),
('default', 'road_index_code', 'PQI', '路面技术状况指数', 2),
('default', 'road_index_code', 'PCI', '路面损坏状况指数', 3),
('default', 'road_index_code', 'RQI', '路面行驶质量指数', 4),
('default', 'road_index_code', 'RDI', '路面车辙深度指数', 5),
('default', 'road_index_code', 'SCI', '路基技术状况指数', 6),
('default', 'road_index_code', 'BCI', '桥隧构造物技术状况指数', 7),
('default', 'road_index_code', 'TCI', '沿线设施技术状况指数', 8)
ON CONFLICT (tenant_id, dict_type, item_code) DO NOTHING;

INSERT INTO disease_type_dict(tenant_id, disease_code, disease_name, disease_category, measure_unit, related_index, sort_no)
VALUES
('default', 'LONGITUDINAL_CRACK', '纵向裂缝', 'PAVEMENT', 'm', 'PCI', 1),
('default', 'TRANSVERSE_CRACK', '横向裂缝', 'PAVEMENT', 'm', 'PCI', 2),
('default', 'POTHOLE', '坑槽', 'PAVEMENT', 'm2', 'PCI', 3),
('default', 'RUTTING', '车辙', 'PAVEMENT', 'm', 'RDI', 4),
('default', 'SETTLEMENT', '沉陷', 'PAVEMENT', 'm2', 'PCI', 5)
ON CONFLICT (tenant_id, disease_code) DO NOTHING;

-- <<< END: srmp-admin/src/main/resources/db/init_dict.sql


-- >>> BEGIN: srmp-admin/src/main/resources/db/init_admin.sql

INSERT INTO sys_org(tenant_id, org_code, org_name, org_type)
VALUES ('default', 'ROOT', '默认组织', 'BUREAU')
ON CONFLICT (tenant_id, org_code) DO NOTHING;

INSERT INTO sys_user(tenant_id, username, password, real_name, status)
VALUES ('default', 'admin', 'admin123', '系统管理员', 'ENABLED')
ON CONFLICT (tenant_id, username) DO NOTHING;

-- <<< END: srmp-admin/src/main/resources/db/init_admin.sql


-- >>> BEGIN: srmp-admin/src/main/resources/db/phase17_outline_sync.sql

-- 阶段十七：Outline 同步入库
CREATE TABLE IF NOT EXISTS outline_sync_task (
    id              VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    collection_id   VARCHAR(200),
    collection_name VARCHAR(500),
    sync_mode       VARCHAR(30),
    status          VARCHAR(30),
    total_count     INTEGER DEFAULT 0,
    success_count   INTEGER DEFAULT 0,
    skip_count      INTEGER DEFAULT 0,
    fail_count      INTEGER DEFAULT 0,
    error_message   TEXT,
    started_at      TIMESTAMP,
    finished_at     TIMESTAMP,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_outline_sync_task_tenant
ON outline_sync_task(tenant_id, created_at DESC);

-- <<< END: srmp-admin/src/main/resources/db/phase17_outline_sync.sql


-- >>> BEGIN: srmp-admin/src/main/resources/db/phase36_map_ai_agent_vector_knowledge.sql

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
    embedding       VECTOR(1024),
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

-- <<< END: srmp-admin/src/main/resources/db/phase36_map_ai_agent_vector_knowledge.sql


-- >>> BEGIN: srmp-admin/src/main/resources/db/phase37_1_knowledge_reindex.sql

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
    embedding_dimensions = COALESCE(embedding_dimensions, 1024),
    embedded_at = COALESCE(embedded_at, updated_at)
WHERE embedding IS NOT NULL
  AND (embedding_provider IS NULL OR embedding_dimensions IS NULL OR embedded_at IS NULL);

-- <<< END: srmp-admin/src/main/resources/db/phase37_1_knowledge_reindex.sql


-- >>> BEGIN: srmp-admin/src/main/resources/db/phase39_remove_legacy_knowledge_tables.sql

-- Phase39: 移除旧知识库表，并清理 Outline 默认文档污染
-- 旧 knowledge_document / knowledge_chunk 已被 ai_knowledge_document / ai_knowledge_chunk 替代。
-- 注意：执行前请确认旧表数据已无需保留，或已完成迁移/备份。

-- 清理已经保存到方案任务引用表里的 Outline 产品默认文档。
-- 全量脚本中本段位于 phase21 / phase36 建表之前：全新库尚无这些表，需按表是否存在再执行 DELETE。
DO $phase39_outline_cleanup$
BEGIN
  IF to_regclass('public.ai_solution_source') IS NOT NULL THEN
    DELETE FROM ai_solution_source
    WHERE lower(coalesce(source_title, '')) IN (
        'our editor',
        'what is outline',
        'getting started',
        'integrations & api',
        'integrations and api'
    );
  END IF;
  IF to_regclass('public.ai_knowledge_chunk') IS NOT NULL
     AND to_regclass('public.ai_knowledge_document') IS NOT NULL THEN
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
  END IF;
  IF to_regclass('public.ai_knowledge_document') IS NOT NULL THEN
    DELETE FROM ai_knowledge_document
    WHERE source_type = 'OUTLINE'
      AND lower(coalesce(title, '')) IN (
          'our editor',
          'what is outline',
          'getting started',
          'integrations & api',
          'integrations and api'
      );
  END IF;
END
$phase39_outline_cleanup$;

DROP TABLE IF EXISTS knowledge_chunk;
DROP TABLE IF EXISTS knowledge_document;

-- <<< END: srmp-admin/src/main/resources/db/phase39_remove_legacy_knowledge_tables.sql


-- >>> BEGIN: srmp-admin/src/main/resources/db/phase39_1_outline_sync_document.sql

-- Phase39.1：Outline 同步明细表，记录每篇文档的同步结果
CREATE TABLE IF NOT EXISTS outline_sync_document (
    id              VARCHAR(32)  PRIMARY KEY,
    tenant_id       VARCHAR(32)  NOT NULL,
    task_id         VARCHAR(32)  NOT NULL,
    source_id       VARCHAR(64)  NOT NULL,
    title           VARCHAR(512),
    status          VARCHAR(32)   NOT NULL,  -- SUCCESS/SKIPPED/FAILED
    error_message   TEXT,
    document_id     VARCHAR(32),              -- ai_knowledge_document.id
    chunk_count     INTEGER      DEFAULT 0,
    content_hash    VARCHAR(64),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_outline_sync_doc_task ON outline_sync_document(tenant_id, task_id);
CREATE INDEX IF NOT EXISTS idx_outline_sync_doc_source ON outline_sync_document(tenant_id, source_id);
-- <<< END: srmp-admin/src/main/resources/db/phase39_1_outline_sync_document.sql


-- >>> BEGIN: srmp-admin/src/main/resources/db/phase39_2_2_outline_sync_contract_vector_closure.sql

-- Phase39.2.2：Outline 同步控制台契约收口与向量化闭环
ALTER TABLE outline_sync_task
ADD COLUMN IF NOT EXISTS dry_run BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS cleanup_missing BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS retry_task_id VARCHAR(64);

CREATE TABLE IF NOT EXISTS outline_sync_task_detail (
    id                    VARCHAR(64) PRIMARY KEY,
    tenant_id             VARCHAR(64) NOT NULL,
    task_id               VARCHAR(64) NOT NULL,
    outline_document_id   VARCHAR(128),
    outline_title         VARCHAR(500),
    collection_id         VARCHAR(128),
    action                VARCHAR(64),
    status                VARCHAR(32),
    skip_reason           VARCHAR(500),
    error_type            VARCHAR(200),
    error_message         TEXT,
    knowledge_document_id VARCHAR(64),
    chunk_count           INTEGER DEFAULT 0,
    cost_ms               BIGINT DEFAULT 0,
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE outline_sync_task_detail
ADD COLUMN IF NOT EXISTS outline_url VARCHAR(1000),
ADD COLUMN IF NOT EXISTS outline_updated_at VARCHAR(80),
ADD COLUMN IF NOT EXISTS content_chars INTEGER DEFAULT 0,
ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64),
ADD COLUMN IF NOT EXISTS old_content_hash VARCHAR(64),
ADD COLUMN IF NOT EXISTS detail_message TEXT,
ADD COLUMN IF NOT EXISTS document_status VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_outline_sync_task_detail_task
ON outline_sync_task_detail(tenant_id, task_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_outline_sync_task_detail_doc
ON outline_sync_task_detail(tenant_id, outline_document_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_outline_sync_task_detail_status_action
ON outline_sync_task_detail(tenant_id, task_id, status, action, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_outline_sync_task_detail_hash
ON outline_sync_task_detail(tenant_id, content_hash);

CREATE INDEX IF NOT EXISTS idx_ai_knowledge_document_outline_collection
ON ai_knowledge_document((metadata->>'outlineCollectionId'))
WHERE source_type='OUTLINE';

-- <<< END: srmp-admin/src/main/resources/db/phase39_2_2_outline_sync_contract_vector_closure.sql


-- >>> BEGIN: srmp-admin/src/main/resources/db/phase39_2_1_outline_sync_detail_display_fix.sql

-- Phase39.2.1：Outline 同步明细字段增强
--
-- 解决：
-- 1. 明细缺少 url、updatedAt、contentHash、contentChars、oldHash 等定位信息；
-- 2. 前端展示失败时只有 error_message，不知道是哪个文档、什么内容、是否内容变化；
-- 3. 后端返回 snake_case，前端字段混用导致展示错位。

ALTER TABLE outline_sync_task_detail
ADD COLUMN IF NOT EXISTS outline_url VARCHAR(1000),
ADD COLUMN IF NOT EXISTS outline_updated_at VARCHAR(80),
ADD COLUMN IF NOT EXISTS content_chars INTEGER DEFAULT 0,
ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64),
ADD COLUMN IF NOT EXISTS old_content_hash VARCHAR(64),
ADD COLUMN IF NOT EXISTS detail_message TEXT,
ADD COLUMN IF NOT EXISTS document_status VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_outline_sync_task_detail_status_action
ON outline_sync_task_detail(tenant_id, task_id, status, action, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_outline_sync_task_detail_hash
ON outline_sync_task_detail(tenant_id, content_hash);

-- <<< END: srmp-admin/src/main/resources/db/phase39_2_1_outline_sync_detail_display_fix.sql


-- >>> BEGIN: srmp-admin/src/main/resources/db/phase39_3_outline_auto_sync_webhook.sql

CREATE TABLE IF NOT EXISTS outline_auto_sync_config (
    id                    VARCHAR(64) PRIMARY KEY,
    tenant_id             VARCHAR(64) NOT NULL,
    name                  VARCHAR(200),
    enabled               BOOLEAN DEFAULT FALSE,
    collection_id          VARCHAR(128),
    sync_scope             VARCHAR(32) DEFAULT 'COLLECTION',
    document_ids           JSONB DEFAULT '[]'::jsonb,
    interval_minutes       INTEGER DEFAULT 60,
    force                 BOOLEAN DEFAULT FALSE,
    cleanup_missing        BOOLEAN DEFAULT FALSE,
    vectorize_after_sync   BOOLEAN DEFAULT TRUE,
    vector_force           BOOLEAN DEFAULT FALSE,
    vector_limit           INTEGER DEFAULT 500,
    webhook_enabled        BOOLEAN DEFAULT FALSE,
    webhook_secret         VARCHAR(200),
    last_run_at            TIMESTAMP,
    next_run_at            TIMESTAMP,
    last_task_id           VARCHAR(64),
    last_vector_status     VARCHAR(32),
    status                VARCHAR(32) DEFAULT 'IDLE',
    error_message          TEXT,
    created_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_outline_auto_sync_config_due
ON outline_auto_sync_config(tenant_id, enabled, next_run_at);

CREATE INDEX IF NOT EXISTS idx_outline_auto_sync_config_secret
ON outline_auto_sync_config(tenant_id, webhook_secret)
WHERE webhook_enabled = TRUE;

CREATE TABLE IF NOT EXISTS outline_auto_sync_run (
    id                    VARCHAR(64) PRIMARY KEY,
    tenant_id             VARCHAR(64) NOT NULL,
    config_id             VARCHAR(64),
    trigger_type           VARCHAR(32) NOT NULL,
    outline_event          VARCHAR(100),
    outline_document_id    VARCHAR(128),
    outline_collection_id  VARCHAR(128),
    sync_task_id           VARCHAR(64),
    vectorize_triggered    BOOLEAN DEFAULT FALSE,
    vectorize_status       VARCHAR(32),
    vectorize_message      TEXT,
    status                VARCHAR(32) DEFAULT 'RUNNING',
    error_message          TEXT,
    started_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    finished_at            TIMESTAMP,
    created_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_outline_auto_sync_run_config
ON outline_auto_sync_run(tenant_id, config_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_outline_auto_sync_run_doc
ON outline_auto_sync_run(tenant_id, outline_document_id, created_at DESC);

-- <<< END: srmp-admin/src/main/resources/db/phase39_3_outline_auto_sync_webhook.sql


-- >>> BEGIN: srmp-admin/src/main/resources/db/phase41_outline_vectorize_ops.sql

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

-- <<< END: srmp-admin/src/main/resources/db/phase41_outline_vectorize_ops.sql


-- >>> BEGIN: srmp-admin/src/main/resources/db/phase42_llm_timeout_outline_auto_sync_closure.sql

-- Phase42：AI 请求超时配置 + Outline 自动同步/Webhook 收口
-- 说明：
-- 1. AI/Outline HTTP 超时为应用配置项，无需数据库字段。
-- 2. 本脚本补齐自动同步运行锁与运维查询需要的索引，幂等可重复执行。

create index if not exists idx_outline_auto_sync_config_due
    on outline_auto_sync_config(enabled, next_run_at, status, updated_at);

create index if not exists idx_outline_auto_sync_config_webhook
    on outline_auto_sync_config(tenant_id, webhook_enabled, webhook_secret, collection_id, updated_at desc);

create index if not exists idx_outline_auto_sync_run_config_created
    on outline_auto_sync_run(tenant_id, config_id, created_at desc);

create index if not exists idx_outline_auto_sync_run_doc
    on outline_auto_sync_run(tenant_id, outline_document_id, created_at desc);

comment on table outline_auto_sync_config is 'Outline 自动同步配置：Phase42 增强运行锁、Webhook 精准同步与向量化闭环';
comment on table outline_auto_sync_run is 'Outline 自动同步运行记录：Phase42 记录 schedule/manual/webhook 触发与向量化状态';

-- <<< END: srmp-admin/src/main/resources/db/phase42_llm_timeout_outline_auto_sync_closure.sql


-- >>> BEGIN: srmp-admin/src/main/resources/db/phase20_ai_solution_template.sql

-- 阶段二十：AI 方案模板管理
-- 执行：
-- psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/phase20_ai_solution_template.sql

CREATE TABLE IF NOT EXISTS ai_solution_template (
    id              VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    template_code   VARCHAR(100) NOT NULL,
    template_name   VARCHAR(200) NOT NULL,
    solution_type   VARCHAR(100) NOT NULL,
    source_type     VARCHAR(50),
    source_id       VARCHAR(200),
    category        VARCHAR(100),
    current_version VARCHAR(50),
    status          VARCHAR(30),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS ai_solution_template_version (
    id              VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    template_id     VARCHAR(64) NOT NULL,
    version         VARCHAR(50) NOT NULL,
    content         TEXT NOT NULL,
    content_hash    VARCHAR(128),
    variables       JSONB,
    source_url      VARCHAR(1000),
    published_at    TIMESTAMP,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_solution_template_code
ON ai_solution_template(tenant_id, template_code)
WHERE deleted = false;

CREATE INDEX IF NOT EXISTS idx_ai_solution_template_type
ON ai_solution_template(tenant_id, solution_type, status);

CREATE INDEX IF NOT EXISTS idx_ai_solution_template_version
ON ai_solution_template_version(tenant_id, template_id, version);

INSERT INTO ai_solution_template(
    id, tenant_id, template_code, template_name, solution_type, source_type, source_id,
    category, current_version, status, created_at, updated_at, deleted
)
VALUES
(
    'tpl-road-assessment-report-default',
    'default',
    'road_assessment_report_default',
    '技术状况评定报告默认模板',
    'ROAD_ASSESSMENT_REPORT',
    'SYSTEM',
    NULL,
    'SOLUTION_TEMPLATE',
    'v1',
    'ENABLED',
    now(),
    now(),
    false
)
ON CONFLICT DO NOTHING;

INSERT INTO ai_solution_template_version(
    id, tenant_id, template_id, version, content, content_hash, variables, source_url, published_at, created_at
)
VALUES
(
    'tplv-road-assessment-report-default-v1',
    'default',
    'tpl-road-assessment-report-default',
    'v1',
    '# {{routeCode}} {{year}} 年技术状况评定报告草稿

## 一、路线概况
{{routeSummary}}

## 二、评定结果
{{assessmentSummary}}

## 三、主要病害
{{diseaseSummary}}

## 四、低分路段
{{lowScoreSections}}

## 五、问题分析
{{problemAnalysis}}

## 六、养护建议
{{maintenanceSuggestion}}

## 七、风险提示
{{riskNotice}}
',
    'phase20-default-road-assessment-template-v1',
    '["routeCode","year","routeSummary","assessmentSummary","diseaseSummary","lowScoreSections","problemAnalysis","maintenanceSuggestion","riskNotice"]'::jsonb,
    NULL,
    now(),
    now()
)
ON CONFLICT DO NOTHING;

-- <<< END: srmp-admin/src/main/resources/db/phase20_ai_solution_template.sql


-- >>> BEGIN: srmp-admin/src/main/resources/db/phase21_ai_solution_generate.sql

-- 阶段二十一：AI 方案生成
-- 执行：
-- psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/phase21_ai_solution_generate.sql

CREATE TABLE IF NOT EXISTS ai_solution_task (
    id               VARCHAR(64) PRIMARY KEY,
    tenant_id        VARCHAR(64) NOT NULL,
    solution_type    VARCHAR(100) NOT NULL,
    title            VARCHAR(300),
    route_code       VARCHAR(50),
    year             INTEGER,
    template_id      VARCHAR(64),
    template_version VARCHAR(50),
    status           VARCHAR(30),
    request_json     JSONB,
    result_content   TEXT,
    quality_result   JSONB,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_solution_source (
    id              VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    task_id         VARCHAR(64) NOT NULL,
    source_type     VARCHAR(50),
    source_title    VARCHAR(300),
    source_id       VARCHAR(200),
    source_url      VARCHAR(1000),
    content_excerpt TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_solution_task_tenant
ON ai_solution_task(tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_solution_task_route
ON ai_solution_task(tenant_id, route_code, year, solution_type);

CREATE INDEX IF NOT EXISTS idx_ai_solution_source_task
ON ai_solution_source(tenant_id, task_id);

-- <<< END: srmp-admin/src/main/resources/db/phase21_ai_solution_generate.sql


-- >>> BEGIN: srmp-admin/src/main/resources/db/phase22_ai_trace_monitor.sql

-- 阶段二十二：AI 调用链路监控与超时治理
CREATE TABLE IF NOT EXISTS ai_trace_log (
    id              VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    trace_id        VARCHAR(100) NOT NULL,
    request_type    VARCHAR(50),
    user_message    TEXT,
    mode            VARCHAR(50),
    status          VARCHAR(30),
    total_cost_ms   INTEGER,
    fallback        BOOLEAN DEFAULT FALSE,
    error_message   TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS ai_trace_step (
    id              VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    trace_id        VARCHAR(100) NOT NULL,
    step_name       VARCHAR(100),
    step_label      VARCHAR(100),
    status          VARCHAR(30),
    cost_ms         INTEGER,
    hit_count       INTEGER,
    error_message   TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ai_trace_log_tenant_time ON ai_trace_log(tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_trace_log_trace_id ON ai_trace_log(tenant_id, trace_id);
CREATE INDEX IF NOT EXISTS idx_ai_trace_log_status ON ai_trace_log(tenant_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_trace_step_trace_id ON ai_trace_step(tenant_id, trace_id, created_at ASC);

-- <<< END: srmp-admin/src/main/resources/db/phase22_ai_trace_monitor.sql


-- >>> BEGIN: srmp-admin/src/main/resources/db/phase33_ai_solution_draft_version.sql

-- Phase 33: AI solution draft versioning
-- psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/phase33_ai_solution_draft_version.sql

ALTER TABLE ai_solution_task
ADD COLUMN IF NOT EXISTS origin_type VARCHAR(50),
ADD COLUMN IF NOT EXISTS object_type VARCHAR(100),
ADD COLUMN IF NOT EXISTS object_id VARCHAR(100),
ADD COLUMN IF NOT EXISTS map_object JSONB,
ADD COLUMN IF NOT EXISTS object_summary JSONB,
ADD COLUMN IF NOT EXISTS draft_status VARCHAR(30),
ADD COLUMN IF NOT EXISTS current_version_no INTEGER DEFAULT 1;

CREATE TABLE IF NOT EXISTS ai_solution_task_version (
    id              VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    task_id         VARCHAR(64) NOT NULL,
    version_no      INTEGER NOT NULL,
    title           VARCHAR(300),
    result_content  TEXT,
    quality_result  JSONB,
    map_object      JSONB,
    object_summary  JSONB,
    source_snapshot JSONB,
    change_note     VARCHAR(500),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_solution_task_origin
ON ai_solution_task(tenant_id, origin_type, object_type, object_id);

CREATE INDEX IF NOT EXISTS idx_ai_solution_task_draft_status
ON ai_solution_task(tenant_id, draft_status, updated_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_solution_task_version_no
ON ai_solution_task_version(tenant_id, task_id, version_no);

-- <<< END: srmp-admin/src/main/resources/db/phase33_ai_solution_draft_version.sql


-- >>> BEGIN: srmp-admin/src/main/resources/db/phase38_ai_solution_task_closure.sql

-- Phase38: AI solution task closure.
ALTER TABLE ai_solution_task
ADD COLUMN IF NOT EXISTS ai_trace_id VARCHAR(100),
ADD COLUMN IF NOT EXISTS ai_answer TEXT,
ADD COLUMN IF NOT EXISTS ai_sources JSONB,
ADD COLUMN IF NOT EXISTS ai_tool_results JSONB,
ADD COLUMN IF NOT EXISTS ai_evidence JSONB,
ADD COLUMN IF NOT EXISTS ai_context JSONB,
ADD COLUMN IF NOT EXISTS generation_mode VARCHAR(50),
ADD COLUMN IF NOT EXISTS confirmed_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP;

ALTER TABLE ai_solution_task_version
ADD COLUMN IF NOT EXISTS ai_context JSONB;

CREATE TABLE IF NOT EXISTS ai_solution_task_status_log (
    id            VARCHAR(64) PRIMARY KEY,
    tenant_id     VARCHAR(64) NOT NULL,
    task_id       VARCHAR(64) NOT NULL,
    from_status   VARCHAR(30),
    to_status     VARCHAR(30) NOT NULL,
    action        VARCHAR(50),
    operator      VARCHAR(100),
    note          VARCHAR(500),
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_solution_task_ai_trace
ON ai_solution_task(tenant_id, ai_trace_id);

CREATE INDEX IF NOT EXISTS idx_ai_solution_task_status_log_task
ON ai_solution_task_status_log(tenant_id, task_id, created_at DESC);

-- <<< END: srmp-admin/src/main/resources/db/phase38_ai_solution_task_closure.sql


-- >>> BEGIN: srmp-admin/src/main/resources/db/phase38_3_cleanup_duplicate_system_fallback_sources.sql

-- Phase38.3：清理历史任务中重复的“系统兜底模板”引用来源。
-- 仅保留同一任务下第一条系统兜底模板来源。
WITH duplicated AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY tenant_id, task_id, coalesce(source_title, '系统兜底模板')
            ORDER BY created_at ASC, id ASC
        ) AS rn
    FROM ai_solution_source
    WHERE source_title LIKE '%兜底模板%'
       OR source_title LIKE '%系统兜底%'
       OR source_type = 'SYSTEM_TEMPLATE'
)
DELETE FROM ai_solution_source s
USING duplicated d
WHERE s.id = d.id
  AND d.rn > 1;

-- <<< END: srmp-admin/src/main/resources/db/phase38_3_cleanup_duplicate_system_fallback_sources.sql


-- >>> BEGIN: srmp-admin/src/main/resources/db/phase38_6_system_fallback_source_singleton.sql

-- Phase38.6：系统兜底模板来源单例化。
BEGIN;
WITH fallback_sources AS (
    SELECT id, tenant_id, task_id,
           ROW_NUMBER() OVER (PARTITION BY tenant_id, task_id ORDER BY created_at ASC NULLS LAST, id ASC) AS rn
    FROM ai_solution_source
    WHERE source_type = 'SYSTEM_TEMPLATE'
       OR source_title LIKE '%系统兜底模板%'
       OR source_title LIKE '%兜底模板%'
       OR source_title LIKE '%Fallback Template%'
)
DELETE FROM ai_solution_source s
USING fallback_sources f
WHERE s.id = f.id AND f.rn > 1;

UPDATE ai_solution_source
SET source_type = 'SYSTEM_TEMPLATE', source_title = '系统兜底模板'
WHERE source_type = 'SYSTEM_TEMPLATE'
   OR source_title LIKE '%系统兜底模板%'
   OR source_title LIKE '%兜底模板%'
   OR source_title LIKE '%Fallback Template%';

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_solution_source_one_fallback
ON ai_solution_source(tenant_id, task_id)
WHERE source_type = 'SYSTEM_TEMPLATE'
   OR source_title LIKE '%系统兜底模板%'
   OR source_title LIKE '%兜底模板%'
   OR source_title LIKE '%Fallback Template%';

CREATE OR REPLACE FUNCTION fn_ai_solution_source_normalize_fallback()
RETURNS trigger AS $$
BEGIN
    IF NEW.source_type = 'SYSTEM_TEMPLATE'
       OR NEW.source_title LIKE '%系统兜底模板%'
       OR NEW.source_title LIKE '%兜底模板%'
       OR NEW.source_title LIKE '%Fallback Template%' THEN
        NEW.source_type := 'SYSTEM_TEMPLATE';
        NEW.source_title := '系统兜底模板';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_ai_solution_source_normalize_fallback ON ai_solution_source;
CREATE TRIGGER trg_ai_solution_source_normalize_fallback
BEFORE INSERT OR UPDATE ON ai_solution_source
FOR EACH ROW
EXECUTE FUNCTION fn_ai_solution_source_normalize_fallback();
COMMIT;

-- <<< END: srmp-admin/src/main/resources/db/phase38_6_system_fallback_source_singleton.sql


-- >>> BEGIN: srmp-admin/src/main/resources/db/phase35_template_effectiveness.sql

-- Phase 35: AI solution template effectiveness and unified template metadata
-- psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/phase35_template_effectiveness.sql

ALTER TABLE ai_solution_template
ADD COLUMN IF NOT EXISTS object_type VARCHAR(100),
ADD COLUMN IF NOT EXISTS origin_type VARCHAR(100),
ADD COLUMN IF NOT EXISTS is_default BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS priority INTEGER DEFAULT 0;

ALTER TABLE ai_solution_template_version
ADD COLUMN IF NOT EXISTS change_note VARCHAR(500),
ADD COLUMN IF NOT EXISTS created_by VARCHAR(64);

ALTER TABLE ai_solution_task
ADD COLUMN IF NOT EXISTS template_meta JSONB;

ALTER TABLE ai_trace_step
ADD COLUMN IF NOT EXISTS step_data JSONB;

CREATE INDEX IF NOT EXISTS idx_ai_solution_template_match
ON ai_solution_template(tenant_id, origin_type, object_type, solution_type, status, priority DESC, updated_at DESC)
WHERE deleted = false;

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_solution_template_default
ON ai_solution_template(
    tenant_id,
    COALESCE(origin_type, ''),
    COALESCE(object_type, ''),
    solution_type
)
WHERE is_default = true AND deleted = false;

CREATE INDEX IF NOT EXISTS idx_ai_solution_task_template
ON ai_solution_task(tenant_id, template_id, template_version);

UPDATE ai_solution_template
SET origin_type = COALESCE(origin_type, 'ROUTE_REPORT'),
    object_type = COALESCE(object_type, 'ROAD_ROUTE'),
    priority = COALESCE(priority, 0),
    is_default = COALESCE(is_default, false)
WHERE solution_type = 'ROAD_ASSESSMENT_REPORT'
  AND deleted = false;

-- <<< END: srmp-admin/src/main/resources/db/phase35_template_effectiveness.sql


-- >>> BEGIN: srmp-admin/src/main/resources/db/phase35_sample_solution_templates.sql

-- Phase 35: sample AI solution templates
-- Run after phase20_ai_solution_template.sql and phase35_template_effectiveness.sql.
-- psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/phase35_sample_solution_templates.sql

WITH seed_templates(
    id,
    tenant_id,
    template_code,
    template_name,
    solution_type,
    origin_type,
    object_type,
    priority,
    content,
    variables
) AS (
    VALUES
    (
        'tpl-road-assessment-report-default',
        'default',
        'road_assessment_report_default',
        '路线技术状况评定报告默认模板',
        'ROAD_ASSESSMENT_REPORT',
        'ROUTE_REPORT',
        'ROAD_ROUTE',
        30,
        $tpl$# {{routeCode}} {{year}} 年技术状况评定报告草稿

## 一、路线概况
{{routeSummary}}

## 二、评定结果
{{assessmentSummary}}

## 三、主要病害
{{diseaseSummary}}

## 四、低分路段
{{lowScoreSections}}

## 五、问题分析
{{problemAnalysis}}

## 六、养护建议
{{maintenanceSuggestion}}

## 七、风险提示
{{riskNotice}}
$tpl$,
        '["routeCode","year","routeSummary","assessmentSummary","diseaseSummary","lowScoreSections","problemAnalysis","maintenanceSuggestion","riskNotice"]'::jsonb
    ),
    (
        'tpl-phase35-disease-treatment-default',
        'default',
        'map_object_disease_treatment_default',
        '地图病害治理方案默认模板',
        'DISEASE_TREATMENT_PLAN',
        'MAP_OBJECT',
        'DISEASE',
        30,
        $tpl$# {{routeCode}} {{year}} 病害治理方案

## 一、病害概况
- 对象编号：{{objectId}}
- 病害类型：{{diseaseName}}
- 严重程度：{{severity}}
- 位置范围：{{stakeRange}}
- 工程量：{{quantity}}{{measureUnit}}

## 二、处治建议
{{treatmentAdvice}}

## 三、养护组织建议
{{maintenanceSuggestion}}

## 四、风险提示
{{riskNotice}}
$tpl$,
        '["routeCode","year","objectId","diseaseName","severity","stakeRange","quantity","measureUnit","treatmentAdvice","maintenanceSuggestion","riskNotice"]'::jsonb
    ),
    (
        'tpl-phase35-assessment-low-score-default',
        'default',
        'map_object_assessment_low_score_default',
        '低分评定单元分析默认模板',
        'LOW_SCORE_SECTION_ANALYSIS',
        'MAP_OBJECT',
        'ASSESSMENT_RESULT',
        30,
        $tpl$# {{routeCode}} {{year}} 低分评定单元分析

## 一、单元概况
- 单元编号：{{unitCode}}
- MQI：{{mqi}}
- PQI：{{pqi}}
- PCI：{{pci}}
- 等级：{{grade}}

## 二、问题分析
{{problemAnalysis}}

## 三、养护建议
{{maintenanceSuggestion}}

## 四、风险提示
{{riskNotice}}
$tpl$,
        '["routeCode","year","unitCode","mqi","pqi","pci","grade","problemAnalysis","maintenanceSuggestion","riskNotice"]'::jsonb
    ),
    (
        'tpl-phase35-road-section-maintenance-default',
        'default',
        'map_object_road_section_maintenance_default',
        '路段养护建议默认模板',
        'MAINTENANCE_SUGGESTION',
        'MAP_OBJECT',
        'ROAD_SECTION',
        20,
        $tpl$# {{routeCode}} {{year}} 路段养护建议

## 一、路段概况
{{routeSummary}}

## 二、技术状况
{{assessmentSummary}}

## 三、主要病害
{{diseaseSummary}}

## 四、问题分析
{{problemAnalysis}}

## 五、养护建议
{{maintenanceSuggestion}}

## 六、风险提示
{{riskNotice}}
$tpl$,
        '["routeCode","year","routeSummary","assessmentSummary","diseaseSummary","problemAnalysis","maintenanceSuggestion","riskNotice"]'::jsonb
    ),
    (
        'tpl-phase35-map-region-maintenance-default',
        'default',
        'map_region_maintenance_advice_default',
        '框选区域养护建议默认模板',
        'REGION_MAINTENANCE_SUGGESTION',
        'MAP_REGION',
        'MAP_REGION',
        30,
        $tpl$# {{routeCode}} {{year}} 框选区域养护建议

## 一、区域统计摘要
- 区域面积：{{areaKm2}} km2
- 覆盖路线：{{routeCount}} 条
- 覆盖路段：{{sectionCount}} 段
- 评定单元：{{unitCount}} 个
- 病害数量：{{diseaseCount}} 处，其中重度 {{heavyDiseaseCount}} 处、中度 {{mediumDiseaseCount}} 处
- 平均 MQI：{{avgMqi}}，平均 PQI：{{avgPqi}}，平均 PCI：{{avgPci}}

## 二、热点识别
{{hotspotSummary}}

## 三、区域综合判断
{{regionSummary}}

## 四、养护建议
{{maintenanceSuggestion}}

## 五、风险提示
{{riskNotice}}
$tpl$,
        '["routeCode","year","areaKm2","routeCount","sectionCount","unitCount","diseaseCount","heavyDiseaseCount","mediumDiseaseCount","avgMqi","avgPqi","avgPci","hotspotSummary","regionSummary","maintenanceSuggestion","riskNotice"]'::jsonb
    )
),
seed_with_default AS (
    SELECT
        s.*,
        NOT EXISTS (
            SELECT 1
            FROM ai_solution_template t
            WHERE t.tenant_id = s.tenant_id
              AND COALESCE(t.origin_type, '') = s.origin_type
              AND COALESCE(t.object_type, '') = s.object_type
              AND t.solution_type = s.solution_type
              AND t.is_default = true
              AND t.deleted = false
              AND t.id <> s.id
        ) AS seed_default
    FROM seed_templates s
)
INSERT INTO ai_solution_template(
    id,
    tenant_id,
    template_code,
    template_name,
    solution_type,
    source_type,
    source_id,
    category,
    current_version,
    status,
    origin_type,
    object_type,
    is_default,
    priority,
    created_at,
    updated_at,
    deleted
)
SELECT
    id,
    tenant_id,
    template_code,
    template_name,
    solution_type,
    'SYSTEM',
    'phase35_sample_solution_templates',
    'SOLUTION_TEMPLATE',
    'v1',
    'ENABLED',
    origin_type,
    object_type,
    seed_default,
    priority,
    now(),
    now(),
    false
FROM seed_with_default
ON CONFLICT (id) DO UPDATE
SET template_code = EXCLUDED.template_code,
    template_name = EXCLUDED.template_name,
    solution_type = EXCLUDED.solution_type,
    source_type = EXCLUDED.source_type,
    source_id = EXCLUDED.source_id,
    category = EXCLUDED.category,
    current_version = EXCLUDED.current_version,
    status = EXCLUDED.status,
    origin_type = EXCLUDED.origin_type,
    object_type = EXCLUDED.object_type,
    is_default = EXCLUDED.is_default,
    priority = EXCLUDED.priority,
    updated_at = now(),
    deleted = false;

WITH seed_versions(
    id,
    tenant_id,
    template_id,
    version,
    content,
    content_hash,
    variables
) AS (
    VALUES
    (
        'tplv-road-assessment-report-default-v1',
        'default',
        'tpl-road-assessment-report-default',
        'v1',
        $tpl$# {{routeCode}} {{year}} 年技术状况评定报告草稿

## 一、路线概况
{{routeSummary}}

## 二、评定结果
{{assessmentSummary}}

## 三、主要病害
{{diseaseSummary}}

## 四、低分路段
{{lowScoreSections}}

## 五、问题分析
{{problemAnalysis}}

## 六、养护建议
{{maintenanceSuggestion}}

## 七、风险提示
{{riskNotice}}
$tpl$,
        'phase35-sample-road-assessment-template-v1',
        '["routeCode","year","routeSummary","assessmentSummary","diseaseSummary","lowScoreSections","problemAnalysis","maintenanceSuggestion","riskNotice"]'::jsonb
    ),
    (
        'tplv-phase35-disease-treatment-default-v1',
        'default',
        'tpl-phase35-disease-treatment-default',
        'v1',
        $tpl$# {{routeCode}} {{year}} 病害治理方案

## 一、病害概况
- 对象编号：{{objectId}}
- 病害类型：{{diseaseName}}
- 严重程度：{{severity}}
- 位置范围：{{stakeRange}}
- 工程量：{{quantity}}{{measureUnit}}

## 二、处治建议
{{treatmentAdvice}}

## 三、养护组织建议
{{maintenanceSuggestion}}

## 四、风险提示
{{riskNotice}}
$tpl$,
        'phase35-sample-disease-treatment-template-v1',
        '["routeCode","year","objectId","diseaseName","severity","stakeRange","quantity","measureUnit","treatmentAdvice","maintenanceSuggestion","riskNotice"]'::jsonb
    ),
    (
        'tplv-phase35-assessment-low-score-default-v1',
        'default',
        'tpl-phase35-assessment-low-score-default',
        'v1',
        $tpl$# {{routeCode}} {{year}} 低分评定单元分析

## 一、单元概况
- 单元编号：{{unitCode}}
- MQI：{{mqi}}
- PQI：{{pqi}}
- PCI：{{pci}}
- 等级：{{grade}}

## 二、问题分析
{{problemAnalysis}}

## 三、养护建议
{{maintenanceSuggestion}}

## 四、风险提示
{{riskNotice}}
$tpl$,
        'phase35-sample-low-score-template-v1',
        '["routeCode","year","unitCode","mqi","pqi","pci","grade","problemAnalysis","maintenanceSuggestion","riskNotice"]'::jsonb
    ),
    (
        'tplv-phase35-road-section-maintenance-default-v1',
        'default',
        'tpl-phase35-road-section-maintenance-default',
        'v1',
        $tpl$# {{routeCode}} {{year}} 路段养护建议

## 一、路段概况
{{routeSummary}}

## 二、技术状况
{{assessmentSummary}}

## 三、主要病害
{{diseaseSummary}}

## 四、问题分析
{{problemAnalysis}}

## 五、养护建议
{{maintenanceSuggestion}}

## 六、风险提示
{{riskNotice}}
$tpl$,
        'phase35-sample-road-section-maintenance-template-v1',
        '["routeCode","year","routeSummary","assessmentSummary","diseaseSummary","problemAnalysis","maintenanceSuggestion","riskNotice"]'::jsonb
    ),
    (
        'tplv-phase35-map-region-maintenance-default-v1',
        'default',
        'tpl-phase35-map-region-maintenance-default',
        'v1',
        $tpl$# {{routeCode}} {{year}} 框选区域养护建议

## 一、区域统计摘要
- 区域面积：{{areaKm2}} km2
- 覆盖路线：{{routeCount}} 条
- 覆盖路段：{{sectionCount}} 段
- 评定单元：{{unitCount}} 个
- 病害数量：{{diseaseCount}} 处，其中重度 {{heavyDiseaseCount}} 处、中度 {{mediumDiseaseCount}} 处
- 平均 MQI：{{avgMqi}}，平均 PQI：{{avgPqi}}，平均 PCI：{{avgPci}}

## 二、热点识别
{{hotspotSummary}}

## 三、区域综合判断
{{regionSummary}}

## 四、养护建议
{{maintenanceSuggestion}}

## 五、风险提示
{{riskNotice}}
$tpl$,
        'phase35-sample-map-region-maintenance-template-v1',
        '["routeCode","year","areaKm2","routeCount","sectionCount","unitCount","diseaseCount","heavyDiseaseCount","mediumDiseaseCount","avgMqi","avgPqi","avgPci","hotspotSummary","regionSummary","maintenanceSuggestion","riskNotice"]'::jsonb
    )
)
INSERT INTO ai_solution_template_version(
    id,
    tenant_id,
    template_id,
    version,
    content,
    content_hash,
    variables,
    source_url,
    change_note,
    created_by,
    published_at,
    created_at
)
SELECT
    id,
    tenant_id,
    template_id,
    version,
    content,
    content_hash,
    variables,
    'srmp://phase35/sample-solution-templates',
    'Phase35 sample template initialization',
    'system',
    now(),
    now()
FROM seed_versions
ON CONFLICT (id) DO UPDATE
SET content = EXCLUDED.content,
    content_hash = EXCLUDED.content_hash,
    variables = EXCLUDED.variables,
    source_url = EXCLUDED.source_url,
    change_note = EXCLUDED.change_note,
    created_by = EXCLUDED.created_by,
    published_at = now();

-- <<< END: srmp-admin/src/main/resources/db/phase35_sample_solution_templates.sql


-- >>> BEGIN: srmp-admin/src/main/resources/db/phase36_one_click_demo_seed.sql
-- 原 G210 一键演示种子（路线/路段/台账/评定/病害/index 等）已移除；默认租户见 init_dict 段。
-- <<< END: srmp-admin/src/main/resources/db/phase36_one_click_demo_seed.sql

-- >>> BEGIN: data management（项目主档 + 导入流水 + 业务表 project_id 冗余）
CREATE TABLE IF NOT EXISTS data_mgmt_project (
    id              VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    tenant_id       VARCHAR(64) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    remark          TEXT,
    created_by      VARCHAR(64),
    updated_by      VARCHAR(64),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_data_mgmt_project_tenant_name ON data_mgmt_project(tenant_id, name);

CREATE TABLE IF NOT EXISTS data_import_record (
    id              VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    tenant_id       VARCHAR(64) NOT NULL,
    project_id      VARCHAR(64) NOT NULL,
    import_type     VARCHAR(32) NOT NULL,
    file_name       VARCHAR(500),
    started_at      TIMESTAMP NOT NULL,
    finished_at     TIMESTAMP,
    duration_ms     BIGINT,
    status          VARCHAR(20) NOT NULL,
    message         TEXT,
    result_summary  TEXT,
    created_by      VARCHAR(64),
    updated_by      VARCHAR(64),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_data_import_record_project_time ON data_import_record(tenant_id, project_id, started_at DESC);

ALTER TABLE data_mgmt_project ADD COLUMN IF NOT EXISTS archived BOOLEAN DEFAULT FALSE;
ALTER TABLE data_mgmt_project ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP;
ALTER TABLE data_mgmt_project ADD COLUMN IF NOT EXISTS archived_by VARCHAR(64);

CREATE TABLE IF NOT EXISTS data_mgmt_audit_log (
    id              VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    project_id      VARCHAR(64),
    project_name    VARCHAR(200),
    operation_type  VARCHAR(64) NOT NULL,
    operator        VARCHAR(64),
    operated_at     TIMESTAMP NOT NULL,
    result          VARCHAR(32) NOT NULL,
    reason          TEXT,
    snapshot_before TEXT,
    snapshot_after  TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_data_mgmt_audit_tenant_time ON data_mgmt_audit_log(tenant_id, operated_at DESC);

ALTER TABLE road_route ADD COLUMN IF NOT EXISTS project_id VARCHAR(64);
ALTER TABLE disease_record ADD COLUMN IF NOT EXISTS project_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_road_route_tenant_project ON road_route(tenant_id, project_id) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_disease_record_tenant_project ON disease_record(tenant_id, project_id) WHERE deleted = false;
-- <<< END: data management
