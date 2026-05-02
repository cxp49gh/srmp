package com.smartroad.srmp.gis.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class MapRegionSolutionResponse {
    private String solutionType;
    private String title;
    private String markdown;
    private Map<String, Object> regionSummary;
    private Map<String, Object> qualityCheck;
    private Map<String, Object> templateMeta;
    /**
     * AI 调用结果元信息，用于前端判断本次方案是否真正由大模型生成。
     */
    private Map<String, Object> answerMeta;
    private List<Map<String, Object>> sourceSummaries;
    private Map<String, Object> trace;
}
