package com.smartroad.srmp.agent.eval.service;

import com.smartroad.srmp.agent.eval.dto.RagEvalRequest;
import com.smartroad.srmp.agent.eval.vo.RagEvalResponse;

public interface RagEvalService {
    RagEvalResponse run(RagEvalRequest request);
}
