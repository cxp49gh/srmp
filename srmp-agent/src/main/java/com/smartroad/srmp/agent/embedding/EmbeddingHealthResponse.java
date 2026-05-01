package com.smartroad.srmp.agent.embedding;

import lombok.Data;

@Data
public class EmbeddingHealthResponse {
    private String provider;
    private String model;
    private String endpoint;
    private Integer expectedDimensions;
    private Integer actualDimensions;
    private Boolean available = false;
    private Long costMs = 0L;
    private String errorType;
    private String errorMessage;
    private String suggestion;
}
