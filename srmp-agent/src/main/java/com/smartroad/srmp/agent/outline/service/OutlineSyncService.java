package com.smartroad.srmp.agent.outline.service;

import com.smartroad.srmp.agent.outline.dto.OutlineCollectionDTO;
import com.smartroad.srmp.agent.outline.dto.OutlineDocumentDTO;
import com.smartroad.srmp.agent.outline.dto.OutlineSyncRequest;

import java.util.List;
import java.util.Map;

public interface OutlineSyncService {
    List<OutlineCollectionDTO> collections();
    List<OutlineDocumentDTO> documents(String collectionId, Integer limit, Integer offset);
    Map sync(OutlineSyncRequest request);
    List<Map<String, Object>> tasks(Integer limit);
    Map<String, Object> task(String id);
    List<Map<String, Object>> details(String taskId, Integer limit);
}