-- Phase 51: customer-facing map AI uses project as the primary data scope.
-- Keep historical year columns for imported assessment data and admin diagnostics,
-- but remove year from customer solution template titles and required variables.

WITH target_templates AS (
    SELECT id, tenant_id
    FROM ai_solution_template
    WHERE tenant_id = 'default'
      AND template_code IN (
          'route_report_default',
          'map_object_disease_treatment_default',
          'map_object_low_score_treatment_default',
          'map_object_evaluation_unit_advice_default',
          'map_object_section_plan_default',
          'map_region_maintenance_advice_default'
      )
      AND deleted = false
),
updated_versions AS (
    UPDATE ai_solution_template_version v
    SET content = replace(v.content, '{{routeCode}} {{year}}', '{{routeCode}}'),
        content_hash = md5(replace(v.content, '{{routeCode}} {{year}}', '{{routeCode}}')),
        variables = CASE
            WHEN v.variables IS NULL THEN NULL
            ELSE COALESCE((
                SELECT jsonb_agg(item.value ORDER BY item.ordinality)
                FROM jsonb_array_elements(v.variables) WITH ORDINALITY AS item(value, ordinality)
                WHERE item.value <> to_jsonb('year'::text)
            ), '[]'::jsonb)
        END
    FROM target_templates t
    WHERE v.tenant_id = t.tenant_id
      AND v.template_id = t.id
    RETURNING v.template_id
)
UPDATE ai_solution_template t
SET updated_at = now()
WHERE t.id IN (SELECT template_id FROM updated_versions);
