package com.smartroad.srmp.agent.rag;

import com.smartroad.srmp.agent.mapagent.dto.MapAiContext;

public interface RagQueryRewriteService {
    String rewrite(String userQuestion, MapAiContext context);
}
