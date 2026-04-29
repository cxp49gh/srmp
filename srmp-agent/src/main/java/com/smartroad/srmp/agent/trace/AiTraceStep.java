package com.smartroad.srmp.agent.trace;
import java.util.Map;
import lombok.Data;
@Data
public class AiTraceStep {
    private String name;
    private String label;
    private String status;
    private Long costMs;
    private Integer count;
    private String error;
    private Map<String, Object> data;
}
