package com.smartroad.srmp.agent.outline.service.impl;

import com.smartroad.srmp.agent.outline.service.OutlineGovernanceService;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class OutlineGovernanceServiceImpl implements OutlineGovernanceService {

    private static final int SHORT_CONTENT_THRESHOLD = 200;

    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    public Map<String, Object> dashboard() {
        String tenantId = TenantContextHolder.getTenantId();
        MapSqlParameterSource base = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("sourceType", "OUTLINE");

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("tenantId", tenantId);
        result.put("summary", summary(base));
        result.put("coverage", coverage(base));
        List<Map<String, Object>> lowQualityDocuments = lowQualityDocuments(base);
        result.put("lowQualityDocuments", lowQualityDocuments);
        result.put("citationHeat", citationHeat(base));
        result.put("feedbackSummary", feedbackSummary(base));
        result.put("tasks", governanceTasks(base, lowQualityDocuments));
        return result;
    }

    private Map<String, Object> summary(MapSqlParameterSource base) {
        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("documentCount", queryLong("select count(*) from ai_knowledge_document where tenant_id=:tenantId and source_type=:sourceType and status='ACTIVE'", base));
        summary.put("inactiveDocumentCount", queryLong("select count(*) from ai_knowledge_document where tenant_id=:tenantId and source_type=:sourceType and status <> 'ACTIVE'", base));
        summary.put("chunkCount", queryLong("select count(*) from ai_knowledge_chunk where tenant_id=:tenantId and source_type=:sourceType", base));
        summary.put("embeddedChunkCount", queryLong("select count(*) from ai_knowledge_chunk where tenant_id=:tenantId and source_type=:sourceType and embedding is not null", base));
        summary.put("pendingEmbeddingChunkCount", queryLong("select count(*) from ai_knowledge_chunk where tenant_id=:tenantId and source_type=:sourceType and embedding is null", base));
        summary.put("zeroChunkDocumentCount", queryLong(
                "select count(*) from ai_knowledge_document d where d.tenant_id=:tenantId and d.source_type=:sourceType and d.status='ACTIVE' " +
                        "and not exists (select 1 from ai_knowledge_chunk c where c.tenant_id=d.tenant_id and c.document_id=d.id)",
                base));
        summary.put("staleDocumentCount", queryLong("select count(*) from ai_knowledge_document where tenant_id=:tenantId and source_type=:sourceType and status='ACTIVE' and updated_at < now() - interval '180 days'", base));
        summary.put("zeroReferenceDocumentCount", queryLong(
                "select count(*) from ai_knowledge_document d " +
                        "left join (" + referenceSubquery("count(*) reference_count") + ") r on r.source_id=d.source_id " +
                        "where d.tenant_id=:tenantId and d.source_type=:sourceType and d.status='ACTIVE' and coalesce(r.reference_count,0)=0",
                base));
        return summary;
    }

    private List<Map<String, Object>> coverage(MapSqlParameterSource base) {
        List<Topic> topics = Arrays.asList(
                topic("病害类型", "裂缝", "crack", "坑槽", "车辙", "松散", "沉陷", "泛油"),
                topic("评定指标", "MQI", "PQI", "PCI", "RQI", "RDI", "SRI", "PSSI", "技术状况"),
                topic("处置工艺", "灌缝", "铣刨", "罩面", "修补", "封层", "微表处", "养护工艺"),
                topic("路面类型", "沥青", "水泥", "路面", "桥面", "隧道", "基层"),
                topic("方案模板", "方案", "模板", "处治建议", "养护建议", "施工组织")
        );
        long total = queryLong("select count(*) from ai_knowledge_document where tenant_id=:tenantId and source_type=:sourceType and status='ACTIVE'", base);
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Topic topic : topics) {
            MapSqlParameterSource params = copy(base);
            StringBuilder condition = new StringBuilder();
            for (int i = 0; i < topic.keywords.size(); i++) {
                if (i > 0) condition.append(" or ");
                String key = "kw" + i;
                condition.append("lower(coalesce(d.title,'')) like :").append(key)
                        .append(" or lower(coalesce(c.content,'')) like :").append(key);
                params.addValue(key, "%" + topic.keywords.get(i).toLowerCase(Locale.ROOT) + "%");
            }
            long count = queryLong(
                    "select count(distinct d.id) from ai_knowledge_document d " +
                            "left join ai_knowledge_chunk c on c.tenant_id=d.tenant_id and c.document_id=d.id " +
                            "where d.tenant_id=:tenantId and d.source_type=:sourceType and d.status='ACTIVE' and (" + condition + ")",
                    params);
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("dimension", topic.dimension);
            row.put("keywords", topic.keywords);
            row.put("documentCount", count);
            row.put("coverageRate", total <= 0 ? 0 : Math.round(count * 10000.0 / total) / 100.0);
            row.put("status", count <= 0 ? "MISSING" : count < 3 ? "WEAK" : "OK");
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> lowQualityDocuments(MapSqlParameterSource base) {
        MapSqlParameterSource params = copy(base).addValue("limit", 500);
        List<Map<String, Object>> docs = queryList(
                "select d.id, d.source_id, d.title, d.status, d.updated_at, " +
                        "count(c.id) chunk_count, coalesce(sum(length(c.content)),0) content_chars, count(c.embedding) embedded_chunk_count, " +
                        "coalesce(max(r.reference_count),0) reference_count, max(r.last_referenced_at) last_referenced_at, " +
                        "(d.updated_at < now() - interval '180 days') stale " +
                        "from ai_knowledge_document d " +
                        "left join ai_knowledge_chunk c on c.tenant_id=d.tenant_id and c.document_id=d.id " +
                        "left join (" + referenceSubquery("count(*) reference_count, max(s.created_at) last_referenced_at") + ") r on r.source_id=d.source_id " +
                        "where d.tenant_id=:tenantId and d.source_type=:sourceType " +
                        "group by d.id, d.source_id, d.title, d.status, d.updated_at " +
                        "order by d.updated_at desc limit :limit",
                params);
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> doc : docs) {
            List<String> issues = qualityIssues(doc);
            if (issues.isEmpty()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<String, Object>(doc);
            row.put("issues", issues);
            row.put("qualityLevel", qualityLevel(issues));
            result.add(row);
            if (result.size() >= 100) {
                break;
            }
        }
        return result;
    }

    private List<Map<String, Object>> citationHeat(MapSqlParameterSource base) {
        return queryList(
                "select coalesce(d.source_id, s.source_id) source_id, coalesce(d.title, s.source_title) source_title, " +
                        "max(coalesce(d.metadata ->> 'sourceUrl', s.source_url)) source_url, count(*) reference_count, max(s.created_at) last_referenced_at " +
                        "from ai_solution_source s " +
                        "left join ai_knowledge_chunk c on c.tenant_id=s.tenant_id and c.id=s.source_id and c.source_type='OUTLINE' " +
                        "left join ai_knowledge_document d on d.tenant_id=c.tenant_id and d.id=c.document_id " +
                        "where s.tenant_id=:tenantId and (s.source_type='OUTLINE' or c.id is not null) " +
                        "group by coalesce(d.source_id, s.source_id), coalesce(d.title, s.source_title) " +
                        "order by count(*) desc, max(s.created_at) desc limit 20",
                base);
    }

    private List<Map<String, Object>> feedbackSummary(MapSqlParameterSource base) {
        return queryList(
                "select feedback_type, count(*) feedback_count, max(created_at) last_feedback_at " +
                        "from ai_knowledge_feedback where tenant_id=:tenantId group by feedback_type order by count(*) desc",
                base);
    }

    private List<Map<String, Object>> governanceTasks(MapSqlParameterSource base, List<Map<String, Object>> lowQualityDocuments) {
        List<Map<String, Object>> tasks = new ArrayList<Map<String, Object>>();
        long pendingVectors = queryLong("select count(*) from ai_knowledge_chunk where tenant_id=:tenantId and source_type=:sourceType and embedding is null", base);
        if (pendingVectors > 0) {
            tasks.add(task("P0", "补齐 OUTLINE 向量", "仍有 " + pendingVectors + " 个 chunk 未生成 embedding", "前往 Outline 同步入库页执行补向量"));
        }
        long failedTasks = queryLong("select count(*) from outline_sync_task where tenant_id=:tenantId and status in ('FAILED','PARTIAL_SUCCESS','DRY_RUN_PARTIAL')", base);
        if (failedTasks > 0) {
            tasks.add(task("P0", "处理同步失败任务", "存在 " + failedTasks + " 个失败或部分失败同步任务", "前往 Outline 同步任务页查看明细并重试"));
        }
        if (!lowQualityDocuments.isEmpty()) {
            tasks.add(task("P1", "治理低质量文档", "发现 " + lowQualityDocuments.size() + " 篇空文档、过短、过期、零引用或默认文档", "在 Outline 中补充内容或下线无效文档后重新同步"));
        }
        long missingFeedback = queryLong("select count(*) from ai_knowledge_feedback where tenant_id=:tenantId and feedback_type='MISSING_KNOWLEDGE'", base);
        if (missingFeedback > 0) {
            tasks.add(task("P1", "处理知识缺失反馈", "用户提交了 " + missingFeedback + " 条知识缺失反馈", "进入 AI 知识反馈页定位缺失主题并补充 Outline 文档"));
        }
        return tasks;
    }

    private List<String> qualityIssues(Map<String, Object> doc) {
        List<String> issues = new ArrayList<String>();
        long chunks = number(doc.get("chunk_count"));
        long chars = number(doc.get("content_chars"));
        long refs = number(doc.get("reference_count"));
        String title = string(doc.get("title")).toLowerCase(Locale.ROOT);
        if (chunks <= 0) issues.add("空文档");
        if (chars > 0 && chars < SHORT_CONTENT_THRESHOLD) issues.add("内容过短");
        if (isDefaultOutlineTitle(title)) issues.add("默认系统文档");
        if (Boolean.TRUE.equals(doc.get("stale"))) issues.add("长期未更新");
        if (refs <= 0) issues.add("零引用");
        return issues;
    }

    private boolean isDefaultOutlineTitle(String title) {
        return title.contains("what is outline")
                || title.contains("getting started")
                || title.contains("our editor")
                || title.contains("integrations")
                || title.contains("welcome to outline");
    }

    private String qualityLevel(List<String> issues) {
        if (issues.contains("空文档") || issues.contains("默认系统文档")) return "HIGH";
        if (issues.size() >= 2) return "MEDIUM";
        return "LOW";
    }

    private Map<String, Object> task(String priority, String title, String description, String action) {
        Map<String, Object> task = new LinkedHashMap<String, Object>();
        task.put("priority", priority);
        task.put("title", title);
        task.put("description", description);
        task.put("action", action);
        return task;
    }

    private String referenceSubquery(String selectTail) {
        return "select coalesce(d.source_id, s.source_id) source_id, " + selectTail + " " +
                "from ai_solution_source s " +
                "left join ai_knowledge_chunk c on c.tenant_id=s.tenant_id and c.id=s.source_id and c.source_type='OUTLINE' " +
                "left join ai_knowledge_document d on d.tenant_id=c.tenant_id and d.id=c.document_id " +
                "where s.tenant_id=:tenantId and (s.source_type='OUTLINE' or c.id is not null) " +
                "group by coalesce(d.source_id, s.source_id)";
    }

    private long queryLong(String sql, MapSqlParameterSource params) {
        try {
            Long value = namedParameterJdbcTemplate.queryForObject(sql, params, Long.class);
            return value == null ? 0 : value;
        } catch (Exception e) {
            return 0;
        }
    }

    private List<Map<String, Object>> queryList(String sql, MapSqlParameterSource params) {
        try {
            return namedParameterJdbcTemplate.queryForList(sql, params);
        } catch (Exception e) {
            return new ArrayList<Map<String, Object>>();
        }
    }

    private MapSqlParameterSource copy(MapSqlParameterSource source) {
        MapSqlParameterSource target = new MapSqlParameterSource();
        for (Map.Entry<String, Object> entry : source.getValues().entrySet()) {
            target.addValue(entry.getKey(), entry.getValue());
        }
        return target;
    }

    private Topic topic(String dimension, String... keywords) {
        return new Topic(dimension, Arrays.asList(keywords));
    }

    private long number(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static class Topic {
        private final String dimension;
        private final List<String> keywords;

        private Topic(String dimension, List<String> keywords) {
            this.dimension = dimension;
            this.keywords = keywords;
        }
    }
}
