package com.smartroad.srmp.agent.mapagent.enhance;

import com.smartroad.srmp.agent.mapagent.service.impl.AssessmentResultAdviceEnhancer;
import com.smartroad.srmp.agent.mapagent.service.impl.MapAiAnswerPolisher;
import com.smartroad.srmp.agent.mapagent.service.impl.MapObjectDiseaseAdviceEnhancer;
import com.smartroad.srmp.agent.mapagent.service.impl.RoadAssetAdviceEnhancer;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Phase37.5：一张图 AI 回答增强策略注册器。
 *
 * 当前阶段先把已有 enhancer 收口成统一调用入口：
 * 1. DISEASE 病害对象增强；
 * 2. ASSESSMENT_RESULT 评定结果对象增强；
 * 3. ROAD_ROUTE / ROAD_SECTION 路线/路段对象增强；
 * 4. 最后执行回答清理器。
 *
 * 后续可逐步将每个 enhancer 改造为 MapAiAnswerEnhancer 实现类。
 */
@Component
public class MapAiAnswerEnhancerRegistry {

    @Resource
    private MapObjectDiseaseAdviceEnhancer mapObjectDiseaseAdviceEnhancer;

    @Resource
    private AssessmentResultAdviceEnhancer assessmentResultAdviceEnhancer;

    @Resource
    private RoadAssetAdviceEnhancer roadAssetAdviceEnhancer;

    @Resource
    private MapAiAnswerPolisher mapAiAnswerPolisher;

    public String enhance(String answer, MapAiAnswerEnhanceContext context) {
        String value = answer == null ? "" : answer;

        value = mapObjectDiseaseAdviceEnhancer.enhance(
                value,
                context.getUserQuestion(),
                context.getMapContext(),
                context.getKnowledgeSources(),
                context.getToolResults()
        );

        value = assessmentResultAdviceEnhancer.enhance(
                value,
                context.getUserQuestion(),
                context.getMapContext(),
                context.getKnowledgeSources(),
                context.getToolResults()
        );

        value = roadAssetAdviceEnhancer.enhance(
                value,
                context.getUserQuestion(),
                context.getMapContext(),
                context.getKnowledgeSources(),
                context.getToolResults()
        );

        value = mapAiAnswerPolisher.polish(value);
        return value;
    }
}
