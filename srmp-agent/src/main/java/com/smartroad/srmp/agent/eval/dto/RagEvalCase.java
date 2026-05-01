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
    private List<String> expectedKeywords = new ArrayList<>();
    private List<String> expectedSources = new ArrayList<>();
    private Map<String, Object> options = new LinkedHashMap<>();
}
