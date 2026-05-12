package com.smartroad.srmp.dashboard.service.impl;

import com.smartroad.srmp.dashboard.service.DashboardService;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardServiceImpl implements DashboardService {

    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    public Map<String, Object> overview(String routeCode, Integer year) {
        MapSqlParameterSource params = params(routeCode, year);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("routeCount", value("select count(*) from road_route where tenant_id=:tenantId and deleted=false" + routeFilter(), params));
        result.put("totalLengthKm", value("select coalesce(sum(length_km),0) from road_route where tenant_id=:tenantId and deleted=false" + routeFilter(), params));
        result.put("sectionCount", value("select count(*) from road_section_line where tenant_id=:tenantId and deleted=false" + routeFilter(), params));
        result.put("unitCount", value("select count(*) from road_section_ledger where tenant_id=:tenantId and deleted=false" + routeFilter(), params));
        result.put("disease", diseaseSummary(routeCode));
        result.put("assessment", assessmentSummary(routeCode, year));
        return result;
    }

    @Override
    public Map<String, Object> diseaseSummary(String routeCode) {
        MapSqlParameterSource params = params(routeCode, null);
        return one("select count(*) as disease_count, " +
                "sum(case when severity='HEAVY' then 1 else 0 end) as heavy_count, " +
                "sum(case when severity='MEDIUM' then 1 else 0 end) as medium_count, " +
                "sum(case when severity='LIGHT' then 1 else 0 end) as light_count " +
                "from disease_record where tenant_id=:tenantId and deleted=false" + routeFilter(), params);
    }

    @Override
    public Map<String, Object> assessmentSummary(String routeCode, Integer year) {
        MapSqlParameterSource params = params(routeCode, year);
        return one("select count(*) as assessment_count, round(avg(mqi),3) as avg_mqi, round(avg(pqi),3) as avg_pqi, round(avg(pci),3) as avg_pci, " +
                "sum(case when grade='EXCELLENT' then 1 else 0 end) as excellent_count, " +
                "sum(case when grade='GOOD' then 1 else 0 end) as good_count, " +
                "sum(case when grade='MEDIUM' then 1 else 0 end) as medium_count, " +
                "sum(case when grade='POOR' then 1 else 0 end) as poor_count, " +
                "sum(case when grade='BAD' then 1 else 0 end) as bad_count " +
                "from assessment_result where tenant_id=:tenantId and deleted=false" + routeFilter() + yearFilter(), params);
    }

    private MapSqlParameterSource params(String routeCode, Integer year) {
        return new MapSqlParameterSource()
                .addValue("tenantId", TenantContextHolder.getTenantId())
                .addValue("routeCode", routeCode == null ? "" : routeCode)
                .addValue("year", year == null ? "" : String.valueOf(year));
    }

    private String routeFilter() {
        return " and (nullif(:routeCode, '') is null or route_code = nullif(:routeCode, '')) ";
    }

    private String yearFilter() {
        return " and (nullif(:year, '') is null or cast(year as text) = nullif(:year, '')) ";
    }

    private Object value(String sql, MapSqlParameterSource params) {
        return namedParameterJdbcTemplate.queryForObject(sql, params, Object.class);
    }

    private Map<String, Object> one(String sql, MapSqlParameterSource params) {
        List<Map<String, Object>> list = namedParameterJdbcTemplate.queryForList(sql, params);
        return list.isEmpty() ? new LinkedHashMap<>() : list.get(0);
    }
}
