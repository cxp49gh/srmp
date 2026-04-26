package com.smartroad.srmp.agent.outline.controller;

import com.smartroad.srmp.agent.outline.dto.OutlineCollectionDTO;
import com.smartroad.srmp.agent.outline.dto.OutlineDocumentDTO;
import com.smartroad.srmp.agent.outline.dto.OutlineSyncRequest;
import com.smartroad.srmp.agent.outline.service.OutlineSyncService;
import com.smartroad.srmp.common.core.R;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/outline")
public class OutlineSyncController {

    @Resource
    private OutlineSyncService outlineSyncService;

    @GetMapping("/collections")
    public R<List<OutlineCollectionDTO>> collections() {
        return R.ok(outlineSyncService.collections());
    }

    @PostMapping("/documents/list")
    public R<List<OutlineDocumentDTO>> documents(@RequestBody Map request) {
        String collectionId = request == null || request.get("collectionId") == null ? null : String.valueOf(request.get("collectionId"));
        return R.ok(outlineSyncService.documents(collectionId, readInt(request == null ? null : request.get("limit")), readInt(request == null ? null : request.get("offset"))));
    }

    @PostMapping("/sync")
    public R<Map> sync(@RequestBody OutlineSyncRequest request) {
        return R.ok(outlineSyncService.sync(request));
    }

    @GetMapping("/sync-tasks")
    public R<List<Map<String, Object>>> tasks(@RequestParam(required = false) Integer limit) {
        return R.ok(outlineSyncService.tasks(limit));
    }

    @GetMapping("/sync-tasks/{id}")
    public R<Map<String, Object>> task(@PathVariable String id) {
        return R.ok(outlineSyncService.task(id));
    }

    private Integer readInt(Object value) {
        if (value == null) return null;
        try { return Integer.valueOf(String.valueOf(value)); } catch (Exception e) { return null; }
    }
}