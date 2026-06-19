package com.smartroad.srmp.gis.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class SourceBindingVerifyRequest {
    private String projectId;
    private String bindingType;
    private Map<String, Object> mapTarget = new LinkedHashMap<>();
}
