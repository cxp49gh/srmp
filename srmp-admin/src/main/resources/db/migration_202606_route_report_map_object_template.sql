-- Phase 52: route report is a map-object capability.
-- Canonical template key: MAP_OBJECT + ROAD_ROUTE + ROUTE_REPORT.
-- Do not add alternate ROUTE_REPORT + ROAD_ROUTE template matching.

BEGIN;

UPDATE ai_solution_template
SET is_default = false,
    updated_at = now()
WHERE tenant_id = 'default'
  AND deleted = false
  AND solution_type = 'ROUTE_REPORT'
  AND object_type = 'ROAD_ROUTE'
  AND origin_type = 'MAP_OBJECT'
  AND template_code <> 'route_report_default'
  AND is_default = true;

UPDATE ai_solution_template
SET origin_type = 'MAP_OBJECT',
    object_type = 'ROAD_ROUTE',
    is_default = true,
    priority = GREATEST(COALESCE(priority, 0), 30),
    updated_at = now()
WHERE tenant_id = 'default'
  AND template_code = 'route_report_default'
  AND solution_type = 'ROUTE_REPORT'
  AND deleted = false;

COMMIT;
