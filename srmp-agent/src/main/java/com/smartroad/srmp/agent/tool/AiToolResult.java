package com.smartroad.srmp.agent.tool;

import lombok.Data;

@Data
public class AiToolResult {
    private String toolName;
    private boolean success;
    private String summary;
    private Object data;
    private Integer count;
    private String errorMessage;
    private Long costMs;

    public static AiToolResult success(String toolName, String summary, Object data, Integer count, long costMs) {
        AiToolResult result = new AiToolResult();
        result.setToolName(toolName);
        result.setSuccess(true);
        result.setSummary(summary);
        result.setData(data);
        result.setCount(count);
        result.setCostMs(costMs);
        return result;
    }

    public static AiToolResult failed(String toolName, String message, long costMs) {
        AiToolResult result = new AiToolResult();
        result.setToolName(toolName);
        result.setSuccess(false);
        result.setSummary(message);
        result.setErrorMessage(message);
        result.setCostMs(costMs);
        return result;
    }
}
