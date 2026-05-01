package com.smartroad.srmp.agent.mapagent.enhance;

/**
 * Phase37.5：一张图 AI 回答增强策略接口。
 *
 * 后续新增对象类型时，不应继续在 MapAiAgentServiceImpl 中堆 if/else，
 * 而是新增一个 enhancer 并注册到 MapAiAnswerEnhancerRegistry。
 */
public interface MapAiAnswerEnhancer {

    /**
     * 策略顺序，越小越先执行。
     */
    int order();

    /**
     * 是否支持当前上下文。
     */
    boolean supports(MapAiAnswerEnhanceContext context);

    /**
     * 增强回答。
     */
    String enhance(MapAiAnswerEnhanceContext context);
}
