package com.smartroad.srmp.agent.orchestrator.controller;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AgentOrchestratorOpsControllerLiveTraceTest {

    @Test
    public void liveTraceRuntimePathEncodesTraceId() {
        AgentOrchestratorOpsController controller = new AgentOrchestratorOpsController();

        assertEquals(
                "/api/srmp/langgraph/trace/live/web-lg-1-a%20b",
                controller.liveTraceRuntimePath("web-lg-1-a b")
        );
    }

    @Test
    public void replayRuntimePathEncodesAdaptiveMode() {
        AgentOrchestratorOpsController controller = new AgentOrchestratorOpsController();

        assertEquals(
                "/api/srmp/langgraph/debug/replay/record%201?execute=true&adaptiveMode=compare",
                controller.replayRuntimePath("record 1", true, "compare")
        );
    }

    @Test
    public void governancePolicyCoverageDraftPathIsStable() {
        AgentOrchestratorOpsController controller = new AgentOrchestratorOpsController();

        assertEquals(
                "/api/srmp/langgraph/governance/policies/coverage/draft",
                controller.governancePolicyCoverageDraftPath()
        );
    }
}
