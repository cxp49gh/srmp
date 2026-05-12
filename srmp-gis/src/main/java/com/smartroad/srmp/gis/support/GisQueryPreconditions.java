package com.smartroad.srmp.gis.support;

import com.smartroad.srmp.common.exception.BizException;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 一张图 GIS 查询约束：业务图层与统计必须带数据管理项目 id，避免未选项目时拉全租户数据。
 */
public final class GisQueryPreconditions {

    private GisQueryPreconditions() {}

    public static void requireProjectId(String projectId) {
        if (!StringUtils.hasText(projectId)) {
            throw new BizException("请先选择数据管理项目");
        }
    }

    @SuppressWarnings("unchecked")
    public static String projectIdFromFlatOrNested(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        Object direct = body.get("projectId");
        if (direct != null) {
            return String.valueOf(direct).trim();
        }
        Object nested = body.get("query");
        if (nested instanceof Map) {
            Object v = ((Map<String, Object>) nested).get("projectId");
            return v == null ? null : String.valueOf(v).trim();
        }
        return null;
    }
}
