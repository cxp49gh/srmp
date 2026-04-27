package com.smartroad.srmp.demo.service.impl;

import com.smartroad.srmp.demo.service.DemoDataService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;

@Service
public class DemoDataServiceImpl implements DemoDataService {

    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private static final List<String> DEMO_ROUTES = Arrays.asList(
            "G210", "G321", "S102", "S205", "S308", "X001", "X026", "Y015"
    );

    @Override
    public Map<String, Object> status(String tenantId, Integer year) {
        String tid = normalizeTenant(tenantId);
        int y = year == null ? 2026 : year;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tenantId", tid);
        data.put("year", y);
        data.put("routeCodes", DEMO_ROUTES);
        data.put("tables", tableCounts(tid, y));
        data.put("routes", routes(tid, y));

        Map<String, Object> health = new LinkedHashMap<>();
        Map<String, Object> counts = (Map<String, Object>) data.get("tables");
        health.put("hasRoutes", toLong(counts.get("road_route")) > 0);
        health.put("hasSections", toLong(counts.get("road_section")) > 0);
        health.put("hasUnits", toLong(counts.get("road_evaluation_unit")) >= 1000);
        health.put("hasAssessments", toLong(counts.get("assessment_result")) >= 1000);
        health.put("hasDiseases", toLong(counts.get("disease_record")) >= 3000);
        health.put("ready", Boolean.TRUE.equals(health.get("hasRoutes"))
                && Boolean.TRUE.equals(health.get("hasSections"))
                && Boolean.TRUE.equals(health.get("hasUnits"))
                && Boolean.TRUE.equals(health.get("hasAssessments"))
                && Boolean.TRUE.equals(health.get("hasDiseases")));
        data.put("health", health);

        return data;
    }

    @Override
    public Map<String, Object> dashboard(String tenantId, Integer year) {
        String tid = normalizeTenant(tenantId);
        int y = year == null ? 2026 : year;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tenantId", tid);
        data.put("year", y);
        data.put("summary", summary(tid, y));
        data.put("routeRanking", routes(tid, y));
        data.put("gradeDistribution", gradeDistribution(tid, y));
        data.put("diseaseTop", diseaseTop(tid));
        data.put("lowScoreUnits", lowScoreUnits(tid, y, 10));
        data.put("quickQuestions", quickQuestions());
        return data;
    }

    @Override
    public List<Map<String, Object>> routes(String tenantId, Integer year) {
        String tid = normalizeTenant(tenantId);
        int y = year == null ? 2026 : year;

        MapSqlParameterSource params = params(tid, y);
        return namedParameterJdbcTemplate.queryForList(
                "select r.route_code, max(r.route_name) as route_name, " +
                        "round(max(r.length_km)::numeric, 3) as length_km, " +
                        "count(distinct s.id) as section_count, " +
                        "count(distinct u.id) as unit_count, " +
                        "count(distinct a.id) as assessment_count, " +
                        "count(distinct d.id) as disease_count, " +
                        "round(avg(a.mqi)::numeric, 2) as avg_mqi, " +
                        "round(avg(a.pqi)::numeric, 2) as avg_pqi, " +
                        "round(avg(a.pci)::numeric, 2) as avg_pci, " +
                        "sum(case when a.grade in ('EXCELLENT','优') then 1 else 0 end) as excellent_count, " +
                        "sum(case when a.grade in ('GOOD','良') then 1 else 0 end) as good_count, " +
                        "sum(case when a.grade in ('MEDIUM','中') then 1 else 0 end) as medium_count, " +
                        "sum(case when a.grade in ('POOR','次') then 1 else 0 end) as poor_count, " +
                        "sum(case when a.grade in ('BAD','差') then 1 else 0 end) as bad_count " +
                        "from road_route r " +
                        "left join road_section s on s.tenant_id=r.tenant_id and s.route_code=r.route_code and coalesce(s.deleted,false)=false " +
                        "left join road_evaluation_unit u on u.tenant_id=r.tenant_id and u.route_code=r.route_code and coalesce(u.deleted,false)=false " +
                        "left join assessment_result a on a.tenant_id=r.tenant_id and a.route_code=r.route_code and a.year=:year and coalesce(a.deleted,false)=false " +
                        "left join disease_record d on d.tenant_id=r.tenant_id and d.route_code=r.route_code and coalesce(d.deleted,false)=false " +
                        "where r.tenant_id=:tenantId and coalesce(r.deleted,false)=false " +
                        "group by r.route_code " +
                        "order by r.route_code",
                params
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
        list.add(question("结合知识库生成 S205 养护建议方案", "S205", 2026));
        return list;
    }

    private Map<String, Object> tableCounts(String tenantId, int year) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("road_route", count("road_route", tenantId, null));
        data.put("road_section", count("road_section", tenantId, null));
        data.put("road_evaluation_unit", count("road_evaluation_unit", tenantId, null));
        data.put("assessment_result", count("assessment_result", tenantId, year));
        data.put("index_result", count("index_result", tenantId, year));
        data.put("disease_record", count("disease_record", tenantId, null));
        return data;
    }

    private Map<String, Object> summary(String tenantId, int year) {
        MapSqlParameterSource p = params(tenantId, year);
        List<Map<String, Object>> list = namedParameterJdbcTemplate.queryForList(
                "select " +
                        "(select count(*) from road_route where tenant_id=:tenantId and coalesce(deleted,false)=false) as route_count, " +
                        "(select round(coalesce(sum(length_km),0)::numeric, 3) from road_route where tenant_id=:tenantId and coalesce(deleted,false)=false) as total_length_km, " +
                        "(select count(*) from road_section where tenant_id=:tenantId and coalesce(deleted,false)=false) as section_count, " +
                        "(select count(*) from road_evaluation_unit where tenant_id=:tenantId and coalesce(deleted,false)=false) as unit_count, " +
                        "(select count(*) from assessment_result where tenant_id=:tenantId and year=:year and coalesce(deleted,false)=false) as assessment_count, " +
                        "(select count(*) from disease_record where tenant_id=:tenantId and coalesce(deleted,false)=false) as disease_count, " +
                        "(select round(avg(mqi)::numeric,2) from assessment_result where tenant_id=:tenantId and year=:year and coalesce(deleted,false)=false) as avg_mqi, " +
                        "(select round(avg(pqi)::numeric,2) from assessment_result where tenant_id=:tenantId and year=:year and coalesce(deleted,false)=false) as avg_pqi, " +
                        "(select round(avg(pci)::numeric,2) from assessment_result where tenant_id=:tenantId and year=:year and coalesce(deleted,false)=false) as avg_pci",
                p
        );
        return list.isEmpty() ? new LinkedHashMap<>() : list.get(0);
    }

    private List<Map<String, Object>> gradeDistribution(String tenantId, int year) {
        return namedParameterJdbcTemplate.queryForList(
                "select grade, count(*) as count " +
                        "from assessment_result " +
                        "where tenant_id=:tenantId and year=:year and coalesce(deleted,false)=false " +
                        "group by grade order by grade",
                params(tenantId, year)
        );
    }

    private List<Map<String, Object>> diseaseTop(String tenantId) {
        return namedParameterJdbcTemplate.queryForList(
                "select disease_type, max(disease_name) as disease_name, severity, count(*) as count, " +
                        "round(coalesce(sum(damage_area),0)::numeric,2) as total_area, " +
                        "round(coalesce(sum(damage_length),0)::numeric,2) as total_length " +
                        "from disease_record " +
                        "where tenant_id=:tenantId and coalesce(deleted,false)=false " +
                        "group by disease_type, severity " +
                        "order by count(*) desc limit 20",
                new MapSqlParameterSource().addValue("tenantId", tenantId)
        );
    }

    private List<Map<String, Object>> lowScoreUnits(String tenantId, int year, int limit) {
        return namedParameterJdbcTemplate.queryForList(
                "select route_code, object_id, unit_id, start_stake, end_stake, mqi, pqi, pci, grade " +
                        "from assessment_result " +
                        "where tenant_id=:tenantId and year=:year and coalesce(deleted,false)=false " +
                        "order by coalesce(mqi, 999), coalesce(pci, 999) limit :limit",
                params(tenantId, year).addValue("limit", limit)
        );
    }

    private Long count(String table, String tenantId, Integer year) {
        String sql = "select count(*) from " + table + " where tenant_id=:tenantId and coalesce(deleted,false)=false";
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("tenantId", tenantId);
        if (year != null && ("assessment_result".equals(table) || "index_result".equals(table))) {
            sql += " and year=:year";
            p.addValue("year", year);
        }
        return namedParameterJdbcTemplate.queryForObject(sql, p, Long.class);
    }

    private MapSqlParameterSource params(String tenantId, int year) {
        return new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("year", year);
    }

    private String normalizeTenant(String tenantId) {
        return tenantId == null || tenantId.trim().isEmpty() ? "default" : tenantId.trim();
    }

    private Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private Map<String, Object> question(String text, String routeCode, Integer year) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("text", text);
        q.put("routeCode", routeCode);
        q.put("year", year);
        return q;
    }
}