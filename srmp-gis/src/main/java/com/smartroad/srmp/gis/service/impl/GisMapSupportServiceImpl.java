package com.smartroad.srmp.gis.service.impl;

import com.smartroad.srmp.gis.service.GisMapSupportService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroad.srmp.gis.util.GeoJsonParseUtils;
import com.smartroad.srmp.gis.vo.GeoJsonFeatureCollectionVO;
import com.smartroad.srmp.gis.vo.GeoJsonFeatureVO;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class GisMapSupportServiceImpl implements GisMapSupportService {

    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
        GeoJsonFeatureCollectionVO collection = new GeoJsonFeatureCollectionVO();
        Object geometry = query == null ? null : query.get("geometry");
        String geometryGeoJson = toGeoJsonString(geometry);
        if (geometryGeoJson == null) {
            return collection;
        }

        MapSqlParameterSource params = spatialParams(query, geometryGeoJson);
        Set<String> layers = spatialLayers(query);
        int limit = spatialLimit(query);

        if (layers.contains("ROAD_ROUTE")) {
            collection.getFeatures().addAll(querySpatialLayer(
                    "ROAD_ROUTE",
                    "select id, ST_AsGeoJSON(geom) as geom_geojson, route_code, route_name, route_type, technical_grade, start_stake, end_stake, length_km " +
                            "from road_route where tenant_id=:tenantId and deleted=false and geom is not null " +
                            optionalRoute() + spatialIntersectsSql("geom") +
                            " order by route_code limit " + limit,
                    params
            ));
        }
        if (layers.contains("ROAD_SECTION")) {
            collection.getFeatures().addAll(querySpatialLayer(
                    "ROAD_SECTION",
                    "select id, ST_AsGeoJSON(geom) as geom_geojson, route_code, section_code, section_name, direction, start_stake, end_stake, length_km, pavement_type " +
                            "from road_section where tenant_id=:tenantId and deleted=false and geom is not null " +
                            optionalRoute() + spatialIntersectsSql("geom") +
                            " order by route_code, start_stake limit " + limit,
                    params
            ));
        }
        if (layers.contains("EVALUATION_UNIT")) {
            collection.getFeatures().addAll(querySpatialLayer(
                    "EVALUATION_UNIT",
                    "select id, ST_AsGeoJSON(geom) as geom_geojson, route_code, unit_code, direction, lane_no, start_stake, end_stake, length_m " +
                            "from road_evaluation_unit where tenant_id=:tenantId and deleted=false and geom is not null " +
                            optionalRoute() + spatialIntersectsSql("geom") +
                            " order by route_code, start_stake limit " + limit,
                    params
            ));
        }
        if (layers.contains("DISEASE")) {
            collection.getFeatures().addAll(querySpatialLayer(
                    "DISEASE",
                    "select id, ST_AsGeoJSON(geom) as geom_geojson, route_code, direction, lane_no, start_stake, end_stake, disease_category, disease_type, disease_name, severity, quantity, measure_unit, status, verified " +
                            "from disease_record where tenant_id=:tenantId and deleted=false and geom is not null " +
                            optionalRoute() + optionalDiseaseType() + optionalSeverity() + spatialIntersectsSql("geom") +
                            " order by route_code, start_stake limit " + limit,
                    params
            ));
        }
        if (layers.contains("ASSESSMENT") || layers.contains("ASSESSMENT_RESULT")) {
            collection.getFeatures().addAll(querySpatialLayer(
                    "ASSESSMENT_RESULT",
                    "select ar.id, ST_AsGeoJSON(u.geom) as geom_geojson, ar.route_code, ar.direction, ar.unit_id, ar.start_stake, ar.end_stake, ar.year, " +
                            "ar.mqi, ar.sci, ar.pqi, ar.bci, ar.tci, ar.pci, ar.rqi, ar.rdi, ar.pbi, ar.pwi, ar.sri, ar.pssi, ar.grade " +
                            "from assessment_result ar left join road_evaluation_unit u on u.tenant_id=ar.tenant_id and u.id=ar.unit_id and u.deleted=false " +
                            "where ar.tenant_id=:tenantId and ar.deleted=false and u.geom is not null " +
                            optionalRoute("ar") + optionalYear("ar") + optionalGrade("ar") + spatialIntersectsSql("u.geom") +
                            " order by ar.route_code, ar.start_stake limit " + limit,
                    params
            ));
        }
        return collection;
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
        return optionalYear("");
    }

    private String optionalRoute(String alias) {
        String prefix = alias == null || alias.trim().isEmpty() ? "" : alias.trim() + ".";
        return " and (nullif(:routeCode, '') is null or " + prefix + "route_code = nullif(:routeCode, '')) ";
    }

    private String optionalYear(String alias) {
        String prefix = alias == null || alias.trim().isEmpty() ? "" : alias.trim() + ".";
        return " and (nullif(:year, '') is null or cast(" + prefix + "year as text) = nullif(:year, '')) ";
    }

    private String optionalGrade(String alias) {
        String prefix = alias == null || alias.trim().isEmpty() ? "" : alias.trim() + ".";
        return " and (nullif(:grade, '') is null or " + prefix + "grade = nullif(:grade, '')) ";
    }

    private String optionalDiseaseType() {
        return " and (nullif(:diseaseType, '') is null or disease_type = nullif(:diseaseType, '')) ";
    }

    private String optionalSeverity() {
        return " and (nullif(:severity, '') is null or severity = nullif(:severity, '')) ";
    }

    private String spatialIntersectsSql(String geomColumn) {
        return " and ST_Intersects(" + geomColumn + ", ST_SetSRID(ST_GeomFromGeoJSON(:geometryGeoJson), 4326)) ";
    }

    @SuppressWarnings("unchecked")
    private MapSqlParameterSource spatialParams(Map<String, Object> request, String geometryGeoJson) {
        Map<String, Object> nestedQuery = request == null || !(request.get("query") instanceof Map)
                ? Collections.emptyMap()
                : (Map<String, Object>) request.get("query");
        MapSqlParameterSource params = baseParams(nestedQuery);
        params.addValue("geometryGeoJson", geometryGeoJson);
        params.addValue("grade", getString(nestedQuery, "grade") == null ? "" : getString(nestedQuery, "grade"));
        params.addValue("diseaseType", getString(nestedQuery, "diseaseType") == null ? "" : getString(nestedQuery, "diseaseType"));
        params.addValue("severity", getString(nestedQuery, "severity") == null ? "" : getString(nestedQuery, "severity"));
        return params;
    }

    @SuppressWarnings("unchecked")
    private Set<String> spatialLayers(Map<String, Object> request) {
        Object value = request == null ? null : request.get("layers");
        Set<String> layers = new LinkedHashSet<>();
        if (value instanceof Iterable) {
            for (Object item : (Iterable<Object>) value) {
                addLayer(layers, item);
            }
        } else if (value instanceof String) {
            String[] parts = String.valueOf(value).split(",");
            for (String part : parts) {
                addLayer(layers, part);
            }
        }
        if (layers.isEmpty()) {
            layers.add("ROAD_ROUTE");
            layers.add("ROAD_SECTION");
            layers.add("EVALUATION_UNIT");
            layers.add("DISEASE");
            layers.add("ASSESSMENT_RESULT");
        }
        return layers;
    }

    private void addLayer(Set<String> layers, Object item) {
        if (item == null) {
            return;
        }
        String layer = String.valueOf(item).trim().toUpperCase();
        if (layer.length() == 0) {
            return;
        }
        if ("ASSESSMENT".equals(layer)) {
            layer = "ASSESSMENT_RESULT";
        }
        layers.add(layer);
    }

    @SuppressWarnings("unchecked")
    private int spatialLimit(Map<String, Object> request) {
        Object value = null;
        if (request != null && request.get("options") instanceof Map) {
            Map<String, Object> options = (Map<String, Object>) request.get("options");
            value = options.get("limit") == null ? options.get("maxFeatures") : options.get("limit");
        }
        if (value == null && request != null) {
            value = request.get("limit");
        }
        try {
            int parsed = value == null ? 200 : Integer.parseInt(String.valueOf(value));
            if (parsed < 1) {
                return 1;
            }
            return Math.min(parsed, 1000);
        } catch (Exception e) {
            return 200;
        }
    }

    private String toGeoJsonString(Object geometry) {
        if (geometry == null) {
            return null;
        }
        try {
            if (geometry instanceof String) {
                String text = String.valueOf(geometry).trim();
                return text.isEmpty() ? null : text;
            }
            return OBJECT_MAPPER.writeValueAsString(geometry);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private List<GeoJsonFeatureVO> querySpatialLayer(String objectType, String sql, MapSqlParameterSource params) {
        List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(sql, params);
        List<GeoJsonFeatureVO> features = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            GeoJsonFeatureVO feature = new GeoJsonFeatureVO();
            Object id = row.get("id");
            feature.setId(id == null ? null : String.valueOf(id));
            feature.setGeometry(GeoJsonParseUtils.parse(row.get("geom_geojson") == null ? null : String.valueOf(row.get("geom_geojson"))));
            feature.getProperties().put("objectType", objectType);
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String key = entry.getKey();
                if ("geom_geojson".equalsIgnoreCase(key) || "id".equalsIgnoreCase(key)) {
                    continue;
                }
                feature.getProperties().put(toCamelCase(key), entry.getValue());
            }
            features.add(feature);
        }
        return features;
    }

    private String toCamelCase(String key) {
        if (key == null || key.indexOf('_') < 0) {
            return key;
        }
        StringBuilder builder = new StringBuilder();
        boolean upper = false;
        for (int i = 0; i < key.length(); i++) {
            char ch = key.charAt(i);
            if (ch == '_') {
                upper = true;
                continue;
            }
            builder.append(upper ? Character.toUpperCase(ch) : ch);
            upper = false;
        }
        return builder.toString();
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
