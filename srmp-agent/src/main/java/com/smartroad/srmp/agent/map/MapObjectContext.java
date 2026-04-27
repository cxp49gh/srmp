package com.smartroad.srmp.agent.map;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class MapObjectContext implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String objectId;
    private String objectType;
    private String routeCode;
    private Integer year;
    private Double startStake;
    private Double endStake;
    private Map<String, Object> details;
}
