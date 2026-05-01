package com.smartroad.srmp.agent.mapagent.plan;

import com.smartroad.srmp.agent.mapagent.dto.MapAiContext;
import com.smartroad.srmp.agent.mapagent.dto.MapAiIntent;

import java.util.List;
import java.util.Map;

/**
 * Phase37.5：一张图 AI 工具规划接口。
 */
public interface MapAiToolPlanner {
    List<String> plan(MapAiIntent intent, MapAiContext context, Map<String, Object> options, String message);
}
