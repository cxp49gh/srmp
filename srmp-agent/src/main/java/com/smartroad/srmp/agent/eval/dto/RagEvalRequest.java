package com.smartroad.srmp.agent.eval.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class RagEvalRequest {
    private String tenantId;
    private List<RagEvalCase> cases = new ArrayList<>();
    private Integer topK = 5;
    private Boolean requireVectorUsed = false;
}
