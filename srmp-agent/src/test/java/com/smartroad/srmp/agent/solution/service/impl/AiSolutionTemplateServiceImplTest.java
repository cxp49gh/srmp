package com.smartroad.srmp.agent.solution.service.impl;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;

public class AiSolutionTemplateServiceImplTest {

    @Test
    public void routeReportDefaultOriginUsesMapObject() throws Exception {
        AiSolutionTemplateServiceImpl service = new AiSolutionTemplateServiceImpl();

        Method method = AiSolutionTemplateServiceImpl.class
                .getDeclaredMethod("defaultOriginTypeForSolution", String.class);
        method.setAccessible(true);

        assertEquals("MAP_OBJECT", method.invoke(service, "ROUTE_REPORT"));
        assertEquals("MAP_OBJECT", method.invoke(service, "ROAD_ASSESSMENT_REPORT"));
    }
}
