-- Phase 52: disease review is a first-class map-object solution type.
-- Canonical template key: MAP_OBJECT + DISEASE + DISEASE_REVIEW.

BEGIN;

UPDATE ai_solution_template
SET is_default = false,
    updated_at = now()
WHERE tenant_id = 'default'
  AND deleted = false
  AND solution_type = 'DISEASE_REVIEW'
  AND origin_type = 'MAP_OBJECT'
  AND object_type = 'DISEASE'
  AND template_code <> 'map_object_disease_review_default'
  AND is_default = true;

INSERT INTO ai_solution_template(
    id,
    tenant_id,
    template_code,
    template_name,
    solution_type,
    source_type,
    source_id,
    category,
    origin_type,
    object_type,
    current_version,
    status,
    is_default,
    priority,
    created_at,
    updated_at,
    deleted
)
VALUES (
    'tpl-phase52-disease-review-default',
    'default',
    'map_object_disease_review_default',
    '地图病害复核意见默认模板',
    'DISEASE_REVIEW',
    'SYSTEM',
    NULL,
    'SOLUTION_TEMPLATE',
    'MAP_OBJECT',
    'DISEASE',
    'v1',
    'ENABLED',
    true,
    35,
    now(),
    now(),
    false
)
ON CONFLICT DO NOTHING;

UPDATE ai_solution_template
SET template_name = '地图病害复核意见默认模板',
    solution_type = 'DISEASE_REVIEW',
    origin_type = 'MAP_OBJECT',
    object_type = 'DISEASE',
    current_version = 'v1',
    status = 'ENABLED',
    is_default = true,
    priority = GREATEST(COALESCE(priority, 0), 35),
    updated_at = now(),
    deleted = false
WHERE tenant_id = 'default'
  AND template_code = 'map_object_disease_review_default';

INSERT INTO ai_solution_template_version(
    id,
    tenant_id,
    template_id,
    version,
    content,
    content_hash,
    variables,
    source_url,
    published_at,
    created_at
)
VALUES (
    'tplv-phase52-disease-review-default-v1',
    'default',
    'tpl-phase52-disease-review-default',
    'v1',
    $tpl$# {{routeCode}} 病害复核意见

## 一、病害对象
- 对象编号：{{objectId}}
- 病害类型：{{diseaseName}}
- 严重程度：{{severity}}
- 位置范围：{{stakeRange}}
- 工程量：{{quantity}}{{measureUnit}}

## 二、复核判断
{{problemAnalysis}}

## 三、现场复核重点
{{maintenanceSuggestion}}

## 四、业务证据
{{businessEvidenceSummary}}

## 五、风险提示
{{riskNotice}}
$tpl$,
    md5($tpl$# {{routeCode}} 病害复核意见

## 一、病害对象
- 对象编号：{{objectId}}
- 病害类型：{{diseaseName}}
- 严重程度：{{severity}}
- 位置范围：{{stakeRange}}
- 工程量：{{quantity}}{{measureUnit}}

## 二、复核判断
{{problemAnalysis}}

## 三、现场复核重点
{{maintenanceSuggestion}}

## 四、业务证据
{{businessEvidenceSummary}}

## 五、风险提示
{{riskNotice}}
$tpl$),
    '["routeCode","objectId","diseaseName","severity","stakeRange","quantity","measureUnit","problemAnalysis","maintenanceSuggestion","businessEvidenceSummary","riskNotice"]'::jsonb,
    'srmp://phase52/disease-review-template',
    now(),
    now()
)
ON CONFLICT DO NOTHING;

UPDATE ai_solution_template_version
SET content = $tpl$# {{routeCode}} 病害复核意见

## 一、病害对象
- 对象编号：{{objectId}}
- 病害类型：{{diseaseName}}
- 严重程度：{{severity}}
- 位置范围：{{stakeRange}}
- 工程量：{{quantity}}{{measureUnit}}

## 二、复核判断
{{problemAnalysis}}

## 三、现场复核重点
{{maintenanceSuggestion}}

## 四、业务证据
{{businessEvidenceSummary}}

## 五、风险提示
{{riskNotice}}
$tpl$,
    content_hash = md5($tpl$# {{routeCode}} 病害复核意见

## 一、病害对象
- 对象编号：{{objectId}}
- 病害类型：{{diseaseName}}
- 严重程度：{{severity}}
- 位置范围：{{stakeRange}}
- 工程量：{{quantity}}{{measureUnit}}

## 二、复核判断
{{problemAnalysis}}

## 三、现场复核重点
{{maintenanceSuggestion}}

## 四、业务证据
{{businessEvidenceSummary}}

## 五、风险提示
{{riskNotice}}
$tpl$),
    variables = '["routeCode","objectId","diseaseName","severity","stakeRange","quantity","measureUnit","problemAnalysis","maintenanceSuggestion","businessEvidenceSummary","riskNotice"]'::jsonb,
    source_url = 'srmp://phase52/disease-review-template',
    published_at = now()
WHERE tenant_id = 'default'
  AND template_id = 'tpl-phase52-disease-review-default'
  AND version = 'v1';

COMMIT;
