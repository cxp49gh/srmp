-- G210 2026 演示数据核查脚本
-- 用法：
-- psql -h 127.0.0.1 -U srmp -d srmp -f src/main/resources/db/check_g210_2026_data.sql

\echo '1. assessment_result 汇总'
SELECT
  tenant_id,
  route_code,
  year,
  count(*) AS total,
  round(avg(mqi), 3) AS avg_mqi,
  round(avg(pqi), 3) AS avg_pqi,
  round(avg(pci), 3) AS avg_pci,
  sum(case when grade='EXCELLENT' then 1 else 0 end) AS excellent_count,
  sum(case when grade='GOOD' then 1 else 0 end) AS good_count,
  sum(case when grade='MEDIUM' then 1 else 0 end) AS medium_count,
  sum(case when grade='POOR' then 1 else 0 end) AS poor_count,
  sum(case when grade='BAD' then 1 else 0 end) AS bad_count
FROM assessment_result
WHERE tenant_id = 'default'
  AND route_code = 'G210'
  AND year = 2026
  AND deleted = false
GROUP BY tenant_id, route_code, year;

\echo '2. 低分 / 次差评定对象'
SELECT
  id,
  route_code,
  start_stake,
  end_stake,
  mqi,
  pqi,
  pci,
  grade
FROM assessment_result
WHERE tenant_id = 'default'
  AND route_code = 'G210'
  AND year = 2026
  AND deleted = false
  AND (
    grade IN ('POOR', 'BAD')
    OR coalesce(mqi, 100) < 70
    OR coalesce(pci, 100) < 70
  )
ORDER BY coalesce(mqi, 999), coalesce(pci, 999);

\echo '3. 病害汇总'
SELECT
  route_code,
  disease_type,
  severity,
  count(*) AS total
FROM disease_record
WHERE tenant_id = 'default'
  AND route_code = 'G210'
  AND deleted = false
GROUP BY route_code, disease_type, severity
ORDER BY route_code, disease_type, severity;
