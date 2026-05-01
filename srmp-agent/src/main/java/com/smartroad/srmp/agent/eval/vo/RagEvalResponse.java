package com.smartroad.srmp.agent.eval.vo;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class RagEvalResponse {
    private Integer total = 0;
    private Integer passed = 0;
    private Double passRate = 0.0d;
    private Integer knowledgeToolCalledCount = 0;
    private Integer vectorUsedCount = 0;
    private Double avgTopScore;
    private List<RagEvalCaseResult> results = new ArrayList<>();
}
