package com.smartroad.srmp.gis.service.impl;

import com.smartroad.srmp.gis.util.GeoJsonParseUtils;
import com.smartroad.srmp.gis.vo.GeoJsonFeatureCollectionVO;
import com.smartroad.srmp.gis.vo.GeoJsonFeatureVO;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GisJdbcGeoJsonHelper {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public GisJdbcGeoJsonHelper(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public GeoJsonFeatureCollectionVO queryFeatures(String sql, MapSqlParameterSource params) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);
        GeoJsonFeatureCollectionVO fc = new GeoJsonFeatureCollectionVO();
        for (Map<String, Object> row : rows) {
            GeoJsonFeatureVO feature = new GeoJsonFeatureVO();
            Object id = row.get("id");
            feature.setId(id == null ? null : String.valueOf(id));

            Object geometry = row.get("geometry_geojson");
            feature.setGeometry(GeoJsonParseUtils.parse(geometry == null ? null : String.valueOf(geometry)));

            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String key = entry.getKey();
                if ("id".equals(key) || "geometry_geojson".equals(key)) {
                    continue;
                }
                feature.getProperties().put(toCamel(key), entry.getValue());
            }
            fc.getFeatures().add(feature);
        }
        return fc;
    }

    public MapSqlParameterSource baseParams(Object query) {
        return new MapSqlParameterSource()
                .addValue("tenantId", TenantContextHolder.getTenantId())
                .addValue("routeCode", stringValue(query, "routeCode"))
                .addValue("year", stringValue(query, "year"))
                .addValue("indexCode", stringValue(query, "indexCode"))
                .addValue("grade", stringValue(query, "grade"))
                .addValue("diseaseType", stringValue(query, "diseaseType"))
                .addValue("severity", stringValue(query, "severity"))
                .addValue("taskId", stringValue(query, "taskId"))
                .addValue("limit", 5000);
    }

    public String optionalRoute() {
        return " and (nullif(:routeCode, '') is null or route_code = nullif(:routeCode, '')) ";
    }

    public String optionalYear() {
        return " and (nullif(:year, '') is null or cast(year as text) = nullif(:year, '')) ";
    }

    public String optionalTask() {
        return " and (nullif(:taskId, '') is null or task_id = nullif(:taskId, '')) ";
    }

    public String optionalGrade() {
        return " and (nullif(:grade, '') is null or grade = nullif(:grade, '')) ";
    }

    public String optionalDiseaseType() {
        return " and (nullif(:diseaseType, '') is null or disease_type = nullif(:diseaseType, '')) ";
    }

    public String optionalSeverity() {
        return " and (nullif(:severity, '') is null or severity = nullif(:severity, '')) ";
    }

    public String stringValue(Object bean, String property) {
        if (bean == null) {
            return "";
        }
        try {
            String methodName = "get" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
            Method method = bean.getClass().getMethod(methodName);
            Object value = method.invoke(bean);
            return value == null ? "" : String.valueOf(value);
        } catch (Exception ignore) {
            return "";
        }
    }

    private String toCamel(String key) {
        StringBuilder sb = new StringBuilder();
        boolean upperNext = false;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == '_') {
                upperNext = true;
                continue;
            }
            if (upperNext) {
                sb.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}