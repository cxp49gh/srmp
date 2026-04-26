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
    deleted             BOOLEAN DEFAULT FALSE,
    CONSTRAINT uk_road_route_code UNIQUE(tenant_id, route_code)
);

CREATE INDEX IF NOT EXISTS idx_route_geom ON road_route USING GIST(geom);
CREATE INDEX IF NOT EXISTS idx_route_code ON road_route(tenant_id, route_code);

CREATE TABLE IF NOT EXISTS road_section (
    id                    VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    tenant_id             VARCHAR(64) NOT NULL,
    route_id              VARCHAR(64) NOT NULL,
    route_code            VARCHAR(50) NOT NULL,
    section_code          VARCHAR(100) NOT NULL,
    section_name          VARCHAR(200),
    direction             VARCHAR(20) DEFAULT 'BOTH',
    start_stake           NUMERIC(12,3) NOT NULL,
    end_stake             NUMERIC(12,3) NOT NULL,
    length_km             NUMERIC(12,3),
    pavement_type         VARCHAR(50),
    technical_grade       VARCHAR(50),
    geom                  GEOMETRY(LineString, 4326),
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted               BOOLEAN DEFAULT FALSE,
    CONSTRAINT uk_road_section_code UNIQUE(tenant_id, section_code)
);

CREATE INDEX IF NOT EXISTS idx_section_geom ON road_section USING GIST(geom);
CREATE INDEX IF NOT EXISTS idx_section_stake ON road_section(tenant_id, route_code, direction, start_stake, end_stake);

CREATE TABLE IF NOT EXISTS road_evaluation_unit (
    id                    VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::varchar,
    tenant_id             VARCHAR(64) NOT NULL,
    route_id              VARCHAR(64) NOT NULL,
    section_id            VARCHAR(64),
    route_code            VARCHAR(50) NOT NULL,
    unit_code             VARCHAR(100) NOT NULL,
    direction             VARCHAR(20) DEFAULT 'BOTH',
    lane_no               INTEGER,
    start_stake           NUMERIC(12,3) NOT NULL,
    end_stake             NUMERIC(12,3) NOT NULL,
    length_m              INTEGER DEFAULT 1000,
    geom                  GEOMETRY(LineString, 4326),
    center_point          GEOMETRY(Point, 4326),
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted               BOOLEAN DEFAULT FALSE,
    CONSTRAINT uk_eval_unit_code UNIQUE(tenant_id, unit_code)
);

CREATE INDEX IF NOT EXISTS idx_eval_unit_geom ON road_evaluation_unit USING GIST(geom);
CREATE INDEX IF NOT EXISTS idx_eval_unit_center ON road_evaluation_unit USING GIST(center_point);
CREATE INDEX IF NOT EXISTS idx_eval_unit_route_stake ON road_evaluation_unit(tenant_id, route_code, direction, start_stake, end_stake);


-- 阶段二道路资产补充字段，重复执行安全。
ALTER TABLE road_section ADD COLUMN IF NOT EXISTS lane_count INTEGER;
ALTER TABLE road_section ADD COLUMN IF NOT EXISTS road_width NUMERIC(8,2);
ALTER TABLE road_section ADD COLUMN IF NOT EXISTS traffic_volume_level VARCHAR(50);
ALTER TABLE road_section ADD COLUMN IF NOT EXISTS adcode VARCHAR(20);
ALTER TABLE road_section ADD COLUMN IF NOT EXISTS manage_org_id VARCHAR(64);
ALTER TABLE road_section ADD COLUMN IF NOT EXISTS remark TEXT;
ALTER TABLE road_section ADD COLUMN IF NOT EXISTS created_by VARCHAR(64);
ALTER TABLE road_section ADD COLUMN IF NOT EXISTS updated_by VARCHAR(64);

ALTER TABLE road_evaluation_unit ADD COLUMN IF NOT EXISTS pavement_type VARCHAR(50);
ALTER TABLE road_evaluation_unit ADD COLUMN IF NOT EXISTS technical_grade VARCHAR(50);
ALTER TABLE road_evaluation_unit ADD COLUMN IF NOT EXISTS road_width NUMERIC(8,2);
ALTER TABLE road_evaluation_unit ADD COLUMN IF NOT EXISTS adcode VARCHAR(20);
ALTER TABLE road_evaluation_unit ADD COLUMN IF NOT EXISTS manage_org_id VARCHAR(64);
ALTER TABLE road_evaluation_unit ADD COLUMN IF NOT EXISTS created_by VARCHAR(64);
ALTER TABLE road_evaluation_unit ADD COLUMN IF NOT EXISTS updated_by VARCHAR(64);

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
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted             BOOLEAN DEFAULT FALSE,
    CONSTRAINT uk_disease_type_code UNIQUE(tenant_id, disease_code)
);
CREATE INDEX IF NOT EXISTS idx_disease_type_category ON disease_type_dict(tenant_id, disease_category);

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
