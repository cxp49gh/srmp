package com.smartroad.srmp.gis.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroad.srmp.gis.dto.SourceBindingVerifyRequest;
import com.smartroad.srmp.gis.service.SourceBindingVerifyService;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SourceBindingVerifyServiceImpl implements SourceBindingVerifyService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<String> GEOJSON_TYPES = Arrays.asList(
            "Point", "MultiPoint", "LineString", "MultiLineString",
            "Polygon", "MultiPolygon", "GeometryCollection"
    );

    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    public Map<String, Object> verify(SourceBindingVerifyRequest request) {
        if (request == null) {
            return result("INVALID", "验证请求不能为空", null, null, 0);
        }
        String projectId = text(request.getProjectId());
        if (!StringUtils.hasText(projectId)) {
            return result("INVALID", "projectId 不能为空", null, null, 0);
        }
        Map<String, Object> target = request.getMapTarget() == null
                ? Collections.<String, Object>emptyMap()
                : new LinkedHashMap<>(request.getMapTarget());
        String bindingType = upper(request.getBindingType());
        if ("OBJECT".equals(bindingType)) {
            return verifyObject(projectId, target);
        }
        if ("RANGE".equals(bindingType)) {
            return verifyRange(projectId, target);
        }
        return result("INVALID", "bindingType 必须为 OBJECT 或 RANGE", null, null, 0);
    }

    private Map<String, Object> verifyObject(
            String projectId,
            Map<String, Object> target
    ) {
        String objectType = normalizeObjectType(value(target, "objectType", "object_type"));
        String objectId = strictText(value(target, "objectId", "object_id"));
        String sql = objectSql(objectType);
        if (!StringUtils.hasText(objectType) || !StringUtils.hasText(objectId)) {
            return result(
                    "INVALID", "对象绑定必须同时提供 objectType 和 objectId",
                    null, recommendedLayer(objectType), 0
            );
        }
        if (sql == null) {
            return result(
                    "INVALID", "不支持的 objectType：" + objectType,
                    null, recommendedLayer(objectType), 0
            );
        }

        BigDecimal requestedStart = decimal(value(target, "startStake", "start_stake"));
        BigDecimal requestedEnd = decimal(value(target, "endStake", "end_stake"));
        if (hasOneStake(target) && (requestedStart == null || requestedEnd == null)) {
            return result(
                    "INVALID", "对象辅助桩号必须同时为有效 startStake 和 endStake",
                    null, recommendedLayer(objectType), 0
            );
        }
        if (requestedStart != null && requestedEnd != null
                && requestedStart.compareTo(requestedEnd) > 0) {
            return result(
                    "INVALID", "对象辅助桩号起点不能大于终点",
                    null, recommendedLayer(objectType), 0
            );
        }

        List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(
                sql,
                baseParams(projectId).addValue("objectId", objectId)
        );
        if (rows.isEmpty()) {
            return result(
                    "NOT_FOUND", "当前项目中未找到该对象",
                    null, recommendedLayer(objectType), 0
            );
        }

        Map<String, Object> row = rows.get(0);
        String requestedRoute = strictText(value(target, "routeCode", "route_code"));
        if (value(target, "routeCode", "route_code") != null
                && !StringUtils.hasText(requestedRoute)) {
            return result(
                    "INVALID", "routeCode 必须为非空字符串",
                    null, recommendedLayer(objectType), 1
            );
        }
        String actualRoute = text(rowValue(row, "route_code", "routeCode"));
        if (StringUtils.hasText(requestedRoute)
                && StringUtils.hasText(actualRoute)
                && !requestedRoute.equals(actualRoute)) {
            return result(
                    "INVALID", "来源路线与当前项目对象路线不一致",
                    null, recommendedLayer(objectType), 1
            );
        }

        BigDecimal actualStart = decimal(rowValue(row, "start_stake", "startStake"));
        BigDecimal actualEnd = decimal(rowValue(row, "end_stake", "endStake"));
        if (requestedStart != null && requestedEnd != null
                && actualStart != null && actualEnd != null
                && !overlaps(requestedStart, requestedEnd, actualStart, actualEnd)) {
            return result(
                    "INVALID", "来源桩号与当前项目对象桩号不一致",
                    null, recommendedLayer(objectType), 1
            );
        }

        Map<String, Object> resolved = new LinkedHashMap<>();
        resolved.put("objectType", objectType);
        resolved.put("objectId", objectId);
        resolved.put("id", objectId);
        putIfPresent(resolved, "routeCode", actualRoute);
        putIfPresent(resolved, "startStake", actualStart);
        putIfPresent(resolved, "endStake", actualEnd);
        return result(
                "VALID", "来源对象已在当前项目验证",
                resolved, recommendedLayer(objectType), 1
        );
    }

    private Map<String, Object> verifyRange(
            String projectId,
            Map<String, Object> target
    ) {
        Object geometry = value(target, "geometry");
        if (geometry != null) {
            return verifyGeometryRange(projectId, target, geometry);
        }
        Object bbox = value(target, "bbox");
        if (bbox != null) {
            return verifyBboxRange(projectId, target, bbox);
        }

        String routeCode = strictText(value(target, "routeCode", "route_code"));
        BigDecimal start = decimal(value(target, "startStake", "start_stake"));
        BigDecimal end = decimal(value(target, "endStake", "end_stake"));
        if (!StringUtils.hasText(routeCode) || start == null || end == null) {
            return result(
                    "INVALID",
                    "路线范围必须提供 routeCode、startStake 和 endStake",
                    null, "roadSection", 0
            );
        }
        if (start.compareTo(end) > 0) {
            return result(
                    "INVALID", "路线范围起点不能大于终点",
                    null, "roadSection", 0
            );
        }

        MapSqlParameterSource params = baseParams(projectId)
                .addValue("routeCode", routeCode)
                .addValue("startStake", start)
                .addValue("endStake", end);
        int matched = count(
                "select count(*) as matched_count from road_section_line "
                        + "where tenant_id=:tenantId and project_id=:projectId "
                        + "and deleted=false and route_code=:routeCode "
                        + "and coalesce(start_stake,end_stake) <= :endStake "
                        + "and coalesce(end_stake,start_stake) >= :startStake",
                params
        );
        Map<String, Object> resolved = new LinkedHashMap<>();
        resolved.put("routeCode", routeCode);
        resolved.put("startStake", start);
        resolved.put("endStake", end);
        return rangeResult(resolved, "roadSection", matched);
    }

    private Map<String, Object> verifyGeometryRange(
            String projectId,
            Map<String, Object> target,
            Object geometry
    ) {
        String validationError = geometryError(geometry);
        if (validationError != null) {
            return result("INVALID", validationError, null, "roadSection", 0);
        }
        String geometryJson;
        try {
            geometryJson = OBJECT_MAPPER.writeValueAsString(geometry);
        } catch (Exception e) {
            return result("INVALID", "geometry 无法序列化", null, "roadSection", 0);
        }
        int matched = count(
                spatialCountSql(
                        "ST_SetSRID(ST_GeomFromGeoJSON(:geometryGeoJson),4326)"
                ),
                baseParams(projectId).addValue("geometryGeoJson", geometryJson)
        );
        Map<String, Object> resolved = new LinkedHashMap<>();
        putIfPresent(
                resolved, "objectType",
                normalizeObjectType(value(target, "objectType", "object_type"))
        );
        resolved.put("geometry", geometry);
        return rangeResult(resolved, "roadSection", matched);
    }

    private Map<String, Object> verifyBboxRange(
            String projectId,
            Map<String, Object> target,
            Object bbox
    ) {
        List<BigDecimal> values = bboxValues(bbox);
        if (values == null) {
            return result(
                    "INVALID",
                    "bbox 必须为 [minLng,minLat,maxLng,maxLat] 有限数值数组",
                    null, "roadSection", 0
            );
        }
        if (values.get(0).compareTo(values.get(2)) > 0
                || values.get(1).compareTo(values.get(3)) > 0) {
            return result(
                    "INVALID", "bbox 最小坐标不能大于最大坐标",
                    null, "roadSection", 0
            );
        }
        MapSqlParameterSource params = baseParams(projectId)
                .addValue("minLng", values.get(0))
                .addValue("minLat", values.get(1))
                .addValue("maxLng", values.get(2))
                .addValue("maxLat", values.get(3));
        int matched = count(
                spatialCountSql(
                        "ST_MakeEnvelope(:minLng,:minLat,:maxLng,:maxLat,4326)"
                ),
                params
        );
        Map<String, Object> resolved = new LinkedHashMap<>();
        putIfPresent(
                resolved, "objectType",
                normalizeObjectType(value(target, "objectType", "object_type"))
        );
        resolved.put("bbox", new ArrayList<BigDecimal>(values));
        return rangeResult(resolved, "roadSection", matched);
    }

    private Map<String, Object> rangeResult(
            Map<String, Object> resolved,
            String layer,
            int matched
    ) {
        if (matched < 1) {
            return result(
                    "NOT_FOUND",
                    "当前项目中未找到该范围内的业务数据",
                    resolved, layer, 0
            );
        }
        return result(
                "VALID", "来源范围已在当前项目验证",
                resolved, layer, matched
        );
    }

    private String objectSql(String objectType) {
        if ("ROAD_ROUTE".equals(objectType)) {
            return "select id,route_code,start_stake,end_stake from road_route "
                    + "where tenant_id=:tenantId and project_id=:projectId "
                    + "and id=:objectId and deleted=false";
        }
        if ("ROAD_SECTION".equals(objectType)) {
            return "select id,route_code,start_stake,end_stake from road_section_line "
                    + "where tenant_id=:tenantId and project_id=:projectId "
                    + "and id=:objectId and deleted=false";
        }
        if ("EVALUATION_UNIT".equals(objectType)) {
            return "select u.id,u.route_code,u.start_stake,u.end_stake "
                    + "from road_section_ledger u where u.tenant_id=:tenantId "
                    + "and u.id=:objectId and u.deleted=false "
                    + "and (u.project_id=:projectId or exists (select 1 "
                    + "from road_route r where r.tenant_id=u.tenant_id "
                    + "and r.id=u.route_id and r.project_id=:projectId "
                    + "and r.deleted=false))";
        }
        if ("DISEASE".equals(objectType)) {
            return "select id,route_code,start_stake,end_stake from disease_record "
                    + "where tenant_id=:tenantId and project_id=:projectId "
                    + "and id=:objectId and deleted=false";
        }
        if ("ASSESSMENT_RESULT".equals(objectType)) {
            return "select ar.id,ar.route_code,ar.start_stake,ar.end_stake "
                    + "from assessment_result ar where ar.tenant_id=:tenantId "
                    + "and ar.id=:objectId and ar.deleted=false "
                    + "and exists (select 1 from road_route r "
                    + "where r.tenant_id=ar.tenant_id "
                    + "and r.project_id=:projectId and r.deleted=false "
                    + "and (r.id=ar.route_id or "
                    + "(ar.route_id is null and r.route_code=ar.route_code)))";
        }
        return null;
    }

    private String spatialCountSql(String areaExpression) {
        return "select count(*) as matched_count from ("
                + "select r.id from road_route r where r.tenant_id=:tenantId "
                + "and r.project_id=:projectId and r.deleted=false "
                + "and r.geom is not null and ST_Intersects(r.geom,"
                + areaExpression + ") "
                + "union all select s.id from road_section_line s "
                + "where s.tenant_id=:tenantId and s.project_id=:projectId "
                + "and s.deleted=false and s.geom is not null "
                + "and ST_Intersects(s.geom," + areaExpression + ") "
                + "union all select d.id from disease_record d "
                + "where d.tenant_id=:tenantId and d.project_id=:projectId "
                + "and d.deleted=false and d.geom is not null "
                + "and ST_Intersects(d.geom," + areaExpression + ")"
                + ") matched";
    }

    private int count(String sql, MapSqlParameterSource params) {
        List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(
                sql, params
        );
        if (rows.isEmpty()) {
            return 0;
        }
        Object value = rowValue(rows.get(0), "matched_count", "matchedCount");
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    private MapSqlParameterSource baseParams(String projectId) {
        return new MapSqlParameterSource()
                .addValue("tenantId", TenantContextHolder.getTenantId())
                .addValue("projectId", projectId);
    }

    private Map<String, Object> result(
            String status,
            String reason,
            Map<String, Object> resolvedTarget,
            String recommendedLayer,
            int matchedCount
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bindingStatus", status);
        result.put("bindingReason", reason);
        result.put(
                "resolvedTarget",
                resolvedTarget == null
                        ? new LinkedHashMap<String, Object>()
                        : resolvedTarget
        );
        result.put("recommendedLayer", recommendedLayer);
        result.put("matchedCount", matchedCount);
        return result;
    }

    private String recommendedLayer(String objectType) {
        if ("ROAD_ROUTE".equals(objectType)) {
            return "roadRoute";
        }
        if ("ROAD_SECTION".equals(objectType)
                || "EVALUATION_UNIT".equals(objectType)) {
            return "roadSection";
        }
        if ("DISEASE".equals(objectType)) {
            return "disease";
        }
        if ("ASSESSMENT_RESULT".equals(objectType)) {
            return "assessment";
        }
        return null;
    }

    private String geometryError(Object geometry) {
        if (!(geometry instanceof Map)) {
            return "geometry 必须为 GeoJSON 对象";
        }
        Map<?, ?> map = (Map<?, ?>) geometry;
        String type = text(map.get("type"));
        if (!GEOJSON_TYPES.contains(type)) {
            return "geometry.type 不是支持的 GeoJSON 类型";
        }
        if ("GeometryCollection".equals(type)) {
            Object geometries = map.get("geometries");
            if (!(geometries instanceof List)
                    || ((List<?>) geometries).isEmpty()) {
                return "GeometryCollection.geometries 不能为空";
            }
            for (Object item : (List<?>) geometries) {
                String nestedError = geometryError(item);
                if (nestedError != null) {
                    return nestedError;
                }
            }
            return null;
        }
        Object coordinates = map.get("coordinates");
        if ("Point".equals(type)) {
            return validPosition(coordinates) ? null : "Point 坐标结构不合法";
        }
        if ("MultiPoint".equals(type)) {
            return validCollection(coordinates, "POSITION")
                    ? null : "MultiPoint 坐标结构不合法";
        }
        if ("LineString".equals(type)) {
            return validLineString(coordinates)
                    ? null : "LineString 至少需要两个合法坐标点";
        }
        if ("MultiLineString".equals(type)) {
            return validCollection(coordinates, "LINE")
                    ? null : "MultiLineString 坐标结构不合法";
        }
        if ("Polygon".equals(type)) {
            return validPolygon(coordinates)
                    ? null : "Polygon 线环必须闭合且至少包含四个坐标点";
        }
        if ("MultiPolygon".equals(type)) {
            return validCollection(coordinates, "POLYGON")
                    ? null : "MultiPolygon 坐标结构不合法";
        }
        return "geometry.coordinates 不合法";
    }

    private boolean validCollection(Object value, String childType) {
        if (!(value instanceof List) || ((List<?>) value).isEmpty()) {
            return false;
        }
        for (Object item : (List<?>) value) {
            boolean valid;
            if ("POSITION".equals(childType)) {
                valid = validPosition(item);
            } else if ("LINE".equals(childType)) {
                valid = validLineString(item);
            } else {
                valid = validPolygon(item);
            }
            if (!valid) {
                return false;
            }
        }
        return true;
    }

    private boolean validPosition(Object value) {
        if (!(value instanceof List) || ((List<?>) value).size() < 2) {
            return false;
        }
        for (Object item : (List<?>) value) {
            if (item instanceof List || decimal(item) == null) {
                return false;
            }
        }
        return true;
    }

    private boolean validLineString(Object value) {
        if (!(value instanceof List) || ((List<?>) value).size() < 2) {
            return false;
        }
        for (Object item : (List<?>) value) {
            if (!validPosition(item)) {
                return false;
            }
        }
        return true;
    }

    private boolean validPolygon(Object value) {
        if (!(value instanceof List) || ((List<?>) value).isEmpty()) {
            return false;
        }
        for (Object ring : (List<?>) value) {
            if (!(ring instanceof List) || ((List<?>) ring).size() < 4) {
                return false;
            }
            List<?> positions = (List<?>) ring;
            for (Object position : positions) {
                if (!validPosition(position)) {
                    return false;
                }
            }
            if (!samePosition(
                    positions.get(0),
                    positions.get(positions.size() - 1)
            )) {
                return false;
            }
        }
        return true;
    }

    private boolean samePosition(Object left, Object right) {
        if (!(left instanceof List) || !(right instanceof List)) {
            return false;
        }
        List<?> leftValues = (List<?>) left;
        List<?> rightValues = (List<?>) right;
        if (leftValues.size() != rightValues.size()) {
            return false;
        }
        for (int i = 0; i < leftValues.size(); i++) {
            BigDecimal leftNumber = decimal(leftValues.get(i));
            BigDecimal rightNumber = decimal(rightValues.get(i));
            if (leftNumber == null || rightNumber == null
                    || leftNumber.compareTo(rightNumber) != 0) {
                return false;
            }
        }
        return true;
    }

    private List<BigDecimal> bboxValues(Object bbox) {
        if (!(bbox instanceof List) || ((List<?>) bbox).size() != 4) {
            return null;
        }
        List<BigDecimal> result = new ArrayList<>();
        for (Object item : (List<?>) bbox) {
            BigDecimal value = decimal(item);
            if (value == null) {
                return null;
            }
            result.add(value);
        }
        return result;
    }

    private boolean hasOneStake(Map<String, Object> target) {
        return target.containsKey("startStake")
                || target.containsKey("start_stake")
                || target.containsKey("endStake")
                || target.containsKey("end_stake");
    }

    private boolean overlaps(
            BigDecimal leftStart,
            BigDecimal leftEnd,
            BigDecimal rightStart,
            BigDecimal rightEnd
    ) {
        return leftStart.compareTo(rightEnd) <= 0
                && leftEnd.compareTo(rightStart) >= 0;
    }

    private String normalizeObjectType(Object value) {
        String raw = upper(value).replace("-", "_").replace(" ", "_");
        if ("ROADROUTE".equals(raw) || "ROUTE".equals(raw)) {
            return "ROAD_ROUTE";
        }
        if ("ROADSECTION".equals(raw) || "SECTION".equals(raw)) {
            return "ROAD_SECTION";
        }
        if ("ASSESSMENT".equals(raw)) {
            return "ASSESSMENT_RESULT";
        }
        if ("DISEASE_RECORD".equals(raw)) {
            return "DISEASE";
        }
        if ("EVALUATIONUNIT".equals(raw)) {
            return "EVALUATION_UNIT";
        }
        return raw;
    }

    private BigDecimal decimal(Object value) {
        if (value == null || value instanceof Boolean) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Object value(Map<String, Object> map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String key : keys) {
            if (map.containsKey(key) && map.get(key) != null) {
                return map.get(key);
            }
        }
        return null;
    }

    private Object rowValue(Map<String, Object> map, String... keys) {
        Object direct = value(map, keys);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            for (String key : keys) {
                if (entry.getKey().equalsIgnoreCase(key)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null && (!(value instanceof String)
                || StringUtils.hasText((String) value))) {
            map.put(key, value);
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private String strictText(Object value) {
        if (!(value instanceof String)) {
            return null;
        }
        String text = ((String) value).trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private String upper(Object value) {
        return text(value) == null
                ? ""
                : text(value).toUpperCase(Locale.ROOT);
    }
}
