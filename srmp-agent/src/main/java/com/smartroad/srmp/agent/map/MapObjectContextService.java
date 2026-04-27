package com.smartroad.srmp.agent.map;

import java.util.Map;

public interface MapObjectContextService {
    MapObjectContext resolve(Map context);

    Map<String, Object> getObjectDetail(String objectType, String objectId, String routeCode, Integer year);
}
