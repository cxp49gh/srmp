package com.smartroad.srmp.gis.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroad.srmp.gis.dto.MapRegionAnalysisRequest;
import com.smartroad.srmp.gis.service.MapRegionAnalysisService;
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
public class MapRegionAnalysisServiceImpl implements MapRegionAnalysisService {

    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> analyze(MapRegionAnalysisRequest request) {
        validatePolygon(request == null ? null : request.getGeometry());
        MapSqlParameterSource params = baseParams(request);
        String regionSql = "ST_SetSRID(ST_GeomFromGeoJSON(:geometryJson), 4326)";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("geometry", request.getGeometry());
        result.put("query", request.getQuery() == null ? new LinkedHashMap<>() : request.getQuery());
        result.put("layers", request.getLayers() == null ? Collections.emptyList() : request.getLayers());
        result.put("areaKm2", firstValue("select round((ST_Area(" + regionSql + "::geography) / 1000000.0)::numeric, 4)", params));
        result.put("routeCount", firstValue("select count(*) from road_route where tenant_id=:tenantId and deleted=false " + routeFilter("") + spatialFilter("geom", regionSql), params));
        result.put("sectionCount", firstValue("select count(*) from road_section where tenant_id=:tenantId and deleted=false " + routeFilter("") + spatialFilter("geom", regionSql), params));
        result.put("unitCount", firstValue("select count(*) from road_evaluation_unit where tenant_id=:tenantId and deleted=false " + routeFilter("") + spatialFilter("geom", regionSql), params));
        result.put("diseaseSummary", diseaseSummary(params, regionSql));
        result.put("assessmentSummary", assessmentSummary(params, regionSql));
        result.put("hotspots", hotspots(params, regionSql));
        result.put("sourcePrecision", "POSTGIS");
        return result;
    }

    private void validatePolygon(Map<String, Object> geometry) {
        if (geometry == null || !"Polygon".equals(String.valueOf(geometry.get("type")))) {
            throw new IllegalArgumentException("geometry 只支持 GeoJSON Polygon");
        }
        Object coordinates = geometry.get("coordinates");
        if (!(coordinates instanceof List) || ((List<?>) coordinates).isEmpty()) {
            throw new IllegalArgumentException("Polygon coordinates 不能为空");
        }
        Object ring = ((List<?>) coordinates).get(0);
        if (!(ring instanceof List) || ((List<?>) ring).size() < 4) {
            throw new IllegalArgumentException("Polygon 外环至少需要 4 个坐标点");
        }
        Object first = ((List<?>) ring).get(0);
        Object last = ((List<?>) ring).get(((List<?>) ring).size() - 1);
        if (!String.valueOf(first).equals(String.valueOf(last))) {
            throw new IllegalArgumentException("Polygon 外环必须闭合");
        }
    }

    private MapSqlParameterSource baseParams(MapRegionAnalysisRequest request) {
        Map<String, Object> query = request == null || request.getQuery() == null ? new LinkedHashMap<>() : request.getQuery();
        return new MapSqlParameterSource()
                .addValue("tenantId", TenantContextHolder.getTenantId())
                .addValue("geometryJson", toJson(request.getGeometry()))
                .addValue("routeCode", safe(query.get("routeCode")))
                .addValue("year", safe(query.get("year")));
    }

    private String routeFilter(String alias) {
        String prefix = alias == null || alias.isEmpty() ? "" : alias + ".";
        return " and (nullif(:routeCode, '') is null or " + prefix + "route_code = nullif(:routeCode, '')) ";
    }

    private String yearFilter(String alias) {
        String prefix = alias == null || alias.isEmpty() ? "" : alias + ".";
        return " and (nullif(:year, '') is null or cast(" + prefix + "year as text) = nullif(:year, '')) ";
    }

    private String spatialFilter(String column, String regionSql) {
        return " and " + column + " is not null and ST_Intersects(" + column + ", " + regionSql + ") ";
    }

    private Map<String, Object> diseaseSummary(MapSqlParameterSource params, String regionSql) {
        Map<String, Object> summary = queryOne(
                "select count(*) as disease_count, " +
                        "sum(case when severity='HEAVY' then 1 else 0 end) as heavy_count, " +
                        "sum(case when severity='MEDIUM' then 1 else 0 end) as medium_count, " +
                        "sum(case when severity='LIGHT' then 1 else 0 end) as light_count " +
                        "from disease_record where tenant_id=:tenantId and deleted=false " +
                        routeFilter("") + spatialFilter("geom", regionSql),
                params);
        List<Map<String, Object>> byType = namedParameterJdbcTemplate.queryForList(
                "select disease_name, severity, count(*) as count, coalesce(sum(quantity),0) as quantity " +
                        "from disease_record where tenant_id=:tenantId and deleted=false " +
                        routeFilter("") + spatialFilter("geom", regionSql) +
                        "group by disease_name, severity order by count desc limit 10",
                params);
        summary.put("byType", byType);
        return summary;
    }

    private Map<String, Object> assessmentSummary(MapSqlParameterSource params, String regionSql) {
        Map<String, Object> summary = queryOne(
                "select count(*) as assessment_count, round(avg(ar.mqi),3) as avg_mqi, round(avg(ar.pqi),3) as avg_pqi, round(avg(ar.pci),3) as avg_pci, " +
                        "sum(case when ar.grade in ('POOR','BAD') then 1 else 0 end) as poor_bad_count " +
                        "from assessment_result ar left join road_evaluation_unit u on u.tenant_id=ar.tenant_id and u.id=ar.unit_id and u.deleted=false " +
                        "where ar.tenant_id=:tenantId and ar.deleted=false " +
                        routeFilter("ar") + yearFilter("ar") +
                        " and u.geom is not null and ST_Intersects(u.geom, " + regionSql + ")",
                params);
        summary.put("poorBadRate", rate(summary.get("poor_bad_count"), summary.get("assessment_count")));
        return summary;
    }

    private List<Map<String, Object>> hotspots(MapSqlParameterSource params, String regionSql) {
        return namedParameterJdbcTemplate.queryForList(
                "select route_code, min(start_stake) as start_stake, max(end_stake) as end_stake, count(*) as disease_count, " +
                        "sum(case when severity='HEAVY' then 1 else 0 end) as heavy_count " +
                        "from disease_record where tenant_id=:tenantId and deleted=false " +
                        routeFilter("") + spatialFilter("geom", regionSql) +
                        "group by route_code order by heavy_count desc, disease_count desc limit 5",
                params);
    }

    private Object firstValue(String sql, MapSqlParameterSource params) {
        return namedParameterJdbcTemplate.queryForObject(sql, params, Object.class);
    }

    private Map<String, Object> queryOne(String sql, MapSqlParameterSource params) {
        List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(sql, params);
        return rows.isEmpty() ? new LinkedHashMap<>() : new LinkedHashMap<>(rows.get(0));
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

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("GeoJSON 序列化失败：" + e.getMessage(), e);
        }
    }
}
