package com.smartroad.srmp.agent.outline.service;

import com.smartroad.srmp.agent.outline.vo.OutlineSearchResult;

import java.util.List;
import java.util.Map;

public interface OutlineService {
    Map status();

    List<OutlineSearchResult> search(String query, Integer limit);

    Map document(String id);
}
