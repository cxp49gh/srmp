package com.smartroad.srmp.demo.service.impl;

import com.smartroad.srmp.demo.service.DemoDataService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

@Service
public class DemoDataServiceImpl implements DemoDataService {

    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    public Map<String, Object> status(String tenantId, Integer year) {
        int y = year == null ? 2026 : year;
        String requested = normalizeTenant(tenantId);
        String actual = resolveTenant(requested, y);

        Map<String, Object> tables = counts(actual, y);
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("hasRoutes", toLong(tables.get("road_route")) > 0);
        health.put("hasSections", toLong(tables.get("road_section")) > 0);
        health.put("hasUnits", toLong(tables.get("road_evaluation_unit")) >= 1000);
        health.put("hasAssessments", toLong(tables.get("assessment_result")) >= 1000);
        health.put("hasDiseases", toLong(tables.get("disease_record")) >= 3000);
        health.put("ready", Boolean.TRUE.equals(health.get("hasRoutes"))
                && Boolean.TRUE.equals(health.get("hasSections"))
                && Boolean.TRUE.equals(health.get("hasUnits"))
                && Boolean.TRUE.equals(health.get("hasAssessments"))
                && Boolean.TRUE.equals(health.get("hasDiseases")));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requestedTenantId", requested);
        data.put("tenantId", actual);
        data.put("year", y);
        data.put("availableTenants", availableTenants(y));
        data.put("tables", tables);
        data.put("health", health);
        data.put("routes", routes(actual, y));
        return data;
    }

    @Override
    public Map<String, Object> dashboard(String tenantId, Integer year) {
        int y = year == null ? 2026 : year;
        String requested = normalizeTenant(tenantId);
        String actual = resolveTenant(requested, y);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requestedTenantId", requested);
        data.put("tenantId", actual);
        data.put("year", y);
        data.put("availableTenants", availableTenants(y));
        data.put("summary", summary(actual, y));
        data.put("routeRanking", routes(actual, y));
        data.put("gradeDistribution", gradeDistribution(actual, y));
        data.put("diseaseTop", diseaseTop(actual));
        data.put("lowScoreUnits", lowScoreUnits(actual, y));
        data.put("quickQuestions", quickQuestions());
        return data;
    }

    @Override
    public List<Map<String, Object>> routes(String tenantId, Integer year) {
        int y = year == null ? 2026 : year;
        String tid = normalizeTenant(tenantId);
        MapSqlParameterSource p = params(tid, y);
        return namedParameterJdbcTemplate.queryForList(
                "select r.route_code, max(r.route_name) route_name, round(max(r.length_km)::numeric,3) length_km, " +
                        "(select count(*) from road_section s where s.tenant_id=r.tenant_id and s.route_code=r.route_code and coalesce(s.deleted,false)=false) section_count, " +
                        "(select count(*) from road_evaluation_unit u where u.tenant_id=r.tenant_id and u.route_code=r.route_code and coalesce(u.deleted,false)=false) unit_count, " +
                        "(select count(*) from assessment_result a where a.tenant_id=r.tenant_id and a.route_code=r.route_code and a.year=:year and coalesce(a.deleted,false)=false) assessment_count, " +
                        "(select count(*) from disease_record d where d.tenant_id=r.tenant_id and d.route_code=r.route_code and coalesce(d.deleted,false)=false) disease_count, " +
                        "round((select avg(a.mqi) from assessment_result a where a.tenant_id=r.tenant_id and a.route_code=r.route_code and a.year=:year and coalesce(a.deleted,false)=false)::numeric,2) avg_mqi, " +
                        "round((select avg(a.pqi) from assessment_result a where a.tenant_id=r.tenant_id and a.route_code=r.route_code and a.year=:year and coalesce(a.deleted,false)=false)::numeric,2) avg_pqi, " +
                        "round((select avg(a.pci) from assessment_result a where a.tenant_id=r.tenant_id and a.route_code=r.route_code and a.year=:year and coalesce(a.deleted,false)=false)::numeric,2) avg_pci " +
                        "from road_route r where r.tenant_id=:tenantId and coalesce(r.deleted,false)=false group by r.tenant_id,r.route_code order by r.route_code",
                p
        );
    }

    @Override
    public List<Map<String, Object>> quickQuestions() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(question("分析 G210 2026 年路况", "G210", 2026));
        list.add(question("生成 G210 2026 年技术状况评定报告草稿", "G210", 2026));
        list.add(question("对比 G210 和 S205 的 MQI、PCI 情况", "G210", 2026));
        list.add(question("统计 G210 主要病害类型和养护建议", "G210", 2026));
        list.add(question("找出 2026 年次差路段较多的路线", "G210", 2026));
        return list;
    }

    private String resolveTenant(String requested, int year) {
        if (count("assessment_result", requested, year) > 0 || count("road_route", requested, null) > 0) return requested;
        for (String c : Arrays.asList("default", "demo")) {
            if (!c.equals(requested) && (count("assessment_result", c, year) > 0 || count("road_route", c, null) > 0)) return c;
        }
        List<Map<String, Object>> list = availableTenants(year);
        return list.isEmpty() ? requested : String.valueOf(list.get(0).get("tenant_id"));
    }

    private List<Map<String, Object>> availableTenants(int year) {
        try {
            return namedParameterJdbcTemplate.queryForList(
                    "select tenant_id, sum(cnt) data_count from (" +
                            "select tenant_id,count(*) cnt from road_route group by tenant_id " +
                            "union all select tenant_id,count(*) cnt from assessment_result where year=:year group by tenant_id " +
                            "union all select tenant_id,count(*) cnt from disease_record group by tenant_id" +
                            ") x group by tenant_id order by sum(cnt) desc",
                    new MapSqlParameterSource().addValue("year", year));
        } catch (Exception e) {
            // available tenants query failed, return empty
            return Collections.emptyList();
        }
    }

    private Map<String, Object> counts(String tenantId, int year) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("road_route", count("road_route", tenantId, null));
        m.put("road_section", count("road_section", tenantId, null));
        m.put("road_evaluation_unit", count("road_evaluation_unit", tenantId, null));
        m.put("assessment_result", count("assessment_result", tenantId, year));
        m.put("index_result", count("index_result", tenantId, year));
        m.put("disease_record", count("disease_record", tenantId, null));
        return m;
    }

    private Map<String, Object> summary(String tenantId, int year) {
        MapSqlParameterSource p = params(tenantId, year);
        return namedParameterJdbcTemplate.queryForMap(
                "select " +
                        "(select count(*) from road_route where tenant_id=:tenantId and coalesce(deleted,false)=false) route_count, " +
                        "(select round(coalesce(sum(length_km),0)::numeric,3) from road_route where tenant_id=:tenantId and coalesce(deleted,false)=false) total_length_km, " +
                        "(select count(*) from road_section where tenant_id=:tenantId and coalesce(deleted,false)=false) section_count, " +
                        "(select count(*) from road_evaluation_unit where tenant_id=:tenantId and coalesce(deleted,false)=false) unit_count, " +
                        "(select count(*) from assessment_result where tenant_id=:tenantId and year=:year and coalesce(deleted,false)=false) assessment_count, " +
                        "(select count(*) from disease_record where tenant_id=:tenantId and coalesce(deleted,false)=false) disease_count, " +
                        "(select round(avg(mqi)::numeric,2) from assessment_result where tenant_id=:tenantId and year=:year and coalesce(deleted,false)=false) avg_mqi, " +
                        "(select round(avg(pqi)::numeric,2) from assessment_result where tenant_id=:tenantId and year=:year and coalesce(deleted,false)=false) avg_pqi, " +
                        "(select round(avg(pci)::numeric,2) from assessment_result where tenant_id=:tenantId and year=:year and coalesce(deleted,false)=false) avg_pci",
                p);
    }

    private List<Map<String, Object>> gradeDistribution(String tenantId, int year) {
        return namedParameterJdbcTemplate.queryForList("select grade,count(*) count from assessment_result where tenant_id=:tenantId and year=:year and coalesce(deleted,false)=false group by grade order by grade", params(tenantId, year));
    }

    private List<Map<String, Object>> diseaseTop(String tenantId) {
        return namedParameterJdbcTemplate.queryForList(
                "select disease_type,max(disease_name) disease_name,severity,count(*) count,round(coalesce(sum(damage_area),0)::numeric,2) total_area " +
                        "from disease_record where tenant_id=:tenantId and coalesce(deleted,false)=false group by disease_type,severity order by count(*) desc limit 20",
                new MapSqlParameterSource().addValue("tenantId", tenantId));
    }

    private List<Map<String, Object>> lowScoreUnits(String tenantId, int year) {
        return namedParameterJdbcTemplate.queryForList(
                "select route_code,object_id,unit_id,start_stake,end_stake,mqi,pqi,pci,grade from assessment_result " +
                        "where tenant_id=:tenantId and year=:year and coalesce(deleted,false)=false order by coalesce(mqi,999),coalesce(pci,999) limit 10",
                params(tenantId, year));
    }

    private Long count(String table, String tenantId, Integer year) {
        try {
            String sql = "select count(*) from " + table + " where tenant_id=:tenantId and coalesce(deleted,false)=false";
            MapSqlParameterSource p = new MapSqlParameterSource().addValue("tenantId", tenantId);
            if (year != null && ("assessment_result".equals(table) || "index_result".equals(table))) {
                sql += " and year=:year";
                p.addValue("year", year);
            }
            Long v = namedParameterJdbcTemplate.queryForObject(sql, p, Long.class);
            return v == null ? 0L : v;
        } catch (Exception e) {
            // count failed, return 0
            return 0L;
        }
    }

    private MapSqlParameterSource params(String tenantId, int year) {
        return new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("year", year);
    }

    private String normalizeTenant(String tenantId) {
        return tenantId == null || tenantId.trim().isEmpty() ? "default" : tenantId.trim();
    }

    private long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number) return ((Number) v).longValue();
        return Long.parseLong(String.valueOf(v));
    }

    private Map<String, Object> question(String text, String routeCode, Integer year) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("text", text);
        q.put("routeCode", routeCode);
        q.put("year", year);
        return q;
    }
}