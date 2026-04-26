package com.smartroad.srmp.agent.service.impl;

import com.smartroad.srmp.agent.dto.AgentAnalysisRequest;
import com.smartroad.srmp.agent.service.AgentDataQueryService;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

@Service
public class AgentDataQueryServiceImpl implements AgentDataQueryService {

    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    public Map<String, Object> routeSummary(AgentAnalysisRequest request) {
        MapSqlParameterSource params = baseParams(request);
        String sql =
                "select " +
                " count(distinct r.id) as route_count, " +
                " coalesce(sum(r.length_km),0) as route_length_km, " +
                " count(distinct d.id) as disease_count, " +
                " count(distinct a.id) as assessment_count, " +
                " round(avg(a.mqi),3) as avg_mqi, " +
                " round(avg(a.pqi),3) as avg_pqi, " +
                " round(avg(a.pci),3) as avg_pci " +
                " from road_route r " +
                " left join disease_record d on d.tenant_id=r.tenant_id and d.route_code=r.route_code and d.deleted=false " +
                " left join assessment_result a on a.tenant_id=r.tenant_id and a.route_code=r.route_code and a.deleted=false " +
                " where r.tenant_id=:tenantId and r.deleted=false " +
                optionalRoute("r") +
                optionalYear("a");
        return queryOne(sql, params);
    }

    @Override
    public Map<String, Object> diseaseSummary(AgentAnalysisRequest request) {
        MapSqlParameterSource params = baseParams(request);
        String sql =
                "select " +
                " count(*) as disease_count, " +
                " sum(case when severity='HEAVY' then 1 else 0 end) as heavy_count, " +
                " sum(case when severity='MEDIUM' then 1 else 0 end) as medium_count, " +
                " sum(case when severity='LIGHT' then 1 else 0 end) as light_count, " +
                " coalesce(sum(damage_area),0) as total_damage_area, " +
                " coalesce(sum(damage_length),0) as total_damage_length " +
                " from disease_record d " +
                " where d.tenant_id=:tenantId and d.deleted=false " +
                optionalRoute("d") +
                optionalDiseaseType() +
                optionalSeverity();
        return queryOne(sql, params);
    }

    @Override
    public Map<String, Object> assessmentSummary(AgentAnalysisRequest request) {
        MapSqlParameterSource params = baseParams(request);
        String sql =
                "select " +
                " count(*) as assessment_count, " +
                " round(avg(mqi),3) as avg_mqi, " +
                " round(avg(pqi),3) as avg_pqi, " +
                " round(avg(pci),3) as avg_pci, " +
                " sum(case when grade='EXCELLENT' then 1 else 0 end) as excellent_count, " +
                " sum(case when grade='GOOD' then 1 else 0 end) as good_count, " +
                " sum(case when grade='MEDIUM' then 1 else 0 end) as medium_count, " +
                " sum(case when grade='POOR' then 1 else 0 end) as poor_count, " +
                " sum(case when grade='BAD' then 1 else 0 end) as bad_count " +
                " from assessment_result a " +
                " where a.tenant_id=:tenantId and a.deleted=false " +
                optionalRoute("a") +
                optionalYear("a") +
                optionalGrade("a");
        return queryOne(sql, params);
    }

    @Override
    public List<Map<String, Object>> topDiseaseUnits(AgentAnalysisRequest request, int limit) {
        MapSqlParameterSource params = baseParams(request).addValue("limit", limit);
        String sql =
                "select route_code, unit_id, min(start_stake) as start_stake, max(end_stake) as end_stake, " +
                " count(*) as disease_count, " +
                " sum(case when severity='HEAVY' then 1 else 0 end) as heavy_count " +
                " from disease_record d " +
                " where d.tenant_id=:tenantId and d.deleted=false " +
                optionalRoute("d") +
                optionalDiseaseType() +
                optionalSeverity() +
                " group by route_code, unit_id " +
                " order by heavy_count desc, disease_count desc " +
                " limit :limit";
        return namedParameterJdbcTemplate.queryForList(sql, params);
    }

    @Override
    public List<Map<String, Object>> poorAssessmentResults(AgentAnalysisRequest request, int limit) {
        MapSqlParameterSource params = baseParams(request).addValue("limit", limit);
        String sql =
                "select id, route_code, unit_id, start_stake, end_stake, mqi, pqi, pci, grade " +
                " from assessment_result a " +
                " where a.tenant_id=:tenantId and a.deleted=false " +
                optionalRoute("a") +
                optionalYear("a") +
                " and (grade in ('POOR','BAD') or coalesce(mqi,100) < 70 or coalesce(pci,100) < 70) " +
                " order by coalesce(mqi, 999), coalesce(pci, 999) " +
                " limit :limit";
        return namedParameterJdbcTemplate.queryForList(sql, params);
    }

    @Override
    public List<String> findAssessmentIdsForMapQuery(String routeCode, Integer year, String grade, String indexCode) {
        AgentAnalysisRequest request = new AgentAnalysisRequest();
        request.setRouteCode(routeCode);
        request.setYear(year);
        request.setGrade(grade);
        MapSqlParameterSource params = baseParams(request);
        String sql =
                "select id from assessment_result a " +
                " where a.tenant_id=:tenantId and a.deleted=false " +
                optionalRoute("a") +
                optionalYear("a") +
                optionalGrade("a") +
                " order by start_stake limit 500";
        return namedParameterJdbcTemplate.query(sql, params, (rs, rowNum) -> rs.getString("id"));
    }

    @Override
    public List<String> findDiseaseIdsForMapQuery(String routeCode, String diseaseType, String severity) {
        AgentAnalysisRequest request = new AgentAnalysisRequest();
        request.setRouteCode(routeCode);
        request.setDiseaseType(diseaseType);
        request.setSeverity(severity);
        MapSqlParameterSource params = baseParams(request);
        String sql =
                "select id from disease_record d " +
                " where d.tenant_id=:tenantId and d.deleted=false " +
                optionalRoute("d") +
                optionalDiseaseType() +
                optionalSeverity() +
                " order by start_stake limit 500";
        return namedParameterJdbcTemplate.query(sql, params, (rs, rowNum) -> rs.getString("id"));
    }

    private Map<String, Object> queryOne(String sql, MapSqlParameterSource params) {
        List<Map<String, Object>> list = namedParameterJdbcTemplate.queryForList(sql, params);
        return list.isEmpty() ? new LinkedHashMap<>() : list.get(0);
    }

    private MapSqlParameterSource baseParams(AgentAnalysisRequest request) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("tenantId", TenantContextHolder.getTenantId());
        params.addValue("routeCode", request == null || request.getRouteCode() == null ? "" : request.getRouteCode());
        params.addValue("year", request == null || request.getYear() == null ? "" : String.valueOf(request.getYear()));
        params.addValue("grade", request == null || request.getGrade() == null ? "" : request.getGrade());
        params.addValue("diseaseType", request == null || request.getDiseaseType() == null ? "" : request.getDiseaseType());
        params.addValue("severity", request == null || request.getSeverity() == null ? "" : request.getSeverity());
        return params;
    }

    private String optionalRoute(String alias) {
        return " and (nullif(:routeCode, '') is not distinct from " + alias + ".route_code) ";
    }

    private String optionalYear(String alias) {
        return " and ('' = :year or cast(" + alias + ".year as text) = :year) ";
    }

    private String optionalGrade(String alias) {
        return " and (nullif(:grade, '') is not distinct from " + alias + ".grade) ";
    }

    private String optionalDiseaseType() {
        return " and (nullif(:diseaseType, '') is not distinct from d.disease_type) ";
    }

    private String optionalSeverity() {
        return " and (nullif(:severity, '') is not distinct from d.severity) ";
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
