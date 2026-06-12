package com.smartroad.srmp.agent.solution.service;

import java.util.Map;

public interface AiSolutionTemplateContractService {
    Map<String, Object> contracts();

    void assertDefaultTemplateAllowed(String templateCode,
                                      String originType,
                                      String objectType,
                                      String solutionType,
                                      String content);
}
