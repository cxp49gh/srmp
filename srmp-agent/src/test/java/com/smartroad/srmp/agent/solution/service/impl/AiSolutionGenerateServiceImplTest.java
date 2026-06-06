package com.smartroad.srmp.agent.solution.service.impl;

import com.smartroad.srmp.agent.solution.dto.AiSolutionGenerateRequest;
import com.smartroad.srmp.agent.solution.template.SolutionTemplateContext;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import static org.junit.Assert.assertEquals;

public class AiSolutionGenerateServiceImplTest {

    @Test
    public void routeReportTemplateContextUsesMapObjectOrigin() throws Exception {
        AiSolutionGenerateServiceImpl service = new AiSolutionGenerateServiceImpl();
        AiSolutionGenerateRequest request = new AiSolutionGenerateRequest();
        request.setRouteCode("Y016140727");
        request.setSolutionType("ROUTE_REPORT");

        Method method = AiSolutionGenerateServiceImpl.class.getDeclaredMethod(
                "buildTemplateContext",
                String.class,
                AiSolutionGenerateRequest.class,
                String.class,
                java.util.Map.class,
                java.util.List.class
        );
        method.setAccessible(true);

        SolutionTemplateContext context = (SolutionTemplateContext) method.invoke(
                service,
                "default",
                request,
                "Y016140727 路线养护报告",
                new LinkedHashMap<String, Object>(),
                new ArrayList<Object>()
        );

        assertEquals("MAP_OBJECT", context.getOriginType());
        assertEquals("ROAD_ROUTE", context.getObjectType());
        assertEquals("ROUTE_REPORT", context.getSolutionType());
    }
}
