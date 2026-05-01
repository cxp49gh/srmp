package com.smartroad.srmp.agent.eval.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class RagEvalCase {
    private String id;
    private String question;
    private Map<String, Object> mapContext = new LinkedHashMap<>();

    /**
     * 原有硬关键词校验。
     */
    private List<String> expectedKeywords = new ArrayList<>();

    /**
     * Phase37.2：关键词组校验。
     * 每个 group 中命中任意一个词，即认为该业务知识点命中。
     *
     * 例如：
     * ["灌缝", "开槽灌缝", "封缝", "裂缝灌缝"]
     */
    private List<List<String>> expectedKeywordGroups = new ArrayList<>();

    /**
     * 普通关键词命中率阈值。
     * null 表示 1.0；0 表示关闭普通关键词强校验。
     */
    private Double minKeywordHitRatio;

    /**
     * 关键词组命中率阈值。
     * null 表示 1.0。
     */
    private Double minKeywordGroupHitRatio;

    private List<String> expectedSources = new ArrayList<>();
    private Map<String, Object> options = new LinkedHashMap<>();
}
