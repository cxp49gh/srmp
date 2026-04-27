package com.smartroad.srmp.agent.map;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class MapObjectContext implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean present;
    private String objectId;
    private String objectType;
    private String routeCode;
    private Integer year;
    private Map<String, Object> detail;
    private String markdown;
}
