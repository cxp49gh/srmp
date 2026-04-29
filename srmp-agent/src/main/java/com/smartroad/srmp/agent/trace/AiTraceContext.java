package com.smartroad.srmp.agent.trace;

import lombok.Data;
import java.text.SimpleDateFormat;
import java.util.*;

@Data
public class AiTraceContext {
    private String traceId;
    private String requestType;
    private String message;
    private String mode;
    private String status = "SUCCESS";
    private Boolean fallback = false;
    private String error;
    private Long startMs;
    private Long totalCostMs;
    private List<AiTraceStep> steps = new ArrayList<>();

    public static AiTraceContext start(String requestType, String message) {
        AiTraceContext context = new AiTraceContext();
        String time = new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date());
        context.traceId = "ai-" + time + "-" + UUID.randomUUID().toString().substring(0, 8);
        context.requestType = requestType;
        context.message = message;
        context.startMs = System.currentTimeMillis();
        return context;
    }
    public StepTimer step(String name, String label) { return new StepTimer(this, name, label); }
    public void finish() { this.totalCostMs = System.currentTimeMillis() - this.startMs; }
    public Map<String, Object> toMap() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("traceId", traceId); data.put("requestType", requestType); data.put("mode", mode);
        data.put("status", status); data.put("fallback", fallback); data.put("error", error);
        data.put("totalCostMs", totalCostMs); data.put("steps", steps); return data;
    }
    void addStep(AiTraceStep step) { steps.add(step); }
    public static class StepTimer {
        private final AiTraceContext context; private final String name; private final String label; private final long startMs;
        StepTimer(AiTraceContext context, String name, String label) { this.context=context; this.name=name; this.label=label; this.startMs=System.currentTimeMillis(); }
        public void success() { success(null, null); }
        public void success(Integer count) { success(count, null); }
        public void success(Integer count, Map<String, Object> data) { finish("SUCCESS", count, null, data); }
        public void skipped() { skipped(null); }
        public void skipped(Map<String, Object> data) { finish("SKIPPED", 0, null, data); }
        public void failed(Throwable error) { failed(error, null); }
        public void failed(Throwable error, Map<String, Object> data) { finish("FAILED", null, error == null ? null : error.getMessage(), data); }
        public void timeout(Throwable error) { timeout(error, null); }
        public void timeout(Throwable error, Map<String, Object> data) { finish("TIMEOUT", null, error == null ? null : error.getMessage(), data); }
        private void finish(String status, Integer count, String error, Map<String, Object> data) {
            AiTraceStep step = new AiTraceStep(); step.setName(name); step.setLabel(label); step.setStatus(status);
            step.setCostMs(System.currentTimeMillis() - startMs); step.setCount(count); step.setError(error); step.setData(data); context.addStep(step);
        }
    }
}
