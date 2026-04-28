package com.smartroad.srmp.gis.service.impl;

import com.smartroad.srmp.gis.service.GisMapSupportService;
import com.smartroad.srmp.gis.vo.GeoJsonFeatureCollectionVO;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GisMapSupportServiceImpl implements GisMapSupportService {

    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    public Map<String, Object> mapStatistics(Map<String, Object> request) {
        MapSqlParameterSource params = baseParams(request);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalLengthKm", firstValue(
                "select coalesce(sum(length_km),0) from road_route where tenant_id=:tenantId and deleted=false " + optionalRoute(),
                params
        ));
        result.put("routeCount", firstValue(
                "select count(*) from road_route where tenant_id=:tenantId and deleted=false " + optionalRoute(),
                params
        ));
        result.put("sectionCount", firstValue(
                "select count(*) from road_section where tenant_id=:tenantId and deleted=false " + optionalRoute(),
                params
        ));
        result.put("unitCount", firstValue(
                "select count(*) from road_evaluation_unit where tenant_id=:tenantId and deleted=false " + optionalRoute(),
                params
        ));
        result.put("diseaseCount", firstValue(
                "select count(*) from disease_record where tenant_id=:tenantId and deleted=false " + optionalRoute(),
                params
        ));
        result.put("heavyDiseaseCount", firstValue(
                "select count(*) from disease_record where tenant_id=:tenantId and deleted=false and severity='HEAVY' " + optionalRoute(),
                params
        ));

        Map<String, Object> assessment = queryOne(
                "select count(*) as assessment_count, " +
                        "round(avg(mqi),3) as avg_mqi, " +
                        "round(avg(pqi),3) as avg_pqi, " +
                        "round(avg(pci),3) as avg_pci, " +
                        "sum(case when grade in ('EXCELLENT','GOOD') then 1 else 0 end) as good_count, " +
                        "sum(case when grade in ('POOR','BAD') then 1 else 0 end) as poor_count " +
                        "from assessment_result where tenant_id=:tenantId and deleted=false " + optionalRoute() + optionalYear(),
                params
        );
        result.put("assessmentCount", assessment.get("assessment_count"));
        result.put("avgMqi", assessment.get("avg_mqi"));
        result.put("avgPqi", assessment.get("avg_pqi"));
        result.put("avgPci", assessment.get("avg_pci"));
        result.put("excellentGoodRate", rate(assessment.get("good_count"), assessment.get("assessment_count")));
        result.put("poorBadRate", rate(assessment.get("poor_count"), assessment.get("assessment_count")));
        return result;
    }

    @Override
    public Map<String, Object> objectDetail(String objectType, String id) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", TenantContextHolder.getTenantId())
                .addValue("id", id);
        String type = objectType == null ? "" : objectType.trim().toUpperCase();
        if ("ROAD_ROUTE".equals(type)) {
            return queryOne("select id, route_code, route_name, route_type, technical_grade, start_stake, end_stake, length_km from road_route where tenant_id=:tenantId and id=:id and deleted=false", params);
        }
        if ("ROAD_SECTION".equals(type)) {
            return queryOne("select id, route_code, section_code, section_name, direction, start_stake, end_stake, length_km, pavement_type from road_section where tenant_id=:tenantId and id=:id and deleted=false", params);
        }
        if ("EVALUATION_UNIT".equals(type)) {
            return queryOne("select id, route_code, unit_code, direction, lane_no, start_stake, end_stake, length_m from road_evaluation_unit where tenant_id=:tenantId and id=:id and deleted=false", params);
        }
        if ("DISEASE".equals(type)) {
            return queryOne("select id, route_code, disease_type, disease_name, severity, start_stake, end_stake, quantity, measure_unit, status, verified from disease_record where tenant_id=:tenantId and id=:id and deleted=false", params);
        }
        if ("ASSESSMENT".equals(type) || "ASSESSMENT_RESULT".equals(type)) {
            return queryOne("select id, route_code, unit_id, start_stake, end_stake, year, mqi, pqi, pci, rqi, rdi, grade from assessment_result where tenant_id=:tenantId and id=:id and deleted=false", params);
        }
        return Collections.emptyMap();
    }

    @Override
    public GeoJsonFeatureCollectionVO spatialQuery(Map<String, Object> query) {
        // 阶段一演示版：空间查询接口保持可用，后续可根据 GeoJSON 多边形实现 ST_Intersects 聚合查询。
        return new GeoJsonFeatureCollectionVO();
    }

    private MapSqlParameterSource baseParams(Map<String, Object> request) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("tenantId", TenantContextHolder.getTenantId());
        params.addValue("routeCode", getString(request, "routeCode") == null ? "" : getString(request, "routeCode"));
        params.addValue("year", getString(request, "year") == null ? "" : getString(request, "year"));
        return params;
    }

    private String optionalRoute() {
        return " and (nullif(:routeCode, '') is null or route_code = nullif(:routeCode, '')) ";
    }

    private String optionalYear() {
        return " and (nullif(:year, '') is null or cast(year as text) = nullif(:year, '')) ";
    }

    private Object firstValue(String sql, MapSqlParameterSource params) {
        return namedParameterJdbcTemplate.queryForObject(sql, params, Object.class);
    }

    private Map<String, Object> queryOne(String sql, MapSqlParameterSource params) {
        List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(sql, params);
        return rows.isEmpty() ? new LinkedHashMap<>() : rows.get(0);
    }

    private String getString(Map<String, Object> request, String key) {
        if (request == null || request.get(key) == null) {
            return null;
        }
        return String.valueOf(request.get(key));
    }

    private BigDecimal rate(Object numerator, Object denominator) {
        BigDecimal n = toBigDecimal(numerator);
        BigDecimal d = toBigDecimal(denominator);
        if (d.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return n.multiply(new BigDecimal("100")).divide(d, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(String.valueOf(value));
    }
}
