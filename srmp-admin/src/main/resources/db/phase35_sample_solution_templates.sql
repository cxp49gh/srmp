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
