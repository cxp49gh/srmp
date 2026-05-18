package com.smartroad.srmp.gis.service.impl;

import com.smartroad.srmp.disease.dto.DiseaseQueryDTO;
import com.smartroad.srmp.gis.service.GisDiseaseLayerService;
import com.smartroad.srmp.gis.util.GeoJsonParseUtils;
import com.smartroad.srmp.gis.util.GisStyleUtils;
import com.smartroad.srmp.gis.vo.GeoJsonFeatureCollectionVO;
import com.smartroad.srmp.gis.vo.GeoJsonFeatureVO;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GisDiseaseLayerServiceImpl implements GisDiseaseLayerService {
    private static final int SUMMARY_ZOOM = 14;
    private static final int DETAIL_MIN_ZOOM = 17;
    private static final int DETAIL_LIMIT = 1000;
    private static final int CLUSTER_LIMIT = 500;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public GeoJsonFeatureCollectionVO diseases(DiseaseQueryDTO query) {
        DiseaseQueryDTO q = query == null ? new DiseaseQueryDTO() : query;
        GeoJsonFeatureCollectionVO fc = new GeoJsonFeatureCollectionVO();
        if (!validBbox(q)) {
            fc.setMode("summary");
            fc.setTotal(0L);
            fc.setLimit(0);
            fc.setMessage("请移动地图到有效范围后查看病害数据");
            return fc;
        }

        MapSqlParameterSource params = params(q);
        long total = countDiseases(params);
        fc.setTotal(total);

        double zoom = q.getZoom() == null ? 0D : q.getZoom();
        if (zoom < SUMMARY_ZOOM) {
            fc.setMode("summary");
            fc.setLimit(0);
            fc.setMessage("请放大到14级查看病害总和");
            return fc;
        }

        if (zoom < DETAIL_MIN_ZOOM) {
            fc.setMode("cluster");
            fc.setLimit(CLUSTER_LIMIT);
            fc.setMessage(total > 0 ? "当前范围病害 " + total + " 条，已按14级总和显示" : "当前范围暂无病害数据");
            addClusterFeatures(fc, params);
            return fc;
        }

        if (total > DETAIL_LIMIT) {
            fc.setMode("too_many");
            fc.setLimit(DETAIL_LIMIT);
            fc.setMessage("当前区域数据太多，请放大地图查看");
            return fc;
        }

        fc.setMode("detail");
        fc.setLimit(DETAIL_LIMIT);
        fc.setMessage(total > 0 ? "" : "当前范围暂无病害数据");
        addDetailFeatures(fc, params);
        return fc;
    }

    private void addDetailFeatures(GeoJsonFeatureCollectionVO fc, MapSqlParameterSource params) {
        String sql = "select id, route_code, direction, lane_no, start_stake, end_stake, disease_category, disease_type, "
                + "disease_name, severity, quantity, measure_unit, status, ST_AsGeoJSON(geom) as geometry_geojson "
                + "from disease_record where " + whereSql()
                + " order by case upper(coalesce(severity,'')) when 'HEAVY' then 1 when 'SEVERE' then 1 when 'SERIOUS' then 1 "
                + "when 'MEDIUM' then 2 when 'LIGHT' then 3 else 4 end, route_code asc, start_stake asc nulls last "
                + "limit :detailLimit";
        params.addValue("detailLimit", DETAIL_LIMIT);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);
        for (Map<String, Object> row : rows) {
            GeoJsonFeatureVO f = new GeoJsonFeatureVO();
            f.setId(string(row.get("id")));
            f.setGeometry(GeoJsonParseUtils.parse(string(row.get("geometry_geojson"))));
            f.getProperties().put("objectType", "DISEASE");
            f.getProperties().put("routeCode", row.get("route_code"));
            f.getProperties().put("direction", row.get("direction"));
            f.getProperties().put("laneNo", row.get("lane_no"));
            f.getProperties().put("startStake", row.get("start_stake"));
            f.getProperties().put("endStake", row.get("end_stake"));
            f.getProperties().put("diseaseCategory", row.get("disease_category"));
            f.getProperties().put("diseaseType", row.get("disease_type"));
            f.getProperties().put("diseaseName", row.get("disease_name"));
            f.getProperties().put("severity", row.get("severity"));
            f.getProperties().put("quantity", row.get("quantity"));
            f.getProperties().put("measureUnit", row.get("measure_unit"));
            f.getProperties().put("status", row.get("status"));
            f.getProperties().put("color", GisStyleUtils.colorBySeverity(string(row.get("severity"))));
            fc.getFeatures().add(f);
        }
    }

    private void addClusterFeatures(GeoJsonFeatureCollectionVO fc, MapSqlParameterSource params) {
        params.addValue("gridSize", clusterGridSize());
        params.addValue("clusterLimit", CLUSTER_LIMIT);
        String sql = "with base as ("
                + "select ST_PointOnSurface(geom) as p, severity, coalesce(nullif(btrim(disease_name), ''), nullif(btrim(disease_type), ''), '未分类') as disease_name_label "
                + "from disease_record where " + whereSql()
                + "), bucketed as ("
                + "select floor(ST_X(p) / :gridSize) as gx, floor(ST_Y(p) / :gridSize) as gy, p, severity, disease_name_label from base"
                + "), grouped as ("
                + "select gx, gy, avg(ST_X(p)) as lng, avg(ST_Y(p)) as lat, count(1) as count, "
                + "sum(case when upper(coalesce(severity,'')) in ('HEAVY','SEVERE','SERIOUS') then 1 else 0 end) as heavy_count, "
                + "sum(case when upper(coalesce(severity,'')) = 'MEDIUM' then 1 else 0 end) as medium_count, "
                + "sum(case when upper(coalesce(severity,'')) = 'LIGHT' then 1 else 0 end) as light_count "
                + "from bucketed group by gx, gy"
                + "), type_counts as ("
                + "select gx, gy, disease_name_label, count(1) as type_count from bucketed group by gx, gy, disease_name_label"
                + "), limited_clusters as ("
                + "select * from grouped order by count desc limit :clusterLimit"
                + ") select c.gx, c.gy, c.lng, c.lat, c.count, c.heavy_count, c.medium_count, c.light_count, "
                + "t.disease_name_label, t.type_count "
                + "from limited_clusters c left join type_counts t on t.gx = c.gx and t.gy = c.gy "
                + "order by c.count desc, t.type_count desc nulls last, t.disease_name_label asc nulls last";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);
        Map<String, GeoJsonFeatureVO> featureByBucket = new LinkedHashMap<>();
        int idx = 0;
        for (Map<String, Object> row : rows) {
            String bucketKey = string(row.get("gx")) + "|" + string(row.get("gy"));
            GeoJsonFeatureVO f = featureByBucket.get(bucketKey);
            if (f == null) {
                double lng = number(row.get("lng"));
                double lat = number(row.get("lat"));
                if (!Double.isFinite(lng) || !Double.isFinite(lat)) {
                    continue;
                }
                f = new GeoJsonFeatureVO();
                f.setId("cluster-" + (++idx));
                f.setGeometry(point(lng, lat));
                long count = longValue(row.get("count"));
                Map<String, Object> diseaseNameStats = new LinkedHashMap<>();
                List<Map<String, Object>> diseaseStats = new ArrayList<>();
                f.getProperties().put("objectType", "DISEASE_CLUSTER");
                f.getProperties().put("cluster", true);
                f.getProperties().put("clusterLevel", "SUMMARY_14");
                f.getProperties().put("count", count);
                f.getProperties().put("diseaseNameStats", diseaseNameStats);
                f.getProperties().put("diseaseTypeStats", diseaseNameStats);
                f.getProperties().put("diseaseStats", diseaseStats);
                f.getProperties().put("severityStats", severityStats(row));
                f.getProperties().put("color", clusterColor(count));
                featureByBucket.put(bucketKey, f);
            }

            String diseaseName = text(string(row.get("disease_name_label")));
            long typeCount = longValue(row.get("type_count"));
            if (!diseaseName.isEmpty() && typeCount > 0) {
                @SuppressWarnings("unchecked")
                Map<String, Object> diseaseNameStats = (Map<String, Object>) f.getProperties().get("diseaseNameStats");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> diseaseStats = (List<Map<String, Object>>) f.getProperties().get("diseaseStats");
                diseaseNameStats.put(diseaseName, typeCount);
                diseaseStats.add(diseaseStatItem(diseaseName, typeCount));
            }
        }
        fc.getFeatures().addAll(featureByBucket.values());
    }

    private long countDiseases(MapSqlParameterSource params) {
        Long count = jdbcTemplate.queryForObject("select count(1) from disease_record where " + whereSql(), params, Long.class);
        return count == null ? 0L : count;
    }

    private String whereSql() {
        return "tenant_id = :tenantId and deleted = false and geom is not null "
                + "and ST_Intersects(geom, ST_MakeEnvelope(:minLng, :minLat, :maxLng, :maxLat, 4326)) "
                + "and (nullif(:projectId, '') is null or project_id = nullif(:projectId, '')) "
                + "and (nullif(:routeCode, '') is null or route_code = nullif(:routeCode, '')) "
                + "and (nullif(:direction, '') is null or direction = nullif(:direction, '')) "
                + "and (nullif(:taskId, '') is null or task_id = nullif(:taskId, '')) "
                + "and (nullif(:diseaseCategory, '') is null or disease_category = nullif(:diseaseCategory, '')) "
                + "and (nullif(:diseaseType, '') is null or disease_type = nullif(:diseaseType, '')) "
                + "and (nullif(:severity, '') is null or severity = nullif(:severity, '')) "
                + "and (nullif(:status, '') is null or status = nullif(:status, '')) "
                + "and (cast(:startStake as numeric) is null or end_stake is null or end_stake >= cast(:startStake as numeric)) "
                + "and (cast(:endStake as numeric) is null or start_stake is null or start_stake <= cast(:endStake as numeric))";
    }

    private MapSqlParameterSource params(DiseaseQueryDTO q) {
        return new MapSqlParameterSource()
                .addValue("tenantId", TenantContextHolder.getTenantId())
                .addValue("minLng", q.getMinLng())
                .addValue("minLat", q.getMinLat())
                .addValue("maxLng", q.getMaxLng())
                .addValue("maxLat", q.getMaxLat())
                .addValue("projectId", text(q.getProjectId()))
                .addValue("routeCode", text(q.getRouteCode()))
                .addValue("direction", text(q.getDirection()))
                .addValue("taskId", text(q.getTaskId()))
                .addValue("diseaseCategory", text(q.getDiseaseCategory()))
                .addValue("diseaseType", text(q.getDiseaseType()))
                .addValue("severity", text(q.getSeverity()))
                .addValue("status", text(q.getStatus()))
                .addValue("startStake", q.getStartStake())
                .addValue("endStake", q.getEndStake());
    }

    private boolean validBbox(DiseaseQueryDTO q) {
        return finite(q.getMinLng()) && finite(q.getMinLat()) && finite(q.getMaxLng()) && finite(q.getMaxLat())
                && q.getMinLng() >= -180D && q.getMaxLng() <= 180D
                && q.getMinLat() >= -90D && q.getMaxLat() <= 90D
                && q.getMinLng() < q.getMaxLng() && q.getMinLat() < q.getMaxLat();
    }

    private boolean finite(Double value) {
        return value != null && Double.isFinite(value);
    }

    private double clusterGridSize() {
        return 0.01D;
    }

    private Map<String, Object> point(double lng, double lat) {
        Map<String, Object> geometry = new LinkedHashMap<>();
        geometry.put("type", "Point");
        geometry.put("coordinates", new double[]{lng, lat});
        return geometry;
    }

    private Map<String, Object> severityStats(Map<String, Object> row) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("轻度", longValue(row.get("light_count")));
        stats.put("中度", longValue(row.get("medium_count")));
        stats.put("重度", longValue(row.get("heavy_count")));
        return stats;
    }

    private String clusterColor(long count) {
        if (count >= 100) return "#dc2626";
        if (count >= 30) return "#f97316";
        return "#eab308";
    }

    private Map<String, Object> diseaseStatItem(String name, long count) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("count", count);
        return item;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private long longValue(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        if (value == null) return 0L;
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignore) {
            return 0L;
        }
    }

    private double number(Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value == null) return Double.NaN;
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignore) {
            return Double.NaN;
        }
    }
}
