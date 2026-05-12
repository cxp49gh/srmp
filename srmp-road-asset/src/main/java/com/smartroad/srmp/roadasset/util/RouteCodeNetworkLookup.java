package com.smartroad.srmp.roadasset.util;

import com.smartroad.srmp.roadasset.dto.RouteCodeIdRow;
import com.smartroad.srmp.roadasset.dto.RouteNetworkMatch;
import com.smartroad.srmp.roadasset.mapper.RoadRouteMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 路线编码与路网 {@code road_route.route_code} 对齐：去空白、大小写不敏感；支持剥末尾上下行后缀后再匹配，
 * 命中后返回库内规范 {@code route_code} 与 {@code id}。
 */
public final class RouteCodeNetworkLookup {

    private static final int ROUTE_CODE_IN_CHUNK = 400;

    private RouteCodeNetworkLookup() {
    }

    /**
     * 与 XML 中路网匹配条件一致：去首尾空白后按 Unicode 小写比较（{@code lower(trim(...))}），
     * 避免 Shapefile 与库内 {@code route_code} 仅大小写/空格不同导致整批 {@code route_id} 为空。
     */
    public static String normalizeRouteCodeKey(String routeCode) {
        if (routeCode == null) {
            return "";
        }
        return routeCode.trim().toLowerCase(Locale.ROOT);
    }

    /** 原文 + 至多两次「剥末尾上下行后缀」，保持顺序、去重 */
    public static List<String> expandRouteCodeLookupKeys(String routeCode) {
        List<String> keys = new ArrayList<>(3);
        String t = routeCode == null ? "" : routeCode.trim();
        if (!t.isEmpty()) {
            keys.add(t);
        }
        String s1 = stripOneLevelDirectionSuffix(t);
        if (!s1.isEmpty() && !s1.equals(t)) {
            keys.add(s1);
        }
        String s2 = stripOneLevelDirectionSuffix(s1);
        if (!s2.isEmpty() && !s2.equals(s1) && !keys.contains(s2)) {
            keys.add(s2);
        }
        return keys;
    }

    /**
     * 剥一层末尾上下行后缀：优先 {@code AB}，否则单独末尾 {@code A}/{@code B}（与「上行/下行」单字母习惯一致）。
     */
    public static String stripOneLevelDirectionSuffix(String routeCode) {
        if (routeCode == null) {
            return "";
        }
        String s = routeCode.trim();
        int n = s.length();
        if (n < 2) {
            return s;
        }
        String u = s.toUpperCase(Locale.ROOT);
        if (u.endsWith("AB")) {
            return s.substring(0, n - 2).trim();
        }
        if (u.endsWith("A") || u.endsWith("B")) {
            return s.substring(0, n - 1).trim();
        }
        return s;
    }

    /** 反复剥末尾 A/B/AB，得到与路网 {@code route_code} 常用形态一致的编码（未命中路网时亦可作入库参考） */
    public static String stripAllDirectionSuffixes(String routeCode) {
        String s = routeCode == null ? "" : routeCode.trim();
        String t;
        while (!(t = stripOneLevelDirectionSuffix(s)).equals(s)) {
            s = t;
        }
        return s;
    }

    /**
     * 按导入侧编码解析路网：依次尝试原文与去后缀候选，命中则返回库内 {@code id} 与规范 {@code route_code}；
     * 库内查找按编码匹配，不以 deleted 字段过滤（同编码多轨时优先 {@code updated_at} 最新一条）。
     */
    public static RouteNetworkMatch lookup(RoadRouteMapper mapper, String tenantId, String importRouteCode) {
        for (String candidate : expandRouteCodeLookupKeys(importRouteCode)) {
            if (candidate.isEmpty()) {
                continue;
            }
            RouteCodeIdRow row = mapper.selectRowByTenantAndRouteCode(tenantId, candidate);
            if (row != null && row.getId() != null) {
                return new RouteNetworkMatch(row.getId(), row.getRouteCode());
            }
        }
        return null;
    }

    /**
     * 批量解析：对多个导入编码各自按展开顺序匹配路网，返回「导入原文 → 命中结果」；
     * 未命中则 map 中不含该 key。
     */
    public static Map<String, RouteNetworkMatch> resolveBatch(RoadRouteMapper mapper, String tenantId,
                                                              Collection<String> importRouteCodes) {
        Map<String, RouteNetworkMatch> out = new LinkedHashMap<>();
        if (importRouteCodes == null || importRouteCodes.isEmpty()) {
            return out;
        }
        Set<String> originals = new LinkedHashSet<>();
        for (String c : importRouteCodes) {
            if (c != null && !c.trim().isEmpty()) {
                originals.add(c.trim());
            }
        }
        Set<String> allCandidates = new LinkedHashSet<>();
        Map<String, List<String>> origToCandidates = new LinkedHashMap<>();
        for (String orig : originals) {
            List<String> keys = expandRouteCodeLookupKeys(orig);
            origToCandidates.put(orig, keys);
            allCandidates.addAll(keys);
        }
        List<String> candList = new ArrayList<>(allCandidates);
        // key = normalizeRouteCodeKey(...)，与 SQL lower(trim(route_code)) 一致
        Map<String, RouteCodeIdRow> dbByNormalizedKey = new HashMap<>();
        for (int i = 0; i < candList.size(); i += ROUTE_CODE_IN_CHUNK) {
            int end = Math.min(i + ROUTE_CODE_IN_CHUNK, candList.size());
            List<RouteCodeIdRow> rows = mapper.selectIdsByRouteCodes(tenantId, candList.subList(i, end));
            for (RouteCodeIdRow r : rows) {
                if (r.getRouteCode() == null || r.getRouteCode().trim().isEmpty()) {
                    continue;
                }
                String nk = normalizeRouteCodeKey(r.getRouteCode());
                dbByNormalizedKey.putIfAbsent(nk, r);
            }
        }
        for (String orig : originals) {
            for (String cand : origToCandidates.get(orig)) {
                if (cand.isEmpty()) {
                    continue;
                }
                RouteCodeIdRow hit = dbByNormalizedKey.get(normalizeRouteCodeKey(cand));
                if (hit != null && hit.getId() != null) {
                    out.put(orig, new RouteNetworkMatch(hit.getId(), hit.getRouteCode()));
                    break;
                }
            }
        }
        return out;
    }
}
