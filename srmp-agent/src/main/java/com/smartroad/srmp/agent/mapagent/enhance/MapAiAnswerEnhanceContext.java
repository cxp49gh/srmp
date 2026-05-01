package com.smartroad.srmp.agent.mapagent.enhance;

import com.smartroad.srmp.agent.knowledge.vo.AiKnowledgeSearchHit;
import com.smartroad.srmp.agent.mapagent.dto.MapAiContext;
import com.smartroad.srmp.agent.tool.AiToolResult;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase37.5：一张图 AI 回答增强上下文。
 */
@Data
public class MapAiAnswerEnhanceContext {
    private String answer;
    private String userQuestion;
    private MapAiContext mapContext;
    private List<AiKnowledgeSearchHit> knowledgeSources = new ArrayList<>();
    private List<AiToolResult> toolResults = new ArrayList<>();

    public static MapAiAnswerEnhanceContext of(String answer,
                                               String userQuestion,
                                               MapAiContext mapContext,
                                               List<AiKnowledgeSearchHit> knowledgeSources,
                                               List<AiToolResult> toolResults) {
        MapAiAnswerEnhanceContext context = new MapAiAnswerEnhanceContext();
        context.setAnswer(answer);
        context.setUserQuestion(userQuestion);
        context.setMapContext(mapContext);
        if (knowledgeSources != null) {
            context.setKnowledgeSources(knowledgeSources);
        }
        if (toolResults != null) {
            context.setToolResults(toolResults);
        }
        return context;
    }
}
