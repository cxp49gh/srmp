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
