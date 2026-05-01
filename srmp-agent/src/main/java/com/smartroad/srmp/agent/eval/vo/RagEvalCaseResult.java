package com.smartroad.srmp.agent.eval.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class RagEvalCaseResult {
    private String id;
    private String question;
    private Boolean passed = false;

    private String answer;
    private String answerPreview;

    private Integer keywordMatchedCount = 0;
    private Integer keywordTotal = 0;
    private Double keywordHitRatio = 0.0d;
    private List<String> matchedKeywords = new ArrayList<>();
    private List<String> missingKeywords = new ArrayList<>();

    private Integer keywordGroupMatchedCount = 0;
    private Integer keywordGroupTotal = 0;
    private Double keywordGroupHitRatio = 0.0d;
    private List<List<String>> matchedKeywordGroups = new ArrayList<>();
    private List<List<String>> missingKeywordGroups = new ArrayList<>();

    private Boolean sourceMatched = false;
    private List<String> expectedSources = new ArrayList<>();
    private List<String> actualSourceTitles = new ArrayList<>();

    private Boolean knowledgeToolCalled = false;
    private Boolean vectorUsed = false;
    private String searchMode;
    private Integer sourceCount = 0;
    private Double topScore;
    private List<Map<String, Object>> sources = new ArrayList<>();

    /**
     * 结构化失败原因，便于脚本和前端分类统计。
     */
    private List<String> failReasons = new ArrayList<>();

    /**
     * 兼容旧脚本/前端字段。
     */
    private List<String> errors = new ArrayList<>();
}
