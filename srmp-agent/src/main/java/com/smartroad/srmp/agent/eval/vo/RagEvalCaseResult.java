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
    private Integer keywordMatchedCount = 0;
    private Integer keywordTotal = 0;
    private List<String> missingKeywords = new ArrayList<>();
    private Boolean sourceMatched = false;
    private Boolean knowledgeToolCalled = false;
    private Boolean vectorUsed = false;
    private String searchMode;
    private Integer sourceCount = 0;
    private Double topScore;
    private List<Map<String, Object>> sources = new ArrayList<>();
    private List<String> errors = new ArrayList<>();
}
