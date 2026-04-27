package com.smartroad.srmp.agent.map;

import java.util.Map;

public interface MapObjectContextService {
    Map<String, Object> getObjectDetail(String objectType, String objectId, String routeCode, Integer year);
}
