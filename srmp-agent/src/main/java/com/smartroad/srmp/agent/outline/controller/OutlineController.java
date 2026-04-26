package com.smartroad.srmp.agent.outline.controller;

import com.smartroad.srmp.agent.outline.service.OutlineService;
import com.smartroad.srmp.agent.outline.vo.OutlineSearchResult;
import com.smartroad.srmp.common.core.R;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/outline")
public class OutlineController {

    @Resource
    private OutlineService outlineService;

    @GetMapping("/status")
    public R<Map> status() {
        return R.ok(outlineService.status());
    }

    @PostMapping("/search")
    public R<List<OutlineSearchResult>> search(@RequestBody Map request) {
        String query = request == null ? "" : String.valueOf(request.get("query"));
        Integer limit = readInt(request == null ? null : request.get("limit"));
        return R.ok(outlineService.search(query, limit));
    }

    @GetMapping("/documents/{id}")
    public R<Map> document(@PathVariable String id) {
        return R.ok(outlineService.document(id));
    }

    private Integer readInt(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }
}
